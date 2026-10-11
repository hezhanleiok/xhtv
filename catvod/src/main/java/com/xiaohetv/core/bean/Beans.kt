package com.xiaohetv.core.bean

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.xiaohetv.core.net.OkProxySelector
import com.xiaohetv.core.utils.Json

data class Depot(
    @SerializedName("name") var name: String = "",
    @SerializedName("url") var url: String = ""
)

data class Style(
    @SerializedName("type") var type: String = "rect",
    @SerializedName("ratio") var ratio: Float = 0f
) {
    fun typeSafe(): String = if (type.isEmpty()) "rect" else type
    fun ratioSafe(): Float {
        if (ratio <= 0) return if (typeSafe() == "oval") 1f else 0.75f
        return minOf(4f, ratio)
    }
    fun isOval(): Boolean = typeSafe() == "oval"
    fun isList(): Boolean = typeSafe() == "list"
}

data class Doh(
    @SerializedName("name") var name: String = "",
    @SerializedName("url") var url: String = "",
    @SerializedName("ips") var ips: List<String> = emptyList()
) {
    companion object {
        @JvmStatic
        fun builtin(): List<Doh> = listOf(
            Doh("系统", ""),
            Doh("腾讯", "https://doh.pub/dns-query"),
            Doh("阿里", "https://dns.alidns.com/dns-query"),
            Doh("360", "https://doh.360.cn/dns-query")
        )
    }
}

data class Header(
    @SerializedName("host") var host: String = "",
    @SerializedName("header") var header: JsonElement? = null
) {
    val headerMap: Map<String, String> get() = Json.toMap(header)
}

data class Proxy(
    @SerializedName("name") var name: String = "",
    @SerializedName("hosts") var hosts: List<String> = emptyList(),
    @SerializedName("urls") var urls: List<String> = emptyList()
) {
    fun resolve(): java.net.Proxy? = urls.firstNotNullOfOrNull { OkProxySelector.toProxy(it) }
}

data class Rule(
    @SerializedName("name") var name: String = "",
    @SerializedName("hosts") var hosts: List<String> = emptyList(),
    @SerializedName("regex") var regex: List<String> = emptyList(),
    @SerializedName("click") var click: List<String> = emptyList(),
    @SerializedName("script") var script: List<String> = emptyList(),
    @SerializedName("exclude") var exclude: List<String> = emptyList()
)

data class Parse(
    @SerializedName("name") var name: String = "",
    @SerializedName("type") var type: Int = 0,
    @SerializedName("url") var url: String = "",
    @SerializedName("ext") var ext: JsonElement? = null
) {
    fun flags(): List<String> {
        val obj = ext?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        val arr = obj.get("flag")?.takeIf { it.isJsonArray } ?: return emptyList()
        return arr.asJsonArray.map { Json.safeString(it) }.filter { it.isNotEmpty() }
    }

    fun headerMap(): Map<String, String> {
        val obj = ext?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
        return Json.toMap(obj.get("header"))
    }
}

data class Site(
    @SerializedName("key") var key: String = "",
    @SerializedName("name") var name: String = "",
    @SerializedName("type") var type: Int = 0,
    @SerializedName("api") var api: String = "",
    @SerializedName("ext") var ext: JsonElement? = null,
    @SerializedName("jar") var jar: String? = null,
    @SerializedName("playUrl") var playUrl: String? = null,
    @SerializedName("click") var click: String? = null,
    @SerializedName("hide") var hide: Int = 0,
    @SerializedName("indexs") var indexs: Int = 0,
    @SerializedName("searchable") var searchable: Int = 1,
    @SerializedName("changeable") var changeable: Int = 1,
    @SerializedName("quickSearch") var quickSearch: Int = 1,
    @SerializedName("timeout") var timeout: Int = 15,
    @SerializedName("categories") var categories: List<String>? = null,
    @SerializedName("header") var header: JsonElement? = null,
    @SerializedName("style") var style: Style? = null
) {
    fun isSpider(): Boolean = type == 3

    fun extString(): String = Json.safeString(ext)

    fun headerMap(): Map<String, String> = Json.toMap(header)

    fun styleSafe(): Style = style ?: Style()

    fun timeoutSafe(): Long = (if (timeout <= 0) 15 else timeout) * 1000L

    fun visible(): Boolean = hide != 1
}
