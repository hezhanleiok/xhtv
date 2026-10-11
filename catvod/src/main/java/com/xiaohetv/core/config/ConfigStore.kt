package com.xiaohetv.core.config

import com.xiaohetv.core.Core
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.utils.Json
import java.io.File

/**
 * 订阅配置的持久化（configs.json），点播 / 直播 / 壁纸共用一份表。
 */
object ConfigStore {

    private const val FILE = "configs.json"

    private val list = mutableListOf<Config>()
    private var ready = false

    @Synchronized
    fun init() {
        if (ready) return
        ready = true
        runCatching {
            val file = file()
            if (file.exists()) {
                val parsed = Json.gson.fromJson(file.readText(), Array<Config>::class.java)?.toList().orEmpty()
                list.clear()
                list.addAll(parsed)
            }
        }
    }

    private fun file(): File = File(Core.context().filesDir, FILE)

    @Synchronized
    fun all(type: Int): List<Config> {
        init()
        return list.filter { it.type == type }.sortedByDescending { it.time }
    }

    @Synchronized
    fun current(type: Int): Config? {
        init()
        return list.filter { it.type == type }.maxByOrNull { it.time }
    }

    @Synchronized
    fun find(url: String, type: Int): Config {
        init()
        return list.firstOrNull { it.url == url && it.type == type } ?: Config().apply {
            this.url = url
            this.type = type
        }
    }

    @Synchronized
    fun save(config: Config) {
        init()
        val index = list.indexOfFirst { it.type == config.type && it.url == config.url }
        if (index >= 0) list[index] = config else list.add(config)
        persist()
    }

    /** 更新时间戳，使其成为该类型的当前选中。 */
    @Synchronized
    fun select(config: Config) {
        config.time = System.currentTimeMillis()
        save(config)
    }

    @Synchronized
    fun delete(config: Config) {
        init()
        list.removeAll { it.type == config.type && it.url == config.url }
        persist()
    }

    @Synchronized
    fun delete(url: String, type: Int) {
        init()
        list.removeAll { it.type == type && it.url == url }
        persist()
    }

    @Synchronized
    fun clearAll() {
        init()
        list.clear()
        persist()
    }

    @Synchronized
    fun restore(items: List<Config>) {
        init()
        list.clear()
        list.addAll(items)
        persist()
    }

    @Synchronized
    fun persist() {
        runCatching { file().writeText(Json.gson.toJson(list)) }
    }
}
