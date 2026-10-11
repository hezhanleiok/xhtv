package com.xiaohetv.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaohetv.core.config.VodConfig
import com.xiaohetv.data.Store
import com.xiaohetv.player.Playback
import com.xiaohetv.ui.Route
import com.xiaohetv.ui.components.DividerItem
import com.xiaohetv.ui.components.IosRow
import com.xiaohetv.ui.components.IosSection
import com.xiaohetv.ui.components.IosTabBar
import com.xiaohetv.ui.components.LargeTitle
import com.xiaohetv.ui.theme.IosSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(nav: NavHostController) {
    val context = LocalContext.current
    Scaffold(
        topBar = { LargeTitle("我的") },
        bottomBar = {
            IosTabBar(2) { index ->
                when (index) {
                    0 -> nav.navigate(Route.Home)
                    1 -> Playback.liveEntry(context)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            IosSection("内容") {
                IosRow(icon = Icons.Default.History, title = "播放记录", value = "${Store.history.size}") { nav.navigate(Route.History) }
                DividerItem()
                IosRow(icon = Icons.Default.Star, title = "我的收藏", value = "${Store.collects.size}") { nav.navigate(Route.Collect) }
            }
            IosSection("站点") {
                IosRow(
                    title = "当前点播源",
                    value = VodConfig.config?.desc().orEmpty().ifEmpty { "未配置" }
                )
            }
            IosSection("应用") {
                IosRow(icon = Icons.Default.Settings, title = "设置") { nav.navigate(Route.Settings) }
            }
            Text(
                text = "Xiaohe影视 · 兼容 CatVod 配置与 Spider 协议\nApp 本身不内置任何内容来源",
                fontSize = 12.sp,
                color = IosSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
