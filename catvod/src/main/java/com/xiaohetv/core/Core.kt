package com.xiaohetv.core

import android.content.Context
import com.xiaohetv.core.utils.Path

object Core {

    private var appContext: Context? = null

    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
        Path.init(context)
    }

    @JvmStatic
    fun context(): Context = appContext ?: throw IllegalStateException("Core.init() not called")
}
