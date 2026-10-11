package com.xiaohetv.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.ui.components.Chip
import com.xiaohetv.ui.components.IosNavBar
import com.xiaohetv.ui.theme.IosBlue
import com.xiaohetv.ui.theme.IosSecondary
import kotlinx.coroutines.delay

/**
 * 临时「运行日志」页面：实时显示埋点日志，支持 级别筛选 / 复制 / 导出 / 清空。
 * 排查完成后可整体删除（Route.Log + 入口 + 此文件）。
 */
@Composable
fun LogScreen(nav: NavHostController) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(Diag.snapshot()) }
    var filter by remember { mutableIntStateOf(-1) }

    // 每 400ms 拉一次（catvod 模块不引协程 StateFlow，故用轮询）
    LaunchedEffect(Unit) {
        var last = -1
        while (true) {
            val v = Diag.version()
            if (v != last) {
                last = v
                entries = Diag.snapshot()
            }
            delay(400)
        }
    }

    val shown = remember(entries, filter) {
        if (filter < 0) entries else entries.filter { it.level == filter }
    }

    Scaffold(topBar = { IosNavBar(title = "运行日志", onBack = { nav.popBackStack() }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "开发调试版 · 仅用于排查问题（已自动脱敏 token/cookie）",
                fontSize = 11.sp,
                color = IosSecondary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Chip(text = "全部", selected = filter == -1, onClick = { filter = -1 })
                Chip(text = "信息", selected = filter == Diag.INFO, onClick = { filter = Diag.INFO })
                Chip(text = "警告", selected = filter == Diag.WARN, onClick = { filter = Diag.WARN })
                Chip(text = "错误", selected = filter == Diag.ERROR, onClick = { filter = Diag.ERROR })
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "${shown.size} 条", fontSize = 11.sp, color = IosSecondary)
            }

            if (shown.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = "暂无日志", fontSize = 13.sp, color = IosSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(shown) { entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = Diag.timeOf(entry),
                                    fontSize = 11.sp,
                                    color = IosSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(80.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colorOf(entry.level).copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(text = Diag.levelName(entry.level), fontSize = 10.sp, color = colorOf(entry.level))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = entry.tag, fontSize = 11.sp, color = IosBlue)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = entry.message, fontSize = 12.sp, color = Color(0xFF2C2C2E))
                            if (entry.stack.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = entry.stack,
                                    fontSize = 10.sp,
                                    color = Color(0xFF8A8A8E),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LogButton("复制日志") {
                    copyToClipboard(context, Diag.dump())
                    Toast.makeText(context, "已复制 ${Diag.count()} 条日志", Toast.LENGTH_SHORT).show()
                }
                LogButton("导出文件") {
                    val file = Diag.export()
                    if (file == null) {
                        Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "已导出：${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                }
                LogButton("清空") {
                    Diag.clear()
                    Toast.makeText(context, "已清空", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private fun colorOf(level: Int): Color = when (level) {
    Diag.WARN -> Color(0xFFFF9500)
    Diag.ERROR -> Color(0xFFFF3B30)
    else -> Color(0xFF007AFF)
}

@Composable
private fun RowScope.LogButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF2F2F7))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 13.sp, color = Color(0xFF2C2C2E))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("xiaohe-log", text))
    }
}
