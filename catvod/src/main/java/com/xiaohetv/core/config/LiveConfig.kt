package com.xiaohetv.core.config

import com.google.gson.JsonObject
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.bean.Doh
import com.xiaohetv.core.bean.Header
import com.xiaohetv.core.bean.Live
import com.xiaohetv.core.bean.Proxy
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.parser.LiveParser
import com.xiaohetv.core.utils.Json
import com.xiaohetv.core.utils.Tasks
import com.xiaohetv.core.utils.UriUtil

/**
 * 直播配置：JSON（lives）或纯文本（M3U/TXT）。
 */
object LiveConfig {

    var config: Config? = null
    val lives = mutableListOf<Live>()
    var home: Live? = null
    var ads: List<String> = emptyList()

    @JvmStatic
    fun getDesc(): String = config?.desc().orEmpty()

    @JvmStatic
    fun clear() {
        lives.clear()
        home = null
        ads = emptyList()
    }

    @JvmStatic
    fun load(config: Config, callback: (kotlin.Result<Unit>) -> Unit = {}) {
        Tasks.run({ loadBlocking(config) }, callback)
    }

    @JvmStatic
    fun loadCached(config: Config, callback: (kotlin.Result<Unit>) -> Unit = {}) {
        Tasks.run({
            if (Json.isObj(config.json)) {
                val obj = Json.obj(config.json) ?: throw Exception("缓存配置无效")
                parseObject(config, obj, config.json, select = false)
            } else if (config.json.isNotEmpty()) {
                parseText(config, config.json, select = false)
            } else {
                throw Exception("没有缓存")
            }
        }, callback)
    }

    @JvmStatic
    fun loadBlocking(config: Config) {
        val loaded = ConfigLoader.fetchWithBase(config.url)
        val json = ConfigLoader.expand(loaded.base, loaded.text)
        if (Json.isObj(json)) {
            val obj = Json.obj(json) ?: throw Exception("直播配置格式错误")
            if (obj.has("msg")) throw Exception(Json.str(obj, "msg"))
            if (obj.has("urls")) {
                loadDepot(config, obj, loaded.base)
                return
            }
            parseObject(config, obj, json, base = loaded.base)
        } else {
            parseText(config, loaded.text)
        }
    }

    private fun loadDepot(config: Config, obj: JsonObject, base: String) {
        val depots = ConfigLoader.depots(obj.toString())
        if (depots.isEmpty()) throw Exception("配置仓库中没有可用地址")
        val children = depots.map { depot ->
            ConfigStore.find(UriUtil.resolve(base, depot.url), Config.LIVE).apply {
                if (depot.name.isNotEmpty()) name = depot.name
                ConfigStore.save(this)
            }
        }
        ConfigStore.delete(config)
        loadBlocking(children.first())
    }

    @JvmStatic
    fun parseObject(config: Config, obj: JsonObject, json: String? = null, select: Boolean = true, base: String = config.url) {
        lives.clear()
        ads = emptyList()
        val configSpider = resolve(base, Json.str(obj, "spider"))
        val array = obj.getAsJsonArray("lives")
        array?.forEach { item ->
            val live = runCatching { Json.gson.fromJson(item, Live::class.java) }.getOrNull() ?: return@forEach
            live.url = resolve(base, live.url)
            live.api = resolve(base, live.api)
            live.epg = resolve(base, live.epg)
            // 与点播一致：live 没写 jar 时回退到配置顶层的 spider
            if (live.jar.isNullOrBlank() && configSpider.isNotEmpty()) live.jar = configSpider
            if (live.name.isNotEmpty()) lives.add(live)
        }
        applyNetwork(obj)
        config.json = json ?: obj.toString()
        this.config = config
        if (select) ConfigStore.select(config) else ConfigStore.save(config)
        home = lives.firstOrNull { it.name == config.home } ?: lives.firstOrNull()
    }

    @JvmStatic
    fun parseText(config: Config, text: String, select: Boolean = true) {
        clear()
        val live = Live().apply {
            name = config.name.ifEmpty { "直播" }
            url = config.url
        }
        lives.add(live)
        LiveParser.text(live, text)
        config.json = text
        this.config = config
        if (select) ConfigStore.select(config) else ConfigStore.save(config)
        home = live
    }

    private fun applyNetwork(obj: JsonObject, base: String = "") {
        obj.getAsJsonArray("headers")?.let { array ->
            OkHttp.setHeaders(array.mapNotNull { runCatching { Json.gson.fromJson(it, Header::class.java) }.getOrNull() })
        }
        obj.getAsJsonArray("proxy")?.let { array ->
            OkHttp.setProxy(array.mapNotNull { runCatching { Json.gson.fromJson(it, Proxy::class.java) }.getOrNull() })
        }
        obj.getAsJsonArray("doh")?.let { array ->
            OkHttp.setDoh(array.mapNotNull { runCatching { Json.gson.fromJson(it, Doh::class.java) }.getOrNull() })
        }
    }

    private fun resolve(base: String, path: String): String {
        if (path.isEmpty() || base.isEmpty()) return path
        if (path.startsWith("./") || path.startsWith("../")) return UriUtil.resolve(base, path)
        return path
    }

    @JvmStatic
    fun getLive(name: String): Live? = lives.firstOrNull { it.name == name }

    @JvmStatic
    fun setHomeLive(live: Live) {
        home = live
        config?.let {
            it.home = live.name
            ConfigStore.save(it)
        }
    }
}
