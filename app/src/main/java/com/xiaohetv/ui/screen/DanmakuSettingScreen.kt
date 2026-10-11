package com.xiaohetv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaohetv.core.config.VodConfig
import com.xiaohetv.core.utils.Json
import com.xiaohetv.data.Store
import com.xiaohetv.ui.components.IosNavBar
import com.xiaohetv.ui.components.IosSection
import com.xiaohetv.ui.theme.IosSecondary

@Composable
fun DanmakuSettingScreen(nav: NavHostController) {
    var version by remember { mutableIntStateOf(0) }
    var apiDialog by remember { mutableStateOf(false) }

    val configApi = remember(version) {
        runCatching { Json.str(Json.obj(VodConfig.config?.json), "danmaku") }.getOrDefault("")
    }
    val effectiveApi = Store.settings.danmakuApi.ifEmpty { configApi }

    Scaffold(topBar = { IosNavBar("弹幕设置", onBack = { nav.popBackStack() }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2F7))
                .padding(padding)
        ) {
            IosSection {
                SwitchRow(
                    title = "加载弹幕",
                    checked = Store.settings.danmakuLoad
                ) {
                    Store.settings.danmakuLoad = it
                    Store.saveSettings()
                    version++
                }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SettingLine("弹幕 API 地址", effectiveApi.ifEmpty { "未设置" }) { apiDialog = true }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SwitchRow(
                    title = "自动加载弹幕",
                    checked = Store.settings.danmakuAuto
                ) {
                    Store.settings.danmakuAuto = it
                    Store.saveSettings()
                    version++
                }
                Divider(color = Color(0xFFE5E5EA), thickness = 0.5.dp)
                SwitchRow(
                    title = "优先使用爬虫弹幕",
                    checked = Store.settings.danmakuSpider
                ) {
                    Store.settings.danmakuSpider = it
                    Store.saveSettings()
                    version++
                }
            }
            Text(
                text = "弹幕 API 留空时会自动使用点播配置里的 danmaku 字段（可使用 {name} 与 {episode} 占位符）。",
                fontSize = 12.sp,
                color = IosSecondary,
                modifier = Modifier.padding(20.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (apiDialog) {
        TextInputDialog(
            title = "弹幕 API 地址",
            value = Store.settings.danmakuApi,
            placeholder = "https://example.com/danmaku?name={name}&ep={episode}",
            onDismiss = { apiDialog = false },
            onConfirm = {
                apiDialog = false
                Store.settings.danmakuApi = it
                Store.saveSettings()
                version++
            }
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
