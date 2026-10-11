package com.xiaohetv.player

import okhttp3.Interceptor
import okhttp3.Response

/**
 * TVBox / CatVod 约定：封面等图片 URL 会把请求头直接拼在地址后缀里，例如
 *   https://img3.doubanio.com/view/photo/xxx.jpg@Referer=https://api.douban.com/@User-Agent=Mozilla/5.0 ...
 *
 * 参考项目 fongmi 的 `com.fongmi.android.tv.utils.ImgUtil#getUrl` 正是这样处理的：
 * 把 `@Headers=` / `@Cookie=` / `@Referer=` / `@User-Agent=` 解析成请求头，
 * 并把后缀从 URL 中裁掉。不解析就会被防盗链拒绝 —— 实测豆瓣返回 HTTP 418，导致首页"只有字没有海报"。
 */
object ImageHeaders {

    private val KEYS = listOf("Headers", "Cookie", "Referer", "User-Agent")

    /** 解析出「干净 URL + 附加请求头」；无需处理时返回 null。 */
    fun parse(raw: String): Pair<String, Map<String, String>>? {
        if (!raw.contains('@')) return null
        if (KEYS.none { raw.contains("@$it=") }) return null
        val headers = LinkedHashMap<String, String>()
        KEYS.forEach { name ->
            val value = Regex("@$name=([^@]*)").find(raw)?.groupValues?.get(1)?.trim().orEmpty()
            if (value.isEmpty()) return@forEach
            if (name == "Headers") {
                // 形如 {"User-Agent":"xxx","Referer":"yyy"} 或 k:v;k2:v2
                value.trim().removePrefix("{").removeSuffix("}").split(',', ';').forEach { pair ->
                    val index = pair.indexOf(':')
                    if (index > 0) {
                        val key = pair.substring(0, index).replace("\"", "").trim()
                        val text = pair.substring(index + 1).replace("\"", "").trim()
                        if (key.isNotEmpty() && text.isNotEmpty()) headers[key] = text
                    }
                }
            } else {
                headers[name] = value
            }
        }
        val clean = raw.substringBefore('@')
        if (clean.isEmpty()) return null
        return clean to headers
    }
}

/** 给 Coil 的 OkHttpClient 挂上：把 URL 后缀里的请求头还原成真正的请求头。 */
class ImageHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val parsed = ImageHeaders.parse(request.url.toString()) ?: return chain.proceed(request)
        val builder = request.newBuilder().url(parsed.first)
        parsed.second.forEach { (key, value) -> builder.header(key, value) }
        return chain.proceed(builder.build())
    }
}
