package com.xiaohetv.core.bean

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.xiaohetv.core.utils.Json

class Drm {
    @SerializedName("type") var type: String = ""
    @SerializedName("key") var key: String = ""
    @SerializedName("forceKey") var forceKey: Boolean = false
    @SerializedName("header") var header: JsonElement? = null

    fun headerMap(): Map<String, String> = Json.toMap(header)
}

class Catchup {
    @SerializedName("type") var type: String = ""
    @SerializedName("days") var days: String = ""
    @SerializedName("regex") var regex: String = ""
    @SerializedName("source") var source: String = ""
    @SerializedName("replace") var replace: String = ""
}

class Channel {
    @SerializedName("name") var name: String = ""
    @SerializedName("urls") var urls: List<String> = emptyList()
    @SerializedName("number") var number: String = ""
    @SerializedName("logo") var logo: String = ""
    @SerializedName("epg") var epg: String = ""
    @SerializedName("ua") var ua: String = ""
    @SerializedName("click") var click: String = ""
    @SerializedName("format") var format: String = ""
    @SerializedName("origin") var origin: String = ""
    @SerializedName("referer") var referer: String = ""
    @SerializedName("tvgId") var tvgId: String = ""
    @SerializedName("tvgName") var tvgName: String = ""
    @SerializedName("header") var header: JsonElement? = null
    @SerializedName("parse") var parse: Int = 0
    @SerializedName("catchup") var catchup: Catchup? = null
    @SerializedName("drm") var drm: Drm? = null

    var group: String = ""
    var index: Int = 0

    fun headerMap(): Map<String, String> = Json.toMap(header)

    fun url(): String = urls.getOrNull(index)?.substringBeforeLast('$') ?: urls.firstOrNull()?.substringBeforeLast('$').orEmpty()

    fun lines(): List<String> = urls.map { it.substringAfterLast('$', "") }

    fun logoOf(default: String?): String {
        if (logo.isNotEmpty()) return logo
        if (default.isNullOrEmpty()) return ""
        return default
            .replace("{id}", tvgId)
            .replace("{name}", name)
            .replace("{logo}", logo)
    }

    fun epgOf(default: String?): String = epg.ifEmpty { default.orEmpty() }

    fun uaOf(default: String?): String = ua.ifEmpty { default.orEmpty() }

    fun refererOf(default: String?): String = referer.ifEmpty { default.orEmpty() }

    fun originOf(default: String?): String = origin.ifEmpty { default.orEmpty() }
}

class Group {
    @SerializedName("name") var name: String = ""
    @SerializedName("pass") var pass: String = ""
    @SerializedName("channel") var channel: List<Channel> = emptyList()

    fun channelSafe(): List<Channel> = channel
}

class Live {
    @SerializedName("name") var name: String = ""
    @SerializedName("url") var url: String = ""
    @SerializedName("api") var api: String = ""
    @SerializedName("ext") var ext: JsonElement? = null
    @SerializedName("jar") var jar: String? = null
    @SerializedName("click") var click: String = ""
    @SerializedName("logo") var logo: String = ""
    @SerializedName("epg") var epg: String = ""
    @SerializedName("ua") var ua: String = ""
    @SerializedName("origin") var origin: String = ""
    @SerializedName("referer") var referer: String = ""
    @SerializedName("timeZone") var timeZone: String = ""
    @SerializedName("keep") var keep: String = ""
    @SerializedName("timeout") var timeout: Int = 15
    @SerializedName("header") var header: JsonElement? = null
    @SerializedName("catchup") var catchup: Catchup? = null
    @SerializedName("groups") var groups: List<Group> = emptyList()
    @SerializedName("boot") var boot: Boolean = false
    @SerializedName("pass") var pass: Boolean = false

    fun headerMap(): Map<String, String> = Json.toMap(header)

    fun extString(): String = Json.safeString(ext)

    fun timeoutSafe(): Long = (if (timeout <= 0) 15 else timeout) * 1000L
}
