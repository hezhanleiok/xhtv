package com.xiaohetv.core.utils

import android.content.Context
import java.io.File

object Path {

    private var root: File? = null

    @JvmStatic
    fun init(context: Context) {
        root = context.filesDir
    }

    private fun root(): File = root ?: File("")

    @JvmStatic
    fun cache(): File = File(root(), "cache").apply { mkdirs() }

    @JvmStatic
    fun jar(): File = File(cache(), "jar").apply { mkdirs() }

    @JvmStatic
    fun js(): File = File(cache(), "js").apply { mkdirs() }

    @JvmStatic
    fun epg(): File = File(cache(), "epg").apply { mkdirs() }

    @JvmStatic
    fun jar(name: String): File = File(jar(), "${Util.md5(name)}.jar")

    @JvmStatic
    fun js(name: String): File = File(js(), "${Util.md5(name)}.js")
}
