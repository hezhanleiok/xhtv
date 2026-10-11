package com.xiaohetv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaohetv.data.Store
import com.xiaohetv.ui.components.IosNavBar
import com.xiaohetv.ui.components.IosSection
import com.xiaohetv.ui.theme.IosSecondary

@Composable
fun PlayerSettingScreen(nav: NavHostController) {
    var version by remember { mutableIntStateOf(0) }
    var uaDialog by remember { mutableStateOf(false) }
    var scaleDialog by remember { mutableStateOf(false) }
    var backgroundDialog by remember { mutableStateOf(false) }
    var bufferDialog by remember { mutableStateOf(false) }
    var engineDialog by remember { mutableStateOf(false) }
    var decodeDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { IosNavBar("播放设置", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2F7))
                .padding(padding)
        ) {
            IosSection {
                SettingLine("播放引擎", Store.engineText()) { engineDialog = true }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SettingLine("解码方式", Store.decodeText()) { decodeDialog = true }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SettingLine("画面比例", Store.scaleText()) { scaleDialog = true }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SettingLine("后台播放", Store.backgroundText()) { backgroundDialog = true }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SettingLine("播放缓冲", "${Store.settings.buffer} 档") { bufferDialog = true }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SettingLine("播放 UA", Store.settings.ua.ifEmpty { "默认" }) { uaDialog = true }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SettingLine("播放倍速", "${Store.settings.speed}x") { speedDialog = true }
            }
            Text(
                text = "提示：mpv 播放核心与 ffmpeg 软解需要额外的扩展库（lib-*.aar），当前版本内置的是 Media3/ExoPlayer 硬解。",
                fontSize = 12.sp,
                color = IosSecondary,
                modifier = Modifier.padding(20.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (engineDialog) {
        SingleChoiceDialog(
            title = "播放引擎",
            items = listOf("EXO（内置）", "MPV（需要扩展库）"),
            selected = Store.settings.engine,
            onDismiss = { engineDialog = false },
            onPick = {
                engineDialog = false
                Store.settings.engine = it
                Store.saveSettings()
                version++
            }
        )
    }
    if (decodeDialog) {
        SingleChoiceDialog(
            title = "解码方式",
            items = listOf("软解（兼容优先）", "硬解（性能优先）"),
            selected = Store.settings.decode,
            onDismiss = { decodeDialog = false },
            onPick = {
                decodeDialog = false
                Store.settings.decode = it
                Store.saveSettings()
                version++
            }
        )
    }
    if (scaleDialog) {
        SingleChoiceDialog(
            title = "画面比例",
            items = listOf("默认", "16:9", "4:3", "填充", "裁剪"),
            selected = Store.settings.scale,
            onDismiss = { scaleDialog = false },
            onPick = {
                scaleDialog = false
                Store.settings.scale = it
                Store.saveSettings()
                version++
            }
        )
    }
    if (backgroundDialog) {
        SingleChoiceDialog(
            title = "后台播放",
            items = listOf("跟随系统", "始终后台", "从不后台"),
            selected = Store.settings.background,
            onDismiss = { backgroundDialog = false },
            onPick = {
                backgroundDialog = false
                Store.settings.background = it
                Store.saveSettings()
                version++
            }
        )
    }
    if (bufferDialog) {
        SingleChoiceDialog(
            title = "播放缓冲（档位越高越稳）",
            items = (1..10).map { "$it 档" },
            selected = (Store.settings.buffer - 1).coerceIn(0, 9),
            onDismiss = { bufferDialog = false },
            onPick = {
                bufferDialog = false
                Store.settings.buffer = it + 1
                Store.saveSettings()
                version++
            }
        )
    }
    if (speedDialog) {
        SingleChoiceDialog(
            title = "播放倍速",
            items = listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x", "3.0x"),
            selected = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f).indexOfFirst { it == Store.settings.speed }.coerceAtLeast(0),
            onDismiss = { speedDialog = false },
            onPick = {
                speedDialog = false
                Store.settings.speed = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)[it]
                Store.saveSettings()
                version++
            }
        )
    }
    if (uaDialog) {
        TextInputDialog(
            title = "播放 UA",
            value = Store.settings.ua,
            placeholder = "留空使用默认 Chrome UA",
            onDismiss = { uaDialog = false },
            onConfirm = {
                uaDialog = false
                Store.settings.ua = it
                Store.saveSettings()
                version++
            }
        )
    }
}

@Composable
fun SettingLine(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 13.sp,
            color = IosSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 6.dp)
        )
    }
}
