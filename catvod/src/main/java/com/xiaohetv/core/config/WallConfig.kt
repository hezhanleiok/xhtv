package com.xiaohetv.core.config

import com.xiaohetv.core.Core
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Tasks
import java.io.File

/**
 * 壁纸配置：把图片/动图/视频下载到本地。
 */
object WallConfig {

    var config: Config? = null
    var type: Int = 0
    var file: File? = null

    @JvmStatic
    fun getDesc(): String = config?.desc().orEmpty()

    @JvmStatic
    fun wallFile(): File? = file?.takeIf { it.exists() }

    @JvmStatic
    fun load(config: Config, callback: (kotlin.Result<Unit>) -> Unit = {}) {
        Tasks.run({ loadBlocking(config) }, callback)
    }

    @JvmStatic
    fun loadBlocking(config: Config) {
        val target = File(Core.context().filesDir, "wall")
        val url = config.url
        if (url.startsWith("file://")) {
            File(url.removePrefix("file://")).copyTo(target, true)
        } else if (url.startsWith("assets://")) {
            Core.context().assets.open(url.removePrefix("assets://")).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            target.writeBytes(OkHttp.bytes(url))
        }
        if (!target.exists() || target.length() <= 0) throw Exception("壁纸下载失败")
        type = when {
            url.contains(".gif", true) -> 1
            url.contains(".mp4", true) || url.contains(".webm", true) -> 2
            else -> 0
        }
        file = target
        config.json = url
        this.config = config
        ConfigStore.select(config)
    }

    @JvmStatic
    fun clear() {
        config = null
        file = null
        type = 0
    }
}
