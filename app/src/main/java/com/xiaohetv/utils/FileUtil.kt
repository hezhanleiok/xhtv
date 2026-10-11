package com.xiaohetv.utils

import android.content.Context
import java.io.File
import java.text.DecimalFormat

object FileUtil {

    @JvmStatic
    fun size(file: File): Long {
        if (!file.exists()) return 0
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { size(it) } ?: 0
    }

    @JvmStatic
    fun readable(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digit = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digit.toDouble())) + " " + units[digit]
    }

    @JvmStatic
    fun cacheSize(context: Context): String = readable(size(context.cacheDir))

    @JvmStatic
    fun clearCache(context: Context) {
        context.cacheDir.listFiles()?.forEach { clear(it) }
    }

    @JvmStatic
    fun clear(file: File) {
        runCatching {
            if (file.isDirectory) file.listFiles()?.forEach { clear(it) }
            file.delete()
        }
    }

    @JvmStatic
    fun files(context: Context, name: String): File = File(context.filesDir, name)
}
