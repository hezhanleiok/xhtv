package com.xiaohetv.core.utils

import android.os.Handler
import android.os.Looper

object Tasks {

    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun <T> run(work: () -> T, done: (kotlin.Result<T>) -> Unit = {}) {
        Thread {
            val result = runCatching { work() }
            main.post { done(result) }
        }.start()
    }

    @JvmStatic
    fun post(action: () -> Unit) {
        main.post(action)
    }
}
