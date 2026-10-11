package com.xiaohetv.vm

import androidx.lifecycle.ViewModel
import com.xiaohetv.core.bean.Site
import com.xiaohetv.core.config.VodConfig
import com.xiaohetv.data.Session
import com.xiaohetv.data.Store
import kotlinx.coroutines.flow.MutableStateFlow

class SiteVM : ViewModel() {

    val sites = MutableStateFlow<List<Site>>(emptyList())
    val current = MutableStateFlow<Site?>(null)

    /**
     * 站点/配置每次重载都自增，供首页等界面订阅后自动刷新。
     * 解决「添加订阅源后首页还要手动刷新」。
     */
    val version = MutableStateFlow(0)

    fun refresh(keep: Boolean = true) {
        val list = VodConfig.visibleSites()
        // 只有站点集合真的变了才自增 version。
        // 启动时同一份配置会被加载两次（type=0 / type=1），每次都 bump 会让首页整轮重载两遍
        // （日志实测：spider jar 初始化、Guard Init.init、homeContent 全部跑两次 → 启动期白白翻倍）。
        val changed = list.map { it.key } != sites.value.map { it.key }
        sites.value = list
        val stillThere = current.value?.let { now -> list.any { it.key == now.key } } == true
        if (!keep || !stillThere) {
            current.value = list.firstOrNull { it.key == VodConfig.home?.key } ?: list.firstOrNull()
        }
        current.value?.let { Session.site = it }
        if (changed) version.value = version.value + 1
    }

    fun select(site: Site) {
        current.value = site
        Session.site = site
        Store.homeSiteKey = site.key
        VodConfig.setHomeSite(site)
    }

    fun setSearchable(site: Site, enable: Boolean) {
        site.searchable = if (enable) 1 else 0
        val state = Store.state(site.key)
        state.searchable = site.searchable
        Store.saveStates()
    }

    fun setChangeable(site: Site, enable: Boolean) {
        site.changeable = if (enable) 1 else 0
        val state = Store.state(site.key)
        state.changeable = site.changeable
        Store.saveStates()
    }
}
