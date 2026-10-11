package com.xiaohetv.data

import com.xiaohetv.core.Core
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.config.ConfigStore
import com.xiaohetv.core.utils.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份与恢复：订阅配置 + 播放记录 + 收藏 + 站点开关 + 设置。
 */
object Backup {

    class Bundle {
        var configs: List<Config> = emptyList()
        var history: List<Store.History> = emptyList()
        var collects: List<Store.Collect> = emptyList()
        var states: List<Store.SiteState> = emptyList()
        var settings: Store.Settings = Store.Settings()
    }

    private const val EXT = ".tv"

    @JvmStatic
    fun dir(): File = File(Core.context().filesDir, "backup").apply { mkdirs() }

    @JvmStatic
    fun list(): List<File> = dir().listFiles { f -> f.name.endsWith(EXT) }?.sortedByDescending { it.name }.orEmpty()

    @JvmStatic
    fun backup(): Result<File> = runCatching {
        val bundle = Bundle().apply {
            configs = listOf(Config.VOD, Config.LIVE, Config.WALL).flatMap { ConfigStore.all(it) }
            history = Store.history.toList()
            collects = Store.collects.toList()
            states = Store.states.toList()
            settings = Store.settings
        }
        if (bundle.configs.isEmpty() && bundle.history.isEmpty()) throw Exception("没有可备份的数据")
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + EXT
        val file = File(dir(), name)
        file.writeText(Json.gson.toJson(bundle))
        trim()
        file
    }

    @JvmStatic
    fun restore(file: File): Result<Unit> = runCatching {
        if (!file.exists()) throw Exception("备份文件不存在")
        val bundle = Json.gson.fromJson(file.readText(), Bundle::class.java) ?: throw Exception("备份文件解析失败")
        ConfigStore.restore(bundle.configs)
        Store.history.clear()
        Store.history.addAll(bundle.history)
        Store.saveHistory()
        Store.collects.clear()
        Store.collects.addAll(bundle.collects)
        Store.saveCollect()
        Store.states.clear()
        Store.states.addAll(bundle.states)
        Store.saveStates()
        bundle.settings?.let {
            Store.settings = it
            Store.saveSettings()
        }
    }

    private fun trim() {
        list().drop(7).forEach { it.delete() }
    }
}
