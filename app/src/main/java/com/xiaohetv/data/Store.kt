package com.xiaohetv.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * 轻量本地存储：站点开关、播放记录、收藏、各类设置。
 * 订阅配置由 com.xiaohetv.core.config.ConfigStore 管理。
 */
object Store {

    data class SiteState(
        var key: String = "",
        var searchable: Int = 1,
        var changeable: Int = 1
    )

    data class History(
        var siteKey: String = "",
        var vodId: String = "",
        var name: String = "",
        var pic: String = "",
        var remark: String = "",
        var flag: String = "",
        var episode: String = "",
        var url: String = "",
        var position: Long = 0,
        var duration: Long = 0,
        var update: Long = 0
    )

    data class Collect(
        var siteKey: String = "",
        var vodId: String = "",
        var name: String = "",
        var pic: String = "",
        var remark: String = "",
        var update: Long = 0
    )

    data class Settings(
        var speed: Float = 1.0f,
        var resume: Boolean = true,
        var incognito: Boolean = false,
        var size: Int = 2,
        var themeColor: Int = -1,
        var doh: String = "",
        var wall: Int = 0,
        var engine: Int = 0,
        var decode: Int = 1,
        /** 解码方式（照参考项目「解码设置」）：0=自动 1=硬件 2=软解 3=软件；视频/音频分开。 */
        var decodeVideo: Int = 0,
        var decodeAudio: Int = 0,
        var ua: String = "",
        var scale: Int = 0,
        var background: Int = 0,
        var buffer: Int = 3,
        var danmakuLoad: Boolean = true,
        var danmakuApi: String = "",
        var danmakuAuto: Boolean = true,
        var danmakuSpider: Boolean = false,
        var permAsked: Boolean = false
    )

    private val gson = Gson()
    private var root: File? = null

    val states = mutableListOf<SiteState>()
    val history = mutableListOf<History>()
    val collects = mutableListOf<Collect>()
    var settings = Settings()
    var homeSiteKey: String = ""

    /** 搜索记录（最近在前，最多 20 条）。 */
    val searchRecord = mutableListOf<String>()

    fun init(context: Context) {
        root = File(context.filesDir, "store").apply { mkdirs() }
        states.clear()
        states.addAll(read("states.json") ?: emptyList())
        history.clear()
        history.addAll(read("history.json") ?: emptyList())
        collects.clear()
        collects.addAll(read("collect.json") ?: emptyList())
        settings = readOne("settings.json") ?: Settings()
        searchRecord.clear()
        searchRecord.addAll(read("search.json") ?: emptyList())
    }

    fun saveSearch() = write("search.json", searchRecord.take(20))

    fun addSearch(word: String) {
        val text = word.trim()
        if (text.isEmpty()) return
        searchRecord.remove(text)
        searchRecord.add(0, text)
        while (searchRecord.size > 20) searchRecord.removeAt(searchRecord.size - 1)
        saveSearch()
    }

    fun clearSearch() {
        searchRecord.clear()
        saveSearch()
    }

    private fun file(name: String): File? = root?.let { File(it, name) }

    private inline fun <reified T> read(name: String): List<T>? {
        val target = file(name) ?: return null
        if (!target.exists()) return null
        return runCatching {
            val type = object : TypeToken<List<T>>() {}.type
            gson.fromJson<List<T>>(target.readText(), type)
        }.getOrNull()
    }

    private fun readOne(name: String): Settings? {
        val target = file(name) ?: return null
        if (!target.exists()) return null
        return runCatching { gson.fromJson(target.readText(), Settings::class.java) }.getOrNull()
    }

    private fun write(name: String, data: Any) {
        runCatching { file(name)?.writeText(gson.toJson(data)) }
    }

    fun saveStates() = write("states.json", states)
    fun saveHistory() = write("history.json", history.take(500))
    fun saveCollect() = write("collect.json", collects)
    fun saveSettings() = write("settings.json", settings)

    fun state(key: String): SiteState = states.find { it.key == key } ?: SiteState(key).apply { states.add(this) }

    fun applyStates(sites: List<com.xiaohetv.core.bean.Site>) {
        sites.forEach { site ->
            val state = state(site.key)
            if (state.searchable != 2) site.searchable = state.searchable
            if (state.changeable != 2) site.changeable = state.changeable
        }
    }

    fun addHistory(item: History) {
        if (settings.incognito) return
        history.removeAll { it.vodId == item.vodId && it.siteKey == item.siteKey }
        history.add(0, item)
        saveHistory()
    }

    fun findHistory(siteKey: String, vodId: String): History? =
        history.firstOrNull { it.siteKey == siteKey && it.vodId == vodId }

    fun removeHistory(item: History) {
        history.remove(item)
        saveHistory()
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    fun isCollect(siteKey: String, vodId: String): Boolean =
        collects.any { it.siteKey == siteKey && it.vodId == vodId }

    fun toggleCollect(item: Collect): Boolean {
        val exist = collects.firstOrNull { it.siteKey == item.siteKey && it.vodId == item.vodId }
        if (exist != null) collects.remove(exist) else collects.add(0, item)
        saveCollect()
        return exist == null
    }

    fun removeCollect(item: Collect) {
        collects.remove(item)
        saveCollect()
    }

    /** 图片尺寸：0 小 / 1 中 / 2 大 / 3 超大 */
    fun posterRatio(): Float = when (settings.size) {
        0 -> 0.62f
        1 -> 0.75f
        2 -> 0.9f
        else -> 1.05f
    }

    fun sizeText(): String = listOf("小", "中", "大", "超大").getOrElse(settings.size) { "大" }

    fun themeText(): String = when (settings.themeColor) {
        -1 -> "关"
        0 -> "自动"
        else -> "自定义"
    }

    fun incognitoText(): String = if (settings.incognito) "开" else "关"

    fun scaleText(): String = listOf("默认", "16:9", "4:3", "填充", "裁剪").getOrElse(settings.scale) { "默认" }

    fun engineText(): String = if (settings.engine == 1) "MPV" else "EXO"

    fun decodeText(): String = if (settings.decode == 1) "硬解" else "软解"

    /** 解码设置里的模式名（0=自动 1=硬件 2=软解 3=软件）。 */
    fun decodeModeText(mode: Int): String = listOf("自动", "硬件", "软解", "软件").getOrElse(mode) { "自动" }

    fun backgroundText(): String = listOf("跟随系统", "始终后台", "从不后台").getOrElse(settings.background) { "跟随系统" }

    fun backupRestore() {
        settings = Settings()
        saveSettings()
    }
}
