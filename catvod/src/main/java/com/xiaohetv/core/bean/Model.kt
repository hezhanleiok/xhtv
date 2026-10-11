package com.xiaohetv.core.bean

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.core.utils.Json

class VodClass {
    @SerializedName("type_id") var typeId: String = ""
    @SerializedName("type_name") var typeName: String = ""
    @SerializedName("type_flag") var typeFlag: String = ""

    companion object {
        fun from(obj: JsonObject): VodClass {
            val item = VodClass()
            item.typeId = Json.safeString(obj.get("type_id") ?: obj.get("id"))
            item.typeName = Json.safeString(obj.get("type_name") ?: obj.get("name"))
            item.typeFlag = Json.safeString(obj.get("type_flag"))
            return item
        }
    }
}

class Episode {
    var name: String = ""
    var url: String = ""
    var desc: String = ""
    var index: Int = 0
    var selected: Boolean = false
}

class Flag {
    var flag: String = ""
    var episodes: MutableList<Episode> = mutableListOf()
    var selected: Boolean = false

    fun setEpisodes(text: String) {
        episodes.clear()
        text.split("#").forEachIndexed { index, part ->
            if (part.isEmpty()) return@forEachIndexed
            val split = part.split("$")
            val episode = Episode()
            episode.index = index
            episode.url = split.getOrElse(1) { split[0] }.trim()
            episode.name = split[0].trim().ifEmpty { String.format("%02d", index + 1) }
            episodes.add(episode)
        }
    }
}

class Sub {
    @SerializedName("name") var name: String = ""
    @SerializedName("url") var url: String = ""
    @SerializedName("lang") var lang: String = ""
    @SerializedName("format") var format: String = ""
}

class Value {
    @SerializedName("n") var n: String = ""
    @SerializedName("v") var v: String = ""
    var selected: Boolean = false
}

class Filter {
    @SerializedName("key") var key: String = ""
    @SerializedName("name") var name: String = ""
    @SerializedName("init") var init: String = ""
    @SerializedName("value") var value: List<Value> = emptyList()

    fun selectedValue(): String = value.firstOrNull { it.selected }?.v ?: init
}

class Vod {
    @SerializedName("vod_id") var vodId: String = ""
    @SerializedName("vod_name") var vodName: String = ""
    @SerializedName("type_name") var typeName: String = ""
    @SerializedName("vod_pic") var vodPic: String = ""
    @SerializedName("vod_remarks") var vodRemarks: String = ""
    @SerializedName("vod_year") var vodYear: String = ""
    @SerializedName("vod_area") var vodArea: String = ""
    @SerializedName("vod_actor") var vodActor: String = ""
    @SerializedName("vod_director") var vodDirector: String = ""
    @SerializedName("vod_content") var vodContent: String = ""
    @SerializedName("vod_play_from") var vodPlayFrom: String = ""
    @SerializedName("vod_play_url") var vodPlayUrl: String = ""
    @SerializedName("vod_tag") var vodTag: String = ""
    @SerializedName("style") var style: Style? = null

    var siteKey: String = ""
    private var flagsCache: List<Flag>? = null

    fun isFolder(): Boolean = vodTag == "folder"

    fun flags(): List<Flag> {
        flagsCache?.let { return it }
        val froms = vodPlayFrom.split("$$$")
        val urls = vodPlayUrl.split("$$$")
        val list = mutableListOf<Flag>()
        for (i in froms.indices) {
            if (froms[i].isEmpty()) continue
            val flag = Flag()
            flag.flag = froms[i]
            flag.setEpisodes(urls.getOrElse(i) { "" })
            list.add(flag)
        }
        flagsCache = list
        return list
    }

    fun styleSafe(default: Style? = null): Style = style ?: default ?: Style()
}

class Result {

    @SerializedName("list") var list: List<Vod>? = null
    @SerializedName("pagecount") var pagecount: Int = 1
    @SerializedName("code") var code: Int = 1
    @SerializedName("msg") var msg: String = ""
    @SerializedName("playUrl") var playUrl: String = ""
    @SerializedName("flag") var flag: String = ""
    @SerializedName("format") var format: String = ""
    @SerializedName("parse") var parse: Int = 0
    @SerializedName("jx") var jx: Int = 0
    @SerializedName("subs") var subs: List<Sub>? = null

    @SerializedName("class") var classElement: JsonElement? = null
    @SerializedName("url") var urlElement: JsonElement? = null
    @SerializedName("header") var headerElement: JsonElement? = null
    @SerializedName("filters") var filtersElement: JsonElement? = null

    /**
     * 注意：这个字段名与 filtersElement 的 @SerializedName("filters") 重名，
     * Gson 建适配器时会抛 "declares multiple JSON fields named 'filters'"，
     * 导致**每次响应都解析失败**（首页全空、搜索无结果）。
     * 加 @Transient 让 Gson 跳过它；filters 由 filtersElement 经 normalize() 填充。
     */
    @Transient
    var filters: LinkedHashMap<String, List<Filter>> = LinkedHashMap()

    /** 由 classElement 经 normalize() 填充；CmsSpider 会直接赋值并序列化回传，故不能加 @Transient。 */
    var types: List<VodClass> = emptyList()

    fun listSafe(): List<Vod> = list ?: emptyList()

    fun header(): Map<String, String> = Json.toMap(headerElement)

    fun needParse(): Boolean = parse == 1 || jx == 1

    fun url(): String {
        val element = urlElement ?: return ""
        if (element.isJsonPrimitive) return element.asString
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val direct = Json.safeString(obj.get("url"))
            if (direct.isNotEmpty()) return direct
            val values = obj.get("values")?.takeIf { it.isJsonArray }?.asJsonArray
            // Url 对象：position 是"从 0 起算的画质索引"，要按它取，不能一律取第 0 项
            val pos = runCatching { obj.get("position")?.asInt ?: 0 }.getOrDefault(0)
            var chosen: JsonElement? = null
            if (values != null) {
                chosen = if (pos >= 0 && pos < values.size()) values.get(pos) else null
                if (chosen == null || !chosen.isJsonObject) chosen = values.firstOrNull { it.isJsonObject }
            }
            if (chosen != null && chosen.isJsonObject) {
                val v = Json.safeString(chosen.asJsonObject.get("v"))
                if (v.isNotEmpty()) return v
            }
            return Json.safeString(obj.get("m3u8") ?: obj.get("v"))
        }
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            // ① 对象数组：[{ "n":"主画质","v":"https://…" }, …]
            arr.firstOrNull { it.isJsonObject }?.asJsonObject?.let {
                val v = Json.safeString(it.get("v")).ifEmpty { Json.safeString(it.get("url")) }
                if (v.isNotEmpty()) return v
            }
            // ② 字符串数组：按配置字典是「名称, URL」成对排列（URL 在奇数位），
            //    也兼容"纯 URL 清单"。所以取第一个像 URL 的；都不像才退回第一项。
            //    （旧实现直接取第 0 项，成对写法下会返回"主画质"这种名称 → 必然播不了）
            val strings = arr.filter { it.isJsonPrimitive }.map { it.asString }
            strings.firstOrNull { looksUrl(it) }?.let { return it }
            return strings.firstOrNull().orEmpty()
        }
        return ""
    }

    /** 像不像可播放地址（用于在"名称/URL 成对"数组里挑出真正的 URL）。 */
    private fun looksUrl(text: String): Boolean {
        val t = text.trim().lowercase()
        return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("rtsp://") ||
            t.startsWith("rtmp://") || t.startsWith("ftp://") || t.startsWith("magnet:") ||
            t.startsWith("ed2k:") || t.startsWith("thunder:") || t.contains("://")
    }

    /**
     * playUrl 的语义（照影視TV 配置字典）：`json:<JSON 解析端点>` / `parse:<解析器名>`；
     * 其它值按历史行为当"URL 前缀"。返回：0 空、1 json:、2 parse:、3 URL 前缀。
     */
    fun playUrlKind(): Int = when {
        playUrl.startsWith("json:") -> 1
        playUrl.startsWith("parse:") -> 2
        playUrl.isNotEmpty() -> 3
        else -> 0
    }

    fun playUrlValue(): String = when (playUrlKind()) {
        1 -> playUrl.removePrefix("json:")
        2 -> playUrl.removePrefix("parse:")
        else -> playUrl
    }

    // json: / parse: 两种前缀不能直接拼到播放地址上（旧实现会拼出 "parse:xxhttps://…" 这种废地址）
    fun realUrl(): String = if (playUrlKind() == 3) playUrl + url() else url()

    fun normalize(): Result {
        classElement?.let { element ->
            val list = mutableListOf<VodClass>()
            if (element.isJsonArray) {
                element.asJsonArray.forEach { item ->
                    if (item.isJsonObject) list.add(VodClass.from(item.asJsonObject))
                    else list.add(VodClass().apply { typeName = Json.safeString(item); typeId = typeName })
                }
            } else if (element.isJsonObject) {
                element.asJsonObject.entrySet().forEach { (k, v) -> list.add(VodClass().apply { typeId = k; typeName = Json.safeString(v) }) }
            }
            types = list
        }
        filtersElement?.let { element ->
            filters.clear()
            if (element.isJsonObject) {
                element.asJsonObject.entrySet().forEach { (key, value) ->
                    val parsed = parseFilters(value)
                    if (parsed.isNotEmpty()) filters[key] = parsed
                }
            } else if (element.isJsonArray) {
                parseFilters(element).forEach { filters[it.name] = listOf(it) }
            }
        }
        return this
    }

    private fun parseFilters(element: JsonElement): List<Filter> {
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            if (!item.isJsonObject) return@mapNotNull null
            runCatching { Json.gson.fromJson(item, Filter::class.java) }.getOrNull()
        }
    }

    companion object {
        fun empty(): Result = Result()

        fun error(msg: String): Result = Result().apply { this.msg = msg; code = 0 }

        fun fromJson(text: String?): Result {
            if (text.isNullOrBlank()) return empty()
            val strict = runCatching { Json.gson.fromJson(text, Result::class.java) }
            strict.getOrNull()?.let { return it.normalize() }
            // 严格解析失败时不要静默退回空结果（曾导致"蜘蛛明明返回了 10KB 数据，首页却全空"）。
            // 记录真实原因，并用逐字段容错解析兜底，保证首页/搜索/详情仍能出内容。
            Diag.warn("Parse", "Result 严格解析失败，改用容错解析：${strict.exceptionOrNull()?.message?.take(300)}")
            return tolerant(text)
        }

        /** 容错解析：任一字段类型不符都不会让整包失败。 */
        private fun tolerant(text: String): Result {
            val result = Result()
            val root = runCatching { JsonParser.parseString(text) }.getOrNull() ?: return result
            if (!root.isJsonObject) return result
            val obj = root.asJsonObject
            obj.get("class")?.let { result.classElement = it }
            obj.get("filters")?.let { result.filtersElement = it }
            obj.get("url")?.let { result.urlElement = it }
            obj.get("header")?.let { result.headerElement = it }
            obj.get("msg")?.let { result.msg = Json.safeString(it) }
            obj.get("playUrl")?.let { result.playUrl = Json.safeString(it) }
            obj.get("flag")?.let { result.flag = Json.safeString(it) }
            obj.get("format")?.let { result.format = Json.safeString(it) }
            obj.get("pagecount")?.let { runCatching { result.pagecount = it.asInt } }
            obj.get("code")?.let { runCatching { result.code = it.asInt } }
            obj.get("jx")?.let { runCatching { result.jx = it.asInt } }
            obj.get("parse")?.let { runCatching { result.parse = it.asInt } }
            obj.get("list")?.takeIf { it.isJsonArray }?.let { array ->
                result.list = array.asJsonArray.mapNotNull { item ->
                    if (item.isJsonObject) vodFrom(item.asJsonObject) else null
                }
            }
            return result.normalize()
        }

        /** 逐字段安全映射 Vod，避免 Gson 因某个字段类型不符而整包失败。 */
        private fun vodFrom(obj: JsonObject): Vod = Vod().apply {
            vodId = Json.safeString(obj.get("vod_id") ?: obj.get("id"))
            vodName = Json.safeString(obj.get("vod_name") ?: obj.get("name"))
            typeName = Json.safeString(obj.get("type_name"))
            vodPic = Json.safeString(obj.get("vod_pic") ?: obj.get("pic"))
            vodRemarks = Json.safeString(obj.get("vod_remarks") ?: obj.get("remarks"))
            vodYear = Json.safeString(obj.get("vod_year"))
            vodArea = Json.safeString(obj.get("vod_area"))
            vodActor = Json.safeString(obj.get("vod_actor"))
            vodDirector = Json.safeString(obj.get("vod_director"))
            vodContent = Json.safeString(obj.get("vod_content") ?: obj.get("content"))
            vodPlayFrom = Json.safeString(obj.get("vod_play_from"))
            vodPlayUrl = Json.safeString(obj.get("vod_play_url"))
            vodTag = Json.safeString(obj.get("vod_tag"))
        }

        fun list(items: List<Vod>): Result = Result().apply { list = items }
    }
}
