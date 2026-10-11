package com.xiaohetv.player

import android.content.Context
import android.content.Intent
import com.xiaohetv.PlayerActivity
import com.xiaohetv.core.bean.Channel
import com.xiaohetv.core.bean.Live
import com.xiaohetv.core.bean.Vod
import com.xiaohetv.data.Session

object Playback {

    /** 直接播放一个网址（http/m3u8/magnet 等） */
    @JvmStatic
    fun direct(context: Context, url: String, title: String = "") {
        Session.clearPlayback()
        Session.site = null
        Session.directUrl = url
        Session.directTitle = title.ifEmpty { url }
        Session.directHeaders = emptyMap()
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    /** 播放磁力种子里挑出的文件（magnet://<torrent>?name=&index=） */
    @JvmStatic
    fun torrent(context: Context, playUrl: String, title: String) {
        Session.clearPlayback()
        Session.site = null
        Session.directUrl = playUrl
        Session.directTitle = title
        Session.directHeaders = emptyMap()
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    @JvmStatic
    fun vod(context: Context, vod: Vod, flagIndex: Int, episodeIndex: Int, startMs: Long = 0) {
        Session.live = null
        Session.channel = null
        Session.directUrl = ""
        Session.vod = vod
        Session.flagIndex = flagIndex
        Session.episodeIndex = episodeIndex
        // 详情页切全屏时带上当前位置：全屏播放器从断点继续，而不是从头开始
        Session.startPositionMs = startMs
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    @JvmStatic
    fun live(context: Context, live: Live, channel: Channel) {
        Session.vod = null
        Session.directUrl = ""
        Session.livePending = false
        Session.live = live
        Session.channel = channel
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }

    /**
     * 点底部「直播」：直接打开播放器（它默认横屏），由播放页自己加载直播列表并播第一个频道。
     * 这样不存在"主界面先竖屏加载再转横屏"的多余环节。
     */
    @JvmStatic
    fun liveEntry(context: Context) {
        Session.vod = null
        Session.directUrl = ""
        Session.live = null
        Session.channel = null
        Session.livePending = true
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }
}
