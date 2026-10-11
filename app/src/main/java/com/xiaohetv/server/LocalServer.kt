package com.xiaohetv.server

import com.xiaohetv.core.spider.JarLoader
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.core.utils.VodTrace
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * 本地代理服务（复刻参考项目 `com.fongmi.android.tv.server.Server` + `server/process/Proxy`）。
 *
 * 为什么必须有：大量 csp_ spider 的 `playerContent` 返回的是**本地代理地址**
 * （形如 `http://127.0.0.1:<port>/proxy?do=xxx&...`，由 `com.github.catvod.Proxy.getUrl()` 生成），
 * 由宿主起一个本地 HTTP 服务，把请求转交给 jar 里的 `Proxy.proxy(params)` 处理。
 *
 * 参考项目在 `Server.start()` 里从 9978 开始试端口，成功后 `Proxy.set(port)`。
 * 我们没有这个服务时 `Proxy.getPort()` 恒为 -1，解析出来的播放地址就是
 * `http://127.0.0.1:-1/proxy?...` → 播放器必然报 ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
 * （用户反馈的"点播无法播放"根因，已在日志中确认）。
 */
object LocalServer {

    private const val START_PORT = 9978
    private const val END_PORT = 9998

    @Volatile
    private var socket: ServerSocket? = null

    val port: Int get() = com.github.catvod.Proxy.getPort()

    @Synchronized
    fun start() {
        if (socket != null) return
        for (candidate in START_PORT..END_PORT) {
            try {
                val server = ServerSocket(candidate, 50, InetAddress.getByName("127.0.0.1"))
                socket = server
                // 关键：把端口告诉 jar（spider 用它拼本地代理地址）
                com.github.catvod.Proxy.set(candidate)
                Diag.log("Server", "本地代理服务已启动：http://127.0.0.1:$candidate/proxy")
                val pool = Executors.newFixedThreadPool(4)
                Thread {
                    while (true) {
                        val client = try {
                            server.accept()
                        } catch (t: Throwable) {
                            break
                        }
                        pool.execute { handle(client) }
                    }
                }.apply { isDaemon = true; name = "xiaohe-local-server"; start() }
                return
            } catch (t: Throwable) {
                // 端口被占用，试下一个
            }
        }
        Diag.error("Server", "本地代理服务启动失败：$START_PORT-$END_PORT 均不可用", null)
    }

    private fun handle(client: Socket) {
        runCatching {
            client.use { socket ->
                val input = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val requestLine = input.readLine() ?: return@use
                val headers = LinkedHashMap<String, String>()
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    val index = line.indexOf(':')
                    if (index > 0) headers[line.substring(0, index).trim()] = line.substring(index + 1).trim()
                }

                val target = requestLine.split(' ').getOrElse(1) { "/" }
                if (!target.startsWith("/proxy")) {
                    writeHead(socket.getOutputStream(), 404, "text/plain", 0, emptyMap())
                    return@use
                }

                val query = target.substringAfter('?', "")
                val params = LinkedHashMap<String, String>()
                query.split('&').forEach { pair ->
                    if (pair.isEmpty()) return@forEach
                    val key = pair.substringBefore('=')
                    val value = pair.substringAfter('=', "")
                    params[decode(key)] = decode(value)
                }
                // spider 的 proxy 实现常依赖请求头（如 Range）：原样 + 小写各放一份，
                // 因为 NanoHTTPD / OkHttp 给到的头名大小写不一致（参考项目用 session.getHeaders()）
                headers.forEach { (k, v) ->
                    params.putIfAbsent(k, v)
                    params.putIfAbsent(k.lowercase(), v)
                }
                // 与点播结构化日志同一 playbackId：播放器请求的 /proxy?... 与上游 URL 可对应
                VodTrace.stage(
                    "proxy_request", "received",
                    extra = "do=${params["do"].orEmpty()}" +
                        " upstream=${VodTrace.mask(params["url"].orEmpty())}" +
                        " range=${headers["Range"] ?: headers["range"] ?: "-"}" +
                        " params=${VodTrace.snippet(query, 160)}"
                )

                val result = JarLoader.proxy(params)
                val status = (result?.getOrNull(0) as? Int) ?: 502
                val mime = (result?.getOrNull(1) as? String) ?: "application/octet-stream"
                @Suppress("UNCHECKED_CAST")
                val extra = (result?.getOrNull(3) as? Map<String, String>).orEmpty()
                // 兼容三种正文形态：InputStream（常见）/ String / ByteArray
                val body: ByteArray? = when (val raw = result?.getOrNull(2)) {
                    is InputStream -> raw.use { it.readBytes() }
                    is String -> raw.toByteArray(Charsets.UTF_8)
                    is ByteArray -> raw
                    else -> null
                }
                // 关键诊断：代理返回了什么（状态/类型/长度/正文开头），
                // 播放器报解析类错误时看这一行就能判断是"代理没给内容"还是"给了内容但播放器解析不了"
                if (result == null) {
                    VodTrace.stage(
                        "proxy_response", "failed", errorType = "NoProxyResult",
                        errorMessage = "jar 未返回代理结果（proxy 方法未注册或抛异常）",
                        extra = "do=${params["do"].orEmpty()}"
                    )
                } else {
                    VodTrace.stage(
                        "proxy_response", if (status >= 400) "failed" else "ok",
                        errorType = if (status >= 400) "HttpStatus$status" else "",
                        errorMessage = if (status >= 400) "代理返回 $status" else "",
                        extra = "status=$status mime=$mime extra=${extra.keys} bytes=${body?.size ?: 0}" +
                            " bodyHead=${bodyPreview(body)}"
                    )
                }

                val output = socket.getOutputStream()
                writeHead(output, status, mime, body?.size ?: 0, extra)
                if (body != null) output.write(body)
                output.flush()
            }
        }.onFailure {
            VodTrace.fail("proxy", it)
        }
    }

    private fun writeHead(
        output: OutputStream,
        status: Int,
        mime: String,
        length: Int,
        extra: Map<String, String>
    ) {
        val head = StringBuilder()
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
        head.append("Content-Type: ").append(mime).append("\r\n")
        head.append("Content-Length: ").append(length).append("\r\n")
        extra.forEach { (k, v) -> if (!k.equals("Content-Length", true)) head.append(k).append(": ").append(v).append("\r\n") }
        head.append("Connection: close\r\n\r\n")
        output.write(head.toString().toByteArray(Charsets.ISO_8859_1))
    }

    private fun decode(text: String): String = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)

    /** 正文开头预览（m3u8 应能看到 #EXTM3U；html/json 则说明代理返回的是错误页）。 */
    private fun bodyPreview(body: ByteArray?): String {
        if (body == null || body.isEmpty()) return "<空>"
        val size = minOf(body.size, 120)
        return runCatching {
            String(body, 0, size, Charsets.UTF_8).replace("\n", "\\n").replace("\r", "")
        }.getOrDefault("<二进制>")
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        206 -> "Partial Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        else -> "OK"
    }
}
