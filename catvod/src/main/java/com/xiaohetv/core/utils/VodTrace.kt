package com.xiaohetv.core.utils

import java.util.concurrent.atomic.AtomicInteger

/**
 * 影视**点播**播放链路的结构化诊断日志。
 *
 * 目标：一次点播的每一阶段日志都可以用 playbackId 关联，并能回答：
 *   1) 播放地址获取成功了吗？2) 得到的是什么类型的地址？3) 播放器初始化成功了吗？
 *   4) 播放器真的发起了媒体请求吗？5) M3U8 之后的分片请求有没有发生、哪个失败？
 *   6) HTTP 全 200 却播不出时，是解析/格式/解码失败吗？7) 最终原始异常是什么、发生在哪个阶段？
 *
 * 统一格式（便于检索）：
 *   [VOD][playbackId=xxx][stage=segment_request][result=failed] elapsedMs=.. errorType=.. errorMessage=.. 【额外字段】
 *
 * 安全：URL 脱敏（保留 scheme/host/路径与参数名，值打码）；不输出 Cookie/Authorization/Token/签名。
 */
object VodTrace {

    private val counter = AtomicInteger(0)

    @Volatile
    var playbackId: String = ""
        private set

    @Volatile
    private var beginAt: Long = 0L

    /** 开始一次点播任务（每次点击播放/切集/换源都会产生新的 playbackId）。 */
    @JvmStatic
    fun begin(scene: String, extra: String = ""): String {
        val id = "p" + System.currentTimeMillis().toString().takeLast(7) + "-" + counter.incrementAndGet()
        playbackId = id
        beginAt = System.currentTimeMillis()
        stage("entry", "started", 0L, extra = "scene=$scene${if (extra.isEmpty()) "" else " $extra"}")
        return id
    }

    /** 距本次播放任务开始的毫秒数。 */
    @JvmStatic
    fun sinceBegin(): Long = if (beginAt == 0L) -1L else System.currentTimeMillis() - beginAt

    /**
     * 记录一个阶段。
     * @param stage 阶段名：entry / player_content / parse / resolve / player_init / media_open / media_read / proxy / state / first_frame / segment ...
     * @param result ok / failed / started / skipped / suspect / done / timeout
     */
    @JvmStatic
    @JvmOverloads
    fun stage(
        stage: String,
        result: String,
        elapsedMs: Long = -1L,
        errorType: String = "",
        errorMessage: String = "",
        extra: String = ""
    ) {
        val sb = StringBuilder(160)
        sb.append("[VOD][playbackId=").append(playbackId.ifEmpty { "-" })
            .append("][stage=").append(stage)
            .append("][result=").append(result).append(']')
        sb.append(" elapsedMs=").append(if (elapsedMs < 0) sinceBegin() else elapsedMs)
        if (errorType.isNotEmpty()) sb.append(" errorType=").append(errorType)
        if (errorMessage.isNotEmpty()) sb.append(" errorMessage=").append(errorMessage)
        if (extra.isNotEmpty()) sb.append(" | ").append(extra)
        if (result == "failed") Diag.error("VOD", sb.toString()) else Diag.log("VOD", sb.toString())
    }

    /** 失败：带原始异常类型、消息、完整 cause 链与堆栈（绝不吞掉原始错误）。 */
    @JvmStatic
    @JvmOverloads
    fun fail(stage: String, error: Throwable?, elapsedMs: Long = -1L, extra: String = "") {
        val chain = causeChain(error)
        // 异常类型尽量取最内层（例如 InvalidResponseCodeException / UnknownHostException）
        val deepest = deepest(error)
        stage(
            stage = stage,
            result = "failed",
            elapsedMs = elapsedMs,
            errorType = deepest?.javaClass?.name ?: "Unknown",
            errorMessage = oneLine(deepest?.message ?: "无消息"),
            extra = (if (extra.isEmpty()) "" else "$extra | ") + "errorKind=${classify(error)} causeChain=$chain"
        )
        Diag.error("VOD", "[VOD][playbackId=${playbackId.ifEmpty { "-" }}][stage=$stage][result=failed] 原始堆栈", error)
    }

    /** 失败归类：连接超时 / 读取超时 / DNS / TLS / HTTP 状态码 / 格式解析 / 解码 / 取消 / 其它。 */
    @JvmStatic
    fun classify(error: Throwable?): String {
        val node = deepest(error)
        val name = node?.javaClass?.name.orEmpty()
        val msg = (node?.message ?: "").lowercase()
        return when {
            name.contains("ConnectTimeout") || msg.contains("connect timed out") -> "connect_timeout"
            name.contains("SocketTimeout") || msg.contains("timed out") || msg.contains("timeout") -> "read_timeout"
            name.contains("UnknownHost") -> "dns"
            name.contains("SSL") || name.contains("Cert") || msg.contains("certificate") -> "tls"
            name.contains("InvalidResponseCode") || msg.contains("http ") -> "http_status"
            name.contains("Parser") || name.contains("Malformed") || msg.contains("unsupported") -> "format_or_parse"
            name.contains("Decoder") || msg.contains("decoder") -> "decode"
            name.contains("Cancel") || msg.contains("cancel") -> "canceled"
            name.contains("Connect") -> "connect"
            else -> "other"
        }
    }

    /** "A <- B <- C" 形式的 cause 链。 */
    @JvmStatic
    fun causeChain(error: Throwable?): String {
        if (error == null) return "无异常"
        val sb = StringBuilder()
        var node: Throwable? = error
        var depth = 0
        while (node != null && depth < 8) {
            if (depth > 0) sb.append(" <- ")
            sb.append(node.javaClass.simpleName)
            val msg = node.message
            if (!msg.isNullOrEmpty()) sb.append('(').append(oneLine(msg).take(120)).append(')')
            node = node.cause
            depth++
        }
        return sb.toString()
    }

    private fun deepest(error: Throwable?): Throwable? {
        var node = error
        var last: Throwable? = error
        var depth = 0
        while (node != null && depth < 8) {
            last = node
            node = node.cause
            depth++
        }
        return last
    }

    /** 地址类型判定：让日志能回答"得到的是什么类型的地址"。 */
    @JvmStatic
    fun kindOf(url: String): String {
        val text = url.trim()
        if (text.isEmpty()) return "empty"
        val lower = text.lowercase()
        return when {
            lower.startsWith("magnet:") -> "magnet"
            lower.startsWith("ed2k:") -> "ed2k"
            lower.startsWith("thunder:") -> "thunder"
            lower.startsWith("file:") -> "file"
            lower.startsWith("content:") -> "content"
            lower.contains("/proxy?") || lower.contains("/proxy/") -> "local-proxy"
            lower.contains(".m3u8") || lower.contains("m3u8") -> "hls-m3u8"
            lower.contains(".mpd") || lower.contains("mpd?") -> "dash-mpd"
            lower.contains(".mp4") -> "mp4"
            lower.contains(".flv") -> "flv"
            lower.startsWith("http") -> "http"
            text.startsWith("{") || text.startsWith("[") -> "json"
            else -> "other"
        }
    }

    /** 是否"看起来就是可直接播放/可直接请求的地址"。 */
    @JvmStatic
    fun looksPlayable(url: String): Boolean = when (kindOf(url)) {
        "hls-m3u8", "dash-mpd", "mp4", "flv", "http", "local-proxy", "file", "content", "magnet", "ed2k", "thunder" -> true
        else -> false
    }

    /** 脱敏 URL：保留 scheme/host/路径与参数名，值打码（do/type/format 这类无隐私的值保留）。 */
    @JvmStatic
    fun mask(url: String): String {
        if (url.isEmpty()) return ""
        val cut = url.indexOf('?')
        if (cut < 0) return url.take(220)
        val base = url.substring(0, cut)
        val query = url.substring(cut + 1)
        val keep = setOf("do", "type", "format", "quality", "flag", "from", "ep", "index", "id", "site")
        val sb = StringBuilder(base.take(200)).append('?')
        var first = true
        query.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (!first) sb.append('&')
            first = false
            sb.append(key)
            if (value.isNotEmpty()) {
                sb.append('=')
                val lower = key.lowercase()
                if (lower in keep && value.length <= 24) sb.append(value) else sb.append("***(").append(value.length).append(')')
            }
        }
        return sb.toString().take(300)
    }

    /** 内容片段：压成单行并截断（用于判断返回的是 m3u8 / HTML 错误页 / JSON）。 */
    @JvmStatic
    @JvmOverloads
    fun snippet(text: String?, max: Int = 160): String {
        if (text.isNullOrEmpty()) return "<空>"
        return text.replace(Regex("\\s+"), " ").take(max)
    }

    /** 头部字段名（不输出值，避免 Cookie/Token 泄漏）。 */
    @JvmStatic
    fun headerNames(headers: Map<String, String>): String =
        if (headers.isEmpty()) "无" else headers.keys.joinToString(",")

    private fun oneLine(text: String): String = text.replace(Regex("\\s+"), " ").take(300)
}
