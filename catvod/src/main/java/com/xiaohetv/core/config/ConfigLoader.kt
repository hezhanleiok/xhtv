package com.xiaohetv.core.config

import android.util.Base64
import com.xiaohetv.core.Core
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Crypto
import com.xiaohetv.core.utils.Json
import com.xiaohetv.core.utils.UriUtil
import com.xiaohetv.core.utils.Util
import java.io.File

/**
 * 配置读取，兼容 TVBox 生态各种"反爬/伪装"分发方式：
 *
 * 1. **UA 判定**：不少服务器只对 UA 含 okhttp 的请求返回配置，其他 UA 返回首页/图片。
 * 2. **HTTP → HTTPS 兜底**：部分域名只允许 https（http 会被 Cloudflare 400）。
 * 3. **伪装格式**：响应体是「图片二进制 + `**` + Base64(配置)」；
 *    也兼容纯 Base64、TvBox CBC（2423 开头）、BOM 前缀。
 * 4. **相对路径**：以跳转后的最终地址为基准展开 ./ 与 ../。
 */
object ConfigLoader {

    class Loaded(val text: String, val base: String)

    @Throws(Exception::class)
    @JvmStatic
    fun fetch(url: String): String = fetchWithBase(url).text

    @Throws(Exception::class)
    @JvmStatic
    fun fetchWithBase(url: String): Loaded {
        val target = normalize(url)
        if (target.isEmpty()) throw Exception("配置地址为空")
        return when {
            target.startsWith("assets://") -> Loaded(decode(readAsset(target.removePrefix("assets://"))), target)
            target.startsWith("file://") -> Loaded(decode(readFile(target.removePrefix("file://"))), target)
            target.startsWith("content://") -> Loaded(decode(readContent(target)), target)
            target.startsWith("{") || target.startsWith("[") -> Loaded(decode(target), target)
            else -> readHttp(target)
        }
    }

    /** 地址规范化：自动补全协议、去掉粘贴时带上的引号/空格。 */
    @JvmStatic
    fun normalize(url: String): String {
        var value = url.trim().replace("\r", "").replace("\n", "").replace(" ", "")
        value = value.trim('"', '\'', '<', '>', '\uFEFF')
        if (value.isEmpty()) return value
        if (value.startsWith("{") || value.startsWith("[")) return value
        val lower = value.lowercase()
        if (lower.startsWith("assets://") || lower.startsWith("file://") || lower.startsWith("content://")) return value
        if (lower.startsWith("http://") || lower.startsWith("https://")) return value
        if (lower.startsWith("//")) return "https:$value"
        if (value.startsWith("/")) return "file://$value"
        return "http://$value"
    }

    /**
     * 依次尝试：原地址 → https 版地址；每个地址再依次尝试多个 UA。
     * 返回第一个"能被解析成配置"的结果。
     */
    private fun readHttp(url: String): Loaded {
        val candidates = mutableListOf(url)
        if (url.startsWith("http://")) candidates.add("https://" + url.removePrefix("http://"))
        var lastError: String? = null
        var lastBody: String? = null

        for (target in candidates) {
            for (agent in Util.USER_AGENTS) {
                val result = try {
                    OkHttp.request(target, emptyMap(), agent)
                } catch (e: Exception) {
                    lastError = e.message ?: "请求失败"
                    null
                } ?: continue

                val text = result.text()
                decodeOrNull(text)?.let { return Loaded(it, result.url) }
                lastBody = preview(text)
            }
        }
        if (lastBody != null) throw Exception("地址返回的内容不是配置（前 60 字：$lastBody）")
        throw Exception(lastError ?: "无法连接到 $url")
    }

    private fun readFile(path: String): String {
        val file = File(path)
        if (!file.exists()) throw Exception("本地文件不存在：$path")
        return file.readText(Charsets.UTF_8)
    }

    private fun readAsset(name: String): String = runCatching {
        Core.context().assets.open(name).bufferedReader().use { it.readText() }
    }.getOrElse { throw Exception("无法读取 assets://$name") }

    private fun readContent(url: String): String = runCatching {
        Core.context().contentResolver.openInputStream(android.net.Uri.parse(url))?.bufferedReader()?.use { it.readText() }.orEmpty()
    }.getOrDefault("")

    @Throws(Exception::class)
    @JvmStatic
    fun decode(raw: String): String {
        decodeOrNull(raw)?.let { return it }
        val clean = stripBom(raw).trim()
        if (clean.isEmpty()) throw Exception("配置内容为空")
        if (clean.startsWith("<")) throw Exception("配置地址返回的是网页（HTML），请检查地址是否正确")
        throw Exception("配置不是有效的 JSON（前 60 字：${preview(clean)}）")
    }

    /** 解析成功返回 JSON 文本，失败返回 null（不抛异常）。 */
    @JvmStatic
    fun decodeOrNull(raw: String): String? {
        var data = stripBom(raw).trim()
        if (data.isEmpty()) return null
        repeat(4) {
            if (isJson(data)) return data
            if (data.contains("**")) {
                val decoded = tryBase64(data)
                if (decoded != null) {
                    data = stripBom(decoded).trim()
                    return@repeat
                }
            }
            if (data.startsWith("2423")) {
                val plain = cbc(data.replace(Regex("\\s+"), ""))
                if (plain != data) {
                    data = stripBom(plain).trim()
                    return@repeat
                }
            }
            return null
        }
        return if (isJson(data)) data else null
    }

    private fun isJson(text: String): Boolean {
        val first = text.firstOrNull { !it.isWhitespace() } ?: return false
        if (first != '{' && first != '[') return false
        return Json.isObj(text) || Json.isArray(text)
    }

    private fun stripBom(text: String): String =
        text.removePrefix("\uFEFF").removePrefix("\u200B").replace("\uFEFF", "")

    private fun preview(text: String): String =
        text.replace(Regex("[\\x00-\\x1F\\x7F]"), " ").replace(Regex("\\s+"), " ").trim().take(60)

    /**
     * 「二进制前缀 + ** + Base64」结构：从 ** 之后开始解码，
     * 尝试常见偏移量，并逐步裁掉尾部垃圾（限制次数，避免长文本卡顿）。
     */
    private fun tryBase64(data: String): String? {
        val marker = data.indexOf("**")
        if (marker < 0) return null
        val raw = data.substring(marker + 2)
        for (offset in intArrayOf(0, 10, 2, 4, 8, 6)) {
            if (raw.length <= offset) continue
            val body = base64Only(raw.substring(offset))
            if (body.length < 16) continue
            var candidate = body
            var attempts = 0
            while (candidate.length >= 16 && attempts < 40) {
                val decoded = decodeBase64(candidate)
                if (decoded != null) return decoded
                candidate = candidate.substring(0, candidate.length - 4)
                attempts++
            }
        }
        return null
    }

    /** 只保留 Base64 字母表字符，遇到第一个非法字符即停止（尾部二进制垃圾）。 */
    private fun base64Only(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                (ch in 'A'..'Z') || (ch in 'a'..'z') || (ch in '0'..'9') || ch == '+' || ch == '/' || ch == '=' -> sb.append(ch)
                ch == '\n' || ch == '\r' || ch == ' ' || ch == '\t' -> Unit
                else -> break
            }
        }
        return sb.toString()
    }

    private fun decodeBase64(text: String): String? {
        if (text.length < 16) return null
        val padded = text + "=".repeat((4 - text.length % 4) % 4)
        val bytes = runCatching { Base64.decode(padded, Base64.DEFAULT) }.getOrNull() ?: return null
        val decoded = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull() ?: return null
        val clean = stripBom(decoded).trim()
        return if (isJson(clean)) clean else null
    }

    private fun cbc(data: String): String {
        return runCatching {
            val decode = String(Util.hex2byte(data), Charsets.UTF_8).lowercase()
            val key = decode.substring(decode.indexOf("\$#") + 2, decode.indexOf("#\$"))
            val iv = decode.substring(decode.length - 13)
            val cipher = data.substring(data.indexOf("2324") + 4, data.length - 26)
            for (pad in charArrayOf('0', '\u0000')) {
                val plain = runCatching {
                    String(Crypto.aesCbc(Util.hex2byte(cipher), Crypto.padEnd(key, 16, pad), Crypto.padEnd(iv, 16, pad)), Charsets.UTF_8)
                }.getOrNull()
                if (!plain.isNullOrBlank() && plain.trim().firstOrNull()?.let { it == '{' || it == '[' } == true) return plain
            }
            data
        }.getOrDefault(data)
    }

    /**
     * 展开配置中的相对路径，./ 与 ../ 以配置文件的最终位置为基准。
     */
    @JvmStatic
    fun expand(base: String, data: String): String {
        var out = data
        if (out.contains("../")) out = out.replace("../", UriUtil.resolve(base, "../"))
        if (out.contains("./")) out = out.replace("./", UriUtil.resolve(base, "./"))
        return out
    }

    @JvmStatic
    fun depots(json: String): List<com.xiaohetv.core.bean.Depot> {
        val obj = Json.obj(json) ?: return emptyList()
        val array = obj.getAsJsonArray("urls") ?: return emptyList()
        return array.mapNotNull { runCatching { Json.gson.fromJson(it, com.xiaohetv.core.bean.Depot::class.java) }.getOrNull() }
    }
}
