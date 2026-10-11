package com.xiaohetv.core.utils

import android.os.Build
import android.util.Log
import com.xiaohetv.core.Core
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 临时「运行日志」诊断中心。
 *
 * 目的：把 订阅 → Spider → jar → Guard(native 解密) → 首页/搜索 的每一步都记录下来（含完整异常堆栈），
 * 在手机上就能查看/复制/导出，不必连电脑抓 logcat。
 *
 * 只依赖 android.util.Log + 内存环形缓冲，不引入协程/StateFlow，因此放在 catvod 模块里
 * 也能被 JarLoader / SpiderRouter 直接调用。
 *
 * 写入前统一脱敏（token/cookie/password 等参数值会被替换为 ***）。
 */
object Diag {

    const val INFO = 0
    const val WARN = 1
    const val ERROR = 2

    class Entry(
        val time: Long,
        val level: Int,
        val tag: String,
        val message: String,
        val stack: String
    )

    private const val MAX = 1000
    private const val LOG_TAG = "XiaoheDiag"

    private val lock = Any()
    private val list = ArrayList<Entry>()

    @Volatile
    private var ver = 0

    private val clockFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun version(): Int = ver

    fun snapshot(): List<Entry> = synchronized(lock) { ArrayList(list) }

    fun count(): Int = synchronized(lock) { list.size }

    @JvmStatic
    fun log(tag: String, message: String) = add(INFO, tag, message, "")

    @JvmStatic
    fun warn(tag: String, message: String) = add(WARN, tag, message, "")

    @JvmStatic
    fun error(tag: String, message: String) = add(ERROR, tag, message, "")

    @JvmStatic
    fun error(tag: String, message: String, cause: Throwable?) = add(ERROR, tag, message, cause?.let { stackOf(it) } ?: "")

    @JvmStatic
    fun clear() {
        synchronized(lock) {
            list.clear()
            ver++
        }
    }

    fun levelName(level: Int): String = when (level) {
        WARN -> "警告"
        ERROR -> "错误"
        else -> "信息"
    }

    fun timeOf(entry: Entry): String = clockFormat.format(Date(entry.time))

    /** 完整日志文本（复制/导出用），按时间正序。 */
    @JvmStatic
    fun dump(): String {
        val sb = StringBuilder()
        sb.append("Xiaohe 运行日志 ")
        sb.append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        sb.append('\n')
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        sb.append(" / Android ").append(Build.VERSION.RELEASE)
        sb.append(" (API ").append(Build.VERSION.SDK_INT).append(')').append('\n')
        sb.append("------------------------------\n")
        val copy = synchronized(lock) { ArrayList(list) }
        copy.reversed().forEach { e ->
            sb.append(clockFormat.format(Date(e.time)))
                .append(" [").append(levelName(e.level)).append("] ")
                .append(e.tag).append(": ").append(e.message).append('\n')
            if (e.stack.isNotEmpty()) sb.append(e.stack).append('\n')
        }
        return sb.toString()
    }

    /** 导出为文本文件（外部私有目录，用文件管理器/分享即可取出）。 */
    @JvmStatic
    fun export(): File? = runCatching {
        val context = Core.context()
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "xiaohe-log-" + System.currentTimeMillis() + ".txt")
        file.writeText(dump(), Charsets.UTF_8)
        file
    }.getOrNull()

    /** 记录未捕获崩溃，避免只剩一句笼统的"加载失败"。 */
    @JvmStatic
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                error("Crash", "未捕获异常（线程 ${thread.name}）：${throwable.javaClass.name}：${throwable.message}", throwable)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun add(level: Int, tag: String, message: String, stack: String) {
        val safe = redact(message)
        runCatching { Log.println(if (level == ERROR) Log.ERROR else Log.DEBUG, LOG_TAG, "[$tag] $safe") }
        synchronized(lock) {
            list.add(0, Entry(System.currentTimeMillis(), level, tag, safe, stack))
            while (list.size > MAX) list.removeAt(list.size - 1)
            ver++
        }
    }

    private fun stackOf(cause: Throwable): String = runCatching {
        val writer = StringWriter()
        cause.printStackTrace(PrintWriter(writer))
        redact(writer.toString())
    }.getOrDefault("")

    /** 脱敏：token/cookie/password 等参数值。 */
    private fun redact(text: String): String {
        if (text.isEmpty()) return text
        return text.replace(
            Regex("(?i)\\b(token|key|pwd|password|cookie|authorization|auth|secret|sign)=([^&\\s\"',]+)"),
            "$1=***"
        )
    }

    /** 日志用的安全 URL（去掉 query，避免把令牌写进日志）。 */
    @JvmStatic
    fun safeUrl(url: String): String {
        if (url.isEmpty()) return url
        val cut = url.indexOf('?')
        return if (cut < 0) url else url.substring(0, cut) + "?…"
    }
}
