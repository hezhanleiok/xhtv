package com.xiaohetv.data

import android.content.Context
import com.xiaohetv.core.utils.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 模块管理（照参考项目 FongMi 的 Core 模型）：
 * 把"未随包内置的原生核心/运行时"下载到应用私有目录，并按类型加载：
 *
 *  · `.so`  → [System.load]（参考项目即如此：`core.so` 下载后直接加载）
 *  · `.jar` → 交给已有的 JarLoader/DexClassLoader 体系（这里只负责落盘）
 *
 * 说明：内置模块（EXO+FFmpeg、MPV、Thunder、JAR/JS spider、WebView 嗅探）不需要下载；
 * 该机制是为"参考项目里那几个需要下载的模块"留的真实入口 —— 填入模块地址即可安装。
 * 安全提示：只应填入可信来源的模块地址（加载原生库等同执行其代码）。
 */
object ModuleManager {

    private fun dir(context: Context): File = File(context.filesDir, "modules").apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), safeName(name))

    fun isInstalled(context: Context, name: String): Boolean =
        file(context, name).let { it.exists() && it.length() > 0 }

    fun installedSizeKb(context: Context, name: String): Long =
        runCatching { file(context, name).length() / 1024 }.getOrDefault(0L)

    /** 只保留安全字符，避免模块名被当成路径使用。 */
    private fun safeName(name: String): String =
        name.replace(Regex("""[^\w.\-]+"""), "_").ifEmpty { "module.bin" }

    /**
     * 下载并安装模块。成功后：`.so` 立即尝试加载（失败只记日志，不影响 App 其它功能）。
     */
    suspend fun install(context: Context, name: String, url: String): Result<File> {
        val link = url.trim()
        if (link.isEmpty()) return Result.failure(IllegalArgumentException("模块地址为空"))
        return runCatching {
            val out = file(context, name)
            withContext(Dispatchers.IO) {
                java.net.URL(link).openStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            }
            Diag.log("Module", "模块已下载：$name → ${out.name}（${out.length() / 1024}KB）")
            if (out.name.endsWith(".so", true)) {
                runCatching { System.load(out.absolutePath) }
                    .onSuccess { Diag.log("Module", "模块已加载：${out.name}") }
                    .onFailure { Diag.warn("Module", "模块加载失败（已落盘，下次重启可重试）：${it.message}") }
            }
            out
        }.onFailure { Diag.error("Module", "模块下载失败：$name", it) }
    }

    fun remove(context: Context, name: String): Boolean =
        runCatching { file(context, name).delete() }.getOrDefault(false)
}
