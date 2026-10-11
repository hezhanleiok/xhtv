package com.xiaohetv

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xiaohetv.data.Session
import com.xiaohetv.ui.screen.PlayerScreen
import com.xiaohetv.ui.theme.XiaoheTheme

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 直播 / 点播都默认横屏：进入播放页即自动旋转为横屏（仍跟随重力感应左右翻转）
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // 沉浸式全屏：播放时隐藏状态栏/导航栏，避免系统图标压到画面上
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            XiaoheTheme {
                PlayerScreen { finish() }
            }
        }
    }

    /**
     * 已在栈顶时再次启动（详情页「全屏」按钮/双击）不会重建 Activity，
     * 必须自己处理新 intent，否则表现就是"点击全屏没反应"。
     * Session 已被调用方写好，这里重建一次即可按新的影片/线路/集数起播。
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}
