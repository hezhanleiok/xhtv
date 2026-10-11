package com.xiaohetv.core.bean

/**
 * 一条订阅配置（对应参考项目 Room 里的 Config 表）。
 * 同一 type 下 time 最大的一条即"当前选中"。
 */
class Config {

    var type: Int = VOD
    var time: Long = 0
    var url: String = ""
    var name: String = ""
    var json: String = ""
    var home: String = ""
    var parse: String = ""

    fun desc(): String = if (name.isNotEmpty()) name else url

    fun isVod(): Boolean = type == VOD

    fun isLive(): Boolean = type == LIVE

    fun isWall(): Boolean = type == WALL

    fun key(): String = "$type|$url"

    companion object {
        const val VOD = 0
        const val LIVE = 1
        const val WALL = 2
    }
}
