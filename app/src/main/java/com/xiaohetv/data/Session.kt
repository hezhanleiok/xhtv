package com.xiaohetv.data

import com.xiaohetv.core.bean.Channel
import com.xiaohetv.core.bean.Live
import com.xiaohetv.core.bean.Site
import com.xiaohetv.core.bean.Vod

/**
 * 跨页面共享的运行时状态（点播 / 直播 / 直链播放入口）。
 */
object Session {

    var site: Site? = null
    var vod: Vod? = null
    var live: Live? = null
    var channel: Channel? = null

    /** 直接输入网址 / 磁力链接播放时使用 */
    var directUrl: String = ""
    var directTitle: String = ""
    var directHeaders: Map<String, String> = emptyMap()

    var flagIndex: Int = 0
    var episodeIndex: Int = 0
    var searchKey: String = ""

    /** 置 true 后进入搜索页会自动用 searchKey 搜一次（首页点海报按片名找源用）。 */
    var searchAuto: Boolean = false

    /** 详情页切全屏时带过来的播放进度（0 = 不续播），播放器起播后消费并清零。 */
    var startPositionMs: Long = 0

    /**
     * 置 true 表示"刚点了直播，去播放器里加载直播列表"：
     * 播放页会横屏显示加载中，拿到频道后直接播第一个频道。
     * 这样点直播是**直接进横屏播放器**，不会先竖屏加载再旋转。
     */
    var livePending: Boolean = false
    var hotKeys: List<String> = listOf("热门", "电影", "剧集", "综艺", "动漫")

    fun clearPlayback() {
        vod = null
        live = null
        channel = null
        directUrl = ""
        directTitle = ""
        directHeaders = emptyMap()
    }
}
