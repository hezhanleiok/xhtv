package com.xiaohetv.core.config

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.bean.Doh
import com.xiaohetv.core.bean.Header
import com.xiaohetv.core.bean.Live
import com.xiaohetv.core.bean.Parse
import com.xiaohetv.core.bean.Proxy
import com.xiaohetv.core.bean.Site
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Json
import com.xiaohetv.core.utils.Tasks
import com.xiaohetv.core.utils.UriUtil

/**
 * 点播配置：站点、解析器、内嵌直播与内嵌壁纸。
 */
object VodConfig {

    var config: Config? = null
    var spider: String = ""
    var wallpaper: String = ""
    var logo: String = ""
    var notice: String = ""
    var flags: List<String> = emptyList()
    var ads: List<String> = emptyList()
    var extraDoh: List<Doh> = emptyList()

    val sites = mutableListOf<Site>()
    val parses = mutableListOf<Parse>()
    val lives = mutableListOf<Live>()

    var home: Site? = null

    @JvmStatic
    fun getDesc(): String = config?.desc().orEmpty()

    @JvmStatic
    fun isEmpty(): Boolean = sites.isEmpty()

    @JvmStatic
    fun clear() {
        // 与 FongMi 一致：重载配置时清空已缓存的 spider / jar loader，
        // 否则换了新配置或之前加载失败（SpiderNull）时会一直用旧的。
        com.xiaohetv.core.spider.SpiderRouter.clear()
        spider = ""
        wallpaper = ""
        logo = ""
        notice = ""
        flags = emptyList()
        ads = emptyList()
        extraDoh = emptyList()
        sites.clear()
        parses.clear()
        lives.clear()
        home = null
    }

    @JvmStatic
    fun load(config: Config, callback: (kotlin.Result<Unit>) -> Unit = {}) {
        Tasks.run({ loadBlocking(config) }, callback)
    }

    /** 启动时用本地缓存的配置正文直接恢复，不需要联网。 */
    @JvmStatic
    fun loadCached(config: Config, callback: (kotlin.Result<Unit>) -> Unit = {}) {
        Tasks.run({
            val obj = Json.obj(config.json) ?: throw Exception("缓存配置无效")
            parseObject(config, obj, config.json, select = false)
        }, callback)
    }

    @JvmStatic
    fun loadBlocking(config: Config) {
        val loaded = ConfigLoader.fetchWithBase(config.url)
        val json = ConfigLoader.expand(loaded.base, loaded.text)
        val obj = Json.obj(json) ?: throw Exception("配置格式错误（不是 JSON）")
        if (obj.has("msg")) throw Exception(Json.str(obj, "msg"))
        if (obj.has("urls")) {
            loadDepot(config, obj, loaded.base)
            return
        }
        parseObject(config, obj, json, base = loaded.base)
    }

    /** 配置仓库：把仓库里的每个地址登记为独立订阅，并加载第一条。 */
    private fun loadDepot(config: Config, obj: JsonObject, base: String) {
        val depots = ConfigLoader.depots(obj.toString())
        if (depots.isEmpty()) throw Exception("配置仓库中没有可用地址")
        val children = depots.map { depot ->
            ConfigStore.find(UriUtil.resolve(base, depot.url), Config.VOD).apply {
                if (depot.name.isNotEmpty()) name = depot.name
                ConfigStore.save(this)
            }
        }
        ConfigStore.delete(config)
        loadBlocking(children.first())
    }

    /** 解析一份点播配置对象（也用于点播配置内嵌 lives / wallpaper 的登记）。 */
    @JvmStatic
    fun parseObject(config: Config, obj: JsonObject, json: String? = null, select: Boolean = true, base: String = config.url) {
        clear()
        spider = resolve(base, Json.str(obj, "spider"))
        wallpaper = resolve(base, Json.str(obj, "wallpaper"))
        logo = resolve(base, Json.str(obj, "logo"))
        notice = Json.str(obj, "notice")
        flags = strings(obj, "flags")
        ads = strings(obj, "ads")
        extraDoh = array<Doh>(obj, "doh", base)
        sites.addAll(array<Site>(obj, "sites", base).map { fixSite(it, base, spider) }.filter { it.key.isNotEmpty() })
        parses.addAll(array<Parse>(obj, "parses", base).map { fixParse(it, base) }.filter { it.name.isNotEmpty() })
        lives.addAll(array<Live>(obj, "lives", base).filter { it.name.isNotEmpty() })
        OkHttp.setHeaders(array<Header>(obj, "headers", base))
        OkHttp.setProxy(array<Proxy>(obj, "proxy", base))
        OkHttp.setHosts(strings(obj, "hosts"))
        OkHttp.setDoh(array<Doh>(obj, "doh", base))

        config.json = json ?: obj.toString()
        this.config = config
        if (select) ConfigStore.select(config) else ConfigStore.save(config)
        home = sites.firstOrNull { !config.home.isNullOrEmpty() && it.key == config.home } ?: sites.firstOrNull { it.visible() }

        initLive(config, obj, base)
        initWall(config, obj, json, base)
    }

    /**
     * 点播配置里带 lives 时，自动登记一条同名同址的「直播」订阅。
     * 这是"添加一个地址，直播也自动解析出来"的关键。
     */
    private fun initLive(config: Config, obj: JsonObject, base: String) {
        val array = obj.getAsJsonArray("lives") ?: return
        if (array.size() == 0) return
        val temp = ConfigStore.find(config.url, Config.LIVE).apply { name = config.name }
        ConfigStore.save(temp)
        // 点播配置内嵌的直播登记为当前直播订阅（总是同步，确保"一个地址带直播"必然可用）
        ConfigStore.select(temp)
        LiveConfig.parseObject(temp, obj, config.json, base = base, select = true)
    }

    private fun initWall(config: Config, obj: JsonObject, json: String?, base: String) {
        val url = Json.str(obj, "wallpaper")
        if (url.isEmpty()) return
        val wall = ConfigStore.find(resolve(base, url), Config.WALL).apply { name = config.name }
        ConfigStore.save(wall)
        if (ConfigStore.current(Config.WALL) == null) WallConfig.load(wall)
    }

    /**
     * 站点修正：相对路径展开 + **站点没有 jar 时回退到配置顶层的 spider**（FongMi 同款逻辑）。
     * 很多配置只在顶层写 "spider"，各个 csp_ 站点自己不写 jar。
     */
    private fun fixSite(site: Site, base: String, spider: String): Site {
        site.api = resolve(base, site.api)
        site.jar = site.jar?.takeIf { it.isNotBlank() }?.let { resolve(base, it) } ?: spider.ifEmpty { null }
        val ext = site.extString()
        if (ext.startsWith("./") || ext.startsWith("../")) site.ext = Json.primitive(UriUtil.resolve(base, ext))
        return site
    }

    private fun fixParse(parse: Parse, base: String): Parse {
        parse.url = resolve(base, parse.url)
        return parse
    }

    private fun resolve(base: String, path: String): String {
        if (path.isEmpty()) return path
        if (path.startsWith("./") || path.startsWith("../")) return UriUtil.resolve(base, path)
        return path
    }

    private fun strings(obj: JsonObject, key: String): List<String> {
        val element = obj.get(key) ?: return emptyList()
        if (element.isJsonPrimitive) return listOf(Json.safeString(element))
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.map { Json.safeString(it) }.filter { it.isNotEmpty() }
    }

    private inline fun <reified T : Any> array(obj: JsonObject, key: String, base: String): List<T> {
        val element = obj.get(key) ?: return emptyList()
        if (element.isJsonPrimitive) return fetchArray(UriUtil.resolve(base, Json.safeString(element)))
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.flatMap { item ->
            if (item.isJsonPrimitive) fetchArray<T>(UriUtil.resolve(base, Json.safeString(item)))
            else {
                val parsed = runCatching { Json.gson.fromJson(item, T::class.java) }.getOrNull()
                if (parsed == null) emptyList() else listOf(parsed)
            }
        }
    }

    private inline fun <reified T : Any> fetchArray(url: String): List<T> = runCatching {
        val type = object : TypeToken<List<T>>() {}.type
        Json.gson.fromJson<List<T>>(ConfigLoader.fetch(url), type)
    }.getOrDefault(emptyList()).filterNotNull()

    @JvmStatic
    fun getSite(key: String): Site? = sites.firstOrNull { it.key == key }

    @JvmStatic
    fun getParse(name: String): Parse? = parses.firstOrNull { it.name == name }

    @JvmStatic
    fun visibleSites(): List<Site> = sites.filter { it.visible() }

    @JvmStatic
    fun setHomeSite(site: Site) {
        home = site
        config?.let {
            it.home = site.key
            ConfigStore.save(it)
        }
    }

    @JvmStatic
    fun setParse(name: String) {
        config?.let {
            it.parse = name
            ConfigStore.save(it)
        }
    }

    @JvmStatic
    fun getDoh(): List<Doh> = Doh.builtin() + extraDoh
}
