package com.xiaohetv.player

import android.net.Uri
import android.os.SystemClock
import com.xiaohetv.core.Core
import com.xiaohetv.core.bean.Episode
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Util
import com.xunlei.downloadlib.XLTaskHelper
import com.xunlei.downloadlib.parameter.GetTaskId
import java.io.File

/**
 * 磁力 / ed2k / thunder:// 播放（迅雷下载核心，来自 app/libs/thunder-release.aar）。
 *
 * 流程（与参考项目一致）：
 *   1. parse(url)   → 得到种子里的媒体文件列表（每个文件一个 Episode）
 *   2. fetch(playUrl=magnet://<torrent路径>?name=&index=) → addTorrentTask 等待就绪 → getLocalUrl 得到可播放地址
 */
object Thunder {

    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "rmvb", "rm", "ts", "m2ts", "flv", "wmv", "mov", "mpg", "mpeg", "m4v", "webm", "iso")

    @JvmStatic
    fun match(url: String): Boolean {
        val scheme = Uri.parse(url.trim()).scheme?.lowercase().orEmpty()
        return scheme == "magnet" || scheme == "ed2k" || scheme == "thunder"
    }

    @JvmStatic
    fun isTorrentPlayUrl(url: String): Boolean =
        url.startsWith("magnet://") && !url.contains(":?xt=") && url.contains("index=")

    /** 当前设备能否使用迅雷核心（模拟器缺少对应 ABI 的原生库时会返回 false）。 */
    @JvmStatic
    fun isAvailable(): Boolean = runCatching {
        XLTaskHelper.get()
        true
    }.getOrDefault(false)

    private fun isTorrentFile(url: String): Boolean = url.endsWith(".torrent", true) || (!url.startsWith("magnet:") && url.contains(".torrent"))

    private fun dir(name: String): File = File(Core.context().filesDir, "thunder/$name").apply { mkdirs() }

    @JvmStatic
    @Throws(Exception::class)
    fun parse(url: String): List<Episode> {
        val torrent = isTorrentFile(url)
        val taskId = XLTaskHelper.get().parse(url, dir(Util.md5(url)))
        if (!torrent && !taskId.realUrl.startsWith("magnet")) {
            // ed2k / thunder:// 直接给出真实地址
            return listOf(Episode().apply {
                name = taskId.fileName
                this.url = taskId.realUrl
            })
        }
        if (torrent && url.startsWith("http")) taskId.saveFile.writeBytes(OkHttp.bytes(url))
        if (!torrent) waitMetadata(taskId)
        return XLTaskHelper.get().getTorrentInfo(taskId.saveFile).medias
            .filter { it.fileSize > 1024 * 1024 && VIDEO_EXT.contains(it.ext) }
            .sortedBy { it.fileName }
            .map { info ->
                Episode().apply {
                    name = info.fileName
                    desc = info.size
                    this.url = info.playUrl
                }
            }
    }

    private fun waitMetadata(taskId: GetTaskId) {
        for (i in 0 until 150) {
            if (runCatching { XLTaskHelper.get().getTaskInfo(taskId).taskStatus }.getOrDefault(0) == 2) return
            SystemClock.sleep(200)
        }
    }

    /** 把 magnet://<torrent文件>?name=&index= 转成可播放地址。 */
    @JvmStatic
    @Throws(Exception::class)
    fun fetch(playUrl: String): String {
        val uri = Uri.parse(playUrl)
        val torrent = File(uri.path ?: throw Exception("磁力地址无效"))
        val parent = torrent.parentFile ?: throw Exception("磁力路径无效")
        val name = uri.getQueryParameter("name").orEmpty()
        val index = uri.getQueryParameter("index")?.toIntOrNull() ?: 0
        val taskId = XLTaskHelper.get().addTorrentTask(torrent, parent, index)
        for (i in 0 until 200) {
            val info = XLTaskHelper.get().getBtSubTaskInfo(taskId, index).mTaskInfo
            if (info.mTaskStatus == 3) throw Exception(info.getErrorMsg())
            if (info.mTaskStatus != 0) return XLTaskHelper.get().getLocalUrl(File(parent, name))
            SystemClock.sleep(200)
        }
        throw Exception("下载超时，请检查网络或磁力链接")
    }

    @JvmStatic
    fun release() {
        runCatching { XLTaskHelper.get().release() }
    }
}
