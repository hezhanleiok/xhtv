package com.xiaohetv.player

import android.net.Uri
import android.os.SystemClock
import com.xiaohetv.core.Core
import com.xiaohetv.core.bean.Episode
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Diag
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
        val save = dir(Util.md5(url))
        val taskId = XLTaskHelper.get().parse(url, save)
        if (!torrent && !taskId.realUrl.startsWith("magnet")) {
            // ed2k / thunder:// 直接给出真实地址
            return listOf(Episode().apply {
                name = taskId.fileName
                this.url = taskId.realUrl
            })
        }
        if (torrent && url.startsWith("http")) taskId.saveFile.writeBytes(OkHttp.bytes(url))
        Diag.log("Thunder", "磁力任务已建立：taskId=${taskId.taskId} name=${taskId.fileName} taskFile=${taskId.saveFile.name}")
        // 反编译 XLTaskHelper.parse 可见：createBtMagnetTask 返回非 9000 时它**静默 return**（taskId 无效），
        // 我们以前接着白等 30 秒再报"解析失败"，看不到真正原因 → 这里显式判无效任务。
        if (taskId.taskId == 0L) throw Exception("迅雷核心创建磁力任务失败（未联网或被拦截）")

        // 关键修复：磁力元数据（.torrent）是迅雷核心下载后**落在保存目录**里的
        // （库自己的 deleteFile 专门跳过 .torrent 文件，可佐证 torrent 就在保存目录）。
        // 以前我们拿 taskId.saveFile 去解析 —— 那是最终媒体文件路径，此时根本不存在 → 永远解析失败。
        val torrentFile = when {
            torrent && taskId.saveFile.exists() -> taskId.saveFile
            torrent -> File(Uri.parse(url).path ?: url).takeIf { it.exists() }
            else -> waitTorrent(save, 60_000L)
        } ?: throw Exception("磁力元数据获取超时（60 秒没拿到种子，可能没有可用节点）")
        val medias = XLTaskHelper.get().getTorrentInfo(torrentFile).medias
            .filter { it.fileSize > 1024 * 1024 && VIDEO_EXT.contains(it.ext) }
            .sortedBy { it.fileName }
            .map { info ->
                Episode().apply {
                    name = info.fileName
                    desc = info.size
                    this.url = info.playUrl
                }
            }
        Diag.log(
            "Thunder",
            "种子=${torrentFile.name} 可播放文件 ${medias.size} 个" +
                if (medias.isEmpty()) "" else "：${medias.take(3).joinToString { it.name }}"
        )
        return medias
    }

    /** 等保存目录里出现 .torrent（磁力核心下载完元数据后落盘）。 */
    private fun waitTorrent(save: File, timeoutMs: Long): File? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            save.listFiles { f -> f.isFile && f.name.endsWith(".torrent", true) }
                ?.firstOrNull()
                ?.let { return it }
            if (System.currentTimeMillis() >= deadline) return null
            SystemClock.sleep(300)
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
        Diag.log("Thunder", "已加入下载任务：index=$index name=$name torrent=${torrent.name} taskId=${taskId.taskId}")
        if (taskId.taskId == 0L) throw Exception("迅雷核心创建下载任务失败（未联网或被拦截）")
        // 关键修复：以前 `mTaskStatus != 0` 就返回 —— 那只是"任务已登记"，可能还没开始下载，
        // 播放器拿到本地地址后永远 0 KB/s（"没有速度"就是这么来的）。现在等状态 >= 2（下载中）再返回；
        // 若 60 秒仍未进入下载中，则回退为旧行为（照样返回本地地址，但日志写明状态），不会比原来更差。
        var lastStatus = -1
        for (i in 0 until 300) {
            val info = XLTaskHelper.get().getBtSubTaskInfo(taskId, index).mTaskInfo
            lastStatus = info.mTaskStatus
            if (lastStatus == 3) throw Exception(info.getErrorMsg())
            if (lastStatus >= 2) {
                val local = XLTaskHelper.get().getLocalUrl(File(parent, name))
                Diag.log("Thunder", "已开始下载(status=$lastStatus)，本地播放地址：$local")
                return local
            }
            SystemClock.sleep(200)
        }
        Diag.warn("Thunder", "60 秒未进入下载中(status=$lastStatus)，回退直接取本地地址")
        return XLTaskHelper.get().getLocalUrl(File(parent, name))
    }

    @JvmStatic
    fun release() {
        runCatching { XLTaskHelper.get().release() }
    }
}
