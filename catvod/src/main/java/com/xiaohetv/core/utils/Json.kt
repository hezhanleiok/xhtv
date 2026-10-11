package com.xiaohetv.core.utils

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

object Json {

    @JvmStatic
    val gson: Gson = Gson()

    @JvmStatic
    fun parse(text: String?): JsonElement? = runCatching { JsonParser.parseString(text) }.getOrNull()

    @JvmStatic
    fun obj(text: String?): JsonObject? = parse(text)?.takeIf { it.isJsonObject }?.asJsonObject

    @JvmStatic
    fun arr(text: String?): JsonArray? = parse(text)?.takeIf { it.isJsonArray }?.asJsonArray

    @JvmStatic
    fun isObj(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trim = text.trim()
        return trim.startsWith("{") && trim.endsWith("}")
    }

    @JvmStatic
    fun isArray(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trim = text.trim()
        return trim.startsWith("[") && trim.endsWith("]")
    }

    @JvmStatic
    fun safeString(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        return if (element.isJsonPrimitive) element.asString else element.toString()
    }

    @JvmStatic
    fun toMap(element: JsonElement?): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        if (element == null || !element.isJsonObject) return map
        element.asJsonObject.entrySet().forEach { (k, v) -> map[k] = safeString(v) }
        return map
    }

    @JvmStatic
    fun toMap(text: String?): Map<String, String> = toMap(parse(text))

    @JvmStatic
    fun str(obj: JsonObject?, key: String): String = safeString(obj?.get(key))

    @JvmStatic
    fun primitive(value: String): JsonPrimitive = JsonPrimitive(value)
}
