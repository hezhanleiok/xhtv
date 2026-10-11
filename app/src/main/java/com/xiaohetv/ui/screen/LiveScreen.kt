package com.xiaohetv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.xiaohetv.core.bean.Channel
import com.xiaohetv.core.bean.Live
import com.xiaohetv.player.Playback
import com.xiaohetv.ui.Route

/**
 * 直播入口：**直接打开播放器（默认横屏）**，由播放页自己加载直播列表并播第一个频道。
 *
 * 为什么不用"主界面转横屏 + 黑底加载页"：那样会先出现一个竖屏加载环节，再旋转到横屏，
 * 用户看到"竖屏 → 横屏"的多余过程。直接进 PlayerActivity 则全程横屏。
 */
@Composable
fun LiveEntryScreen(nav: NavHostController) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Playback.liveEntry(context)
        nav.popBackStack()
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
}

fun openChannel(context: android.content.Context, live: Live, channel: Channel) {
    Playback.live(context, live, channel)
}
