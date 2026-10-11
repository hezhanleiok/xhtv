package com.xiaohetv.player

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.xiaohetv.core.utils.VodTrace
import java.io.IOException

/**
 * 给 ExoPlayer 的数据源套一层"取证"包装，用来回答点播排查的核心问题：
 *   ① 播放器是否**真的**发起了媒体请求？  ② M3U8 之后的分片请求有没有发生、哪个失败？
 *   ③ HTTP 全 200 却播不出时，是解析/格式/解码失败吗？
 *
 * 说明：HLS 的播放列表与分片（.ts/.m4s/.key）都会经过 DataSource，因此这里能完整覆盖。
 *
 * 本类**只做记录，不改变任何播放行为**（纯委托）。
 * 日志字段语义（避免误读）：
 *   declaredLength = upstream.open() 的返回值（HttpDataSource 返回 Content-Length / 实际长度），**不是已读字节数**
 *   readBytes      = 实际通过 read() 读到的字节数
 *   readCalls      = read() 被调用的次数
 *   → readCalls=0 且 readBytes=0 表示"打开后被取消（播放器不再需要）"，**不是**读取失败；
 *   → readCalls>0 且 readBytes=0 才是真的"读到立即 EOF"（典型是上游 200 + 空正文的反盗链）
 *
 * 防爆量：只记录前 3 个请求、每 20 个记录 1 个，以及**所有失败**与播放列表类请求。
 */
class TracedDataSourceFactory(private val upstream: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = TracedDataSource(upstream.createDataSource())
}

class TracedDataSource(private val delegate: DataSource) : DataSource {

    private var sequence = 0
    private var url = ""
    private var openAt = 0L
    private var bytes = 0L
    private var readCalls = 0
    private var firstReadSize = -1
    private var firstReadHex = ""
    private var logThis = false
    private var redirect = ""

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        sequence++
        url = dataSpec.uri.toString()
        val kind = VodTrace.kindOf(url)
        openAt = SystemClock.elapsedRealtime()
        bytes = 0L
        readCalls = 0
        firstReadSize = -1
        firstReadHex = ""
        // 记录策略：前 3 个 + 每 20 个 + 播放列表/密钥类 + 大范围请求
        logThis = sequence <= 3 || sequence % 20 == 0 ||
            kind == "hls-m3u8" || kind == "dash-mpd" || url.contains(".key") || url.contains(".mpd")
        if (logThis) {
            VodTrace.stage(
                "media_open", "started", elapsedMs = 0L,
                extra = "seq=$sequence kind=$kind url=${VodTrace.mask(url)}" +
                    " position=${dataSpec.position} " +
                    "requestLength=${if (dataSpec.length == C.LENGTH_UNSET.toLong()) -1 else dataSpec.length}"
            )
        }
        return try {
            val declared = delegate.open(dataSpec)
            val elapsed = SystemClock.elapsedRealtime() - openAt
            // 最终实际请求地址（发生重定向时与原始不同）——分片被重定向正是"拿到 m3u8 当分片"的原因
            val finalUrl = runCatching { delegate.uri?.toString() }.getOrNull().orEmpty()
            redirect = if (finalUrl.isNotEmpty() && finalUrl != url) VodTrace.mask(finalUrl) else ""
            if (logThis) {
                VodTrace.stage(
                    "media_open", "ok", elapsedMs = elapsed,
                    extra = "seq=$sequence kind=${VodTrace.kindOf(url)}" +
                        " declaredLength=$declared（open 返回值/Content-Length，非已读字节）" +
                        " mime=${responseMime().ifEmpty { "未知" }}" +
                        " contentEncoding=${header("Content-Encoding").ifEmpty { "-" }}" +
                        " contentRange=${header("Content-Range").ifEmpty { "无(说明不是 206)" }}" +
                        (if (redirect.isEmpty()) " redirect=无" else " redirectTo=$redirect")
                )
            }
            declared
        } catch (e: IOException) {
            val elapsed = SystemClock.elapsedRealtime() - openAt
            // 原始异常原样上报（含 HTTP 状态码/超时/DNS/TLS），再原样抛回播放器，绝不吞掉
            VodTrace.fail(
                "media_open", e, elapsed,
                extra = "seq=$sequence kind=$kind url=${VodTrace.mask(url)}" +
                    " position=${dataSpec.position}"
            )
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            val read = delegate.read(buffer, offset, length)
            readCalls++
            if (read > 0) {
                bytes += read
                if (firstReadSize < 0) {
                    firstReadSize = read
                    // 分片首字节应当是 TS 同步字节 0x47（'G'），可据此判断拿到的是否真是媒体数据
                    firstReadHex = (0 until minOf(read, 4)).joinToString("") {
                        String.format("%02X", buffer[offset + it])
                    }
                }
            }
            read
        } catch (e: IOException) {
            VodTrace.fail(
                "media_read", e, SystemClock.elapsedRealtime() - openAt,
                extra = "seq=$sequence url=${VodTrace.mask(url)} readCalls=$readCalls alreadyReadBytes=$bytes"
            )
            throw e
        }
    }

    override fun getUri(): Uri? = delegate.uri

    /**
     * 关闭时给这次请求下结论。注意区分两种"0 字节"：
     *   readCalls=0 → 打开后被取消（播放器放弃该请求），**不是**失败
     *   readCalls>0 → 真的读到了 EOF（readBytes=0 即上游给了空正文）
     */
    override fun close() {
        try {
            delegate.close()
        } finally {
            if (logThis && sequence > 0) {
                val result = when {
                    bytes > 0 -> "done"
                    readCalls == 0 -> "canceled_before_read"
                    else -> "empty_after_read"
                }
                VodTrace.stage(
                    "media_read", result,
                    elapsedMs = SystemClock.elapsedRealtime() - openAt,
                    extra = "seq=$sequence url=${VodTrace.mask(url)} readCalls=$readCalls readBytes=$bytes" +
                        (if (firstReadSize >= 0) " firstRead=$firstReadSize firstBytesHex=$firstReadHex" else "") +
                        (if (result == "empty_after_read") " 【注意：读到立即 EOF，上游可能 200+空正文（反盗链）】" else "")
                )
            }
        }
    }

    private fun responseMime(): String = header("Content-Type")

    private fun header(name: String): String = runCatching {
        delegate.responseHeaders.entries.firstOrNull { it.key.equals(name, true) }
            ?.value?.firstOrNull().orEmpty()
    }.getOrDefault("")
}
