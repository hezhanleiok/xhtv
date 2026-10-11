package com.xiaohetv.core.utils

/**
 * 相对路径解析：把配置里的 ./ ../ 相对地址展开为绝对地址。
 */
object UriUtil {

    @JvmStatic
    fun resolve(base: String, relative: String): String {
        if (relative.isEmpty()) return base
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        if (relative.startsWith("file://") || relative.startsWith("assets://")) return relative
        return runCatching {
            val uri = java.net.URI(base)
            val scheme = uri.scheme ?: return relative
            val authority = uri.authority ?: return relative
            val origin = "$scheme://$authority"
            if (relative.startsWith("//")) return "$scheme:$relative"
            if (relative.startsWith("/")) return origin + relative
            var dir = (uri.path ?: "/").substringBeforeLast('/', "")
            var tail = relative
            while (tail.startsWith("../")) {
                dir = dir.substringBeforeLast('/', "")
                tail = tail.removePrefix("../")
            }
            while (tail.startsWith("./")) tail = tail.removePrefix("./")
            if (dir.isEmpty()) dir = ""
            "$origin$dir/$tail"
        }.getOrDefault(relative)
    }

    @JvmStatic
    fun convert(url: String): String {
        val trim = url.trim()
        if (trim.startsWith("file://") || trim.startsWith("assets://")) return trim
        if (trim.startsWith("http://") || trim.startsWith("https://")) return trim
        if (trim.startsWith("/")) return "file://$trim"
        return trim
    }
}
