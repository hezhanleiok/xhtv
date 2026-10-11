package com.xiaohetv.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohetv.core.Core
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.config.ConfigStore
import com.xiaohetv.core.config.LiveConfig
import com.xiaohetv.core.config.VodConfig
import com.xiaohetv.core.config.WallConfig
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.core.utils.Tasks
import com.xiaohetv.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 订阅（点播 / 直播 / 壁纸）管理：
 * 添加即解析，点播配置里的 lives 会自动登记为直播配置，wallpaper 会自动登记为壁纸配置。
 */
class ConfigVM : ViewModel() {

    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val vodDesc = MutableStateFlow("")
    val liveDesc = MutableStateFlow("")
    val wallDesc = MutableStateFlow("")
    val version = MutableStateFlow("1.0.0")

    fun refreshDesc() {
        vodDesc.value = VodConfig.getDesc()
        liveDesc.value = LiveConfig.getDesc()
        wallDesc.value = WallConfig.getDesc()
    }

    /**
     * 启动：先用本地缓存恢复（解析完成后再刷新站点），随后自动联网刷新一次。
     * 注意 loadCached 是异步的，必须在它的回调里刷新站点，否则站点列表会一直是空的。
     */
    fun boot(sites: SiteVM) {
        ConfigStore.init()
        applyDoh()
        restoreWall()
        refreshDesc()

        val vod = ConfigStore.current(Config.VOD)
        val live = ConfigStore.current(Config.LIVE)
        Diag.log("Config", "启动引导：vod 缓存 ${vod?.json?.length ?: 0} 字节，live 缓存 ${live?.json?.length ?: 0} 字节")

        if (vod != null && vod.json.isNotEmpty()) {
            VodConfig.loadCached(vod) {
                Diag.log("Config", "缓存恢复完成：站点 ${VodConfig.sites.size} 个")
                refreshDesc()
                sites.refresh(false)
                // 缓存恢复后仍需联网刷新，拿最新配置
                refresh(sites)
            }
        } else {
            // 没有缓存：直接联网加载
            message.value = "正在加载配置…"
            refresh(sites)
        }

        if (live != null && live.json.isNotEmpty()) {
            LiveConfig.loadCached(live) { refreshDesc() }
        }
    }

    /** 恢复用户选择的 DoH 服务器。 */
    private fun applyDoh() {
        val saved = Store.settings.doh
        if (saved.isEmpty()) return
        runCatching {
            val doh = com.xiaohetv.core.utils.Json.gson.fromJson(saved, com.xiaohetv.core.bean.Doh::class.java)
            if (doh != null) com.xiaohetv.core.net.OkHttp.setDoh(listOf(doh))
        }
    }

    private fun restoreWall() {
        val file = File(Core.context().filesDir, "wall")
        if (file.exists() && file.length() > 0) {
            WallConfig.file = file
            WallConfig.config = ConfigStore.current(Config.WALL)
        }
    }

    /** 网络刷新当前选中的点播 / 直播配置。 */
    fun refresh(sites: SiteVM) {
        if (busy.value) return
        val vod = ConfigStore.current(Config.VOD)
        val live = ConfigStore.current(Config.LIVE)
        if (vod == null && live == null) {
            message.value = "还没有订阅，请到设置里添加配置地址"
            return
        }
        vod?.let { load(it, sites) { _, _ -> } }
        live?.let { load(it) { _, _ -> refreshDesc() } }
    }

    /** 批量重载全部订阅（设置页「全部刷新」）。 */
    fun reloadAll(sites: SiteVM, onDone: (String) -> Unit = {}) {
        val vod = ConfigStore.current(Config.VOD)
        val live = ConfigStore.current(Config.LIVE)
        if (vod == null && live == null) {
            onDone("还没有订阅")
            return
        }
        load(vod ?: live!!, sites) { _, msg ->
            if (live != null && vod != null) load(live, sites) { _, _ -> refreshDesc() }
            onDone(msg)
        }
    }

    fun submit(sites: SiteVM?, type: Int, url: String, name: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val trim = url.trim()
        if (trim.isEmpty()) {
            onDone(false, "请输入配置地址")
            return
        }
        val config = ConfigStore.find(trim, type).apply { if (name.isNotBlank()) this.name = name }
        ConfigStore.save(config)
        load(config, sites, onDone)
    }

    fun edit(sites: SiteVM?, config: Config, url: String, name: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val trim = url.trim()
        if (trim.isEmpty()) {
            remove(config, sites)
            onDone(false, "已删除该订阅")
            return
        }
        val old = config.url
        config.url = trim
        config.name = name
        ConfigStore.save(config)
        if (old != trim) {
            // 地址变更：老的 json 缓存作废
            config.json = ""
            ConfigStore.save(config)
        }
        load(config, sites, onDone)
    }

    fun remove(config: Config, sites: SiteVM? = null) {
        ConfigStore.delete(config)
        if (config.isVod() && VodConfig.config?.url == config.url) {
            VodConfig.clear()
            WallConfig.clear()
        }
        if (config.isLive() && LiveConfig.config?.url == config.url) LiveConfig.clear()
        sites?.refresh(false)
        refreshDesc()
    }

    fun select(config: Config, sites: SiteVM, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        load(config, sites) { ok, msg -> onDone(ok, msg) }
    }

    private fun load(config: Config, sites: SiteVM? = null, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            busy.value = true
            message.value = "正在解析配置…"
            Diag.log("Config", "联网加载：${Diag.safeUrl(config.url)} (type=${config.type})")
            val callback: (Result<Unit>) -> Unit = { result ->
                Tasks.post {
                    busy.value = false
                    val ok = result.isSuccess
                    message.value = if (ok) "配置加载完成" else (result.exceptionOrNull()?.message ?: "配置加载失败")
                    if (ok) {
                        Diag.log("Config", "加载成功：站点 ${VodConfig.sites.size} 个 / 直播 ${LiveConfig.lives.size} 个")
                        // 同步写入兼容库 databases/tv 的 Config 表（部分真实 spider 会直接读它）
                        com.xiaohetv.core.utils.LegacyDb.saveConfig(config.url, config.type, config.name)
                        // 统一在这里刷新站点列表：修复"添加/编辑/删除订阅后首页要手动刷新"的问题
                        sites?.refresh(false)
                    } else {
                        Diag.error("Config", "加载失败：${message.value}", result.exceptionOrNull())
                    }
                    refreshDesc()
                    onDone(ok, message.value)
                }
            }
            when (config.type) {
                Config.VOD -> VodConfig.load(config, callback)
                Config.LIVE -> LiveConfig.load(config, callback)
                else -> WallConfig.load(config, callback)
            }
        }
    }
}
