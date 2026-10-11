package com.xiaohetv.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaohetv.core.Core
import com.xiaohetv.core.bean.Config
import com.xiaohetv.core.config.ConfigStore
import com.xiaohetv.ui.components.Chip
import com.xiaohetv.ui.theme.IosSecondary
import java.io.File

/** 输入 / 编辑订阅 */
@Composable
fun ConfigInputDialog(
    title: String,
    config: Config?,
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String) -> Unit
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(config?.url.orEmpty()) }
    var name by remember { mutableStateOf(config?.name.orEmpty()) }
    var picked by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (text.isBlank()) throw Exception("文件内容为空")
            val file = File(Core.context().filesDir, "configs/${System.currentTimeMillis()}.json")
            file.parentFile?.mkdirs()
            file.writeText(text)
            url = "file://${file.absolutePath}"
            picked = uri.lastPathSegment?.substringAfterLast('/') ?: "本地文件"
        }.onFailure { picked = "读取失败：${it.message}" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("订阅地址，如 http://xxx/tv 或 ./vod.json", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("名称（可留空）", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(text = "选择本地文件", selected = false, onClick = { picker.launch(arrayOf("*/*")) })
                    if (picked.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = picked, fontSize = 11.sp, color = IosSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "支持：普通配置 / 配置仓库(urls) / 纯 M3U·TXT 直播文本；点播配置里的 lives 会自动识别为直播。地址不写 http:// 也会自动补全。",
                    fontSize = 11.sp,
                    color = IosSecondary
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(url.trim(), name.trim()) }) { Text("保存并加载") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 已保存的订阅列表：切换 / 编辑 / 删除 */
@Composable
fun ConfigListDialog(
    type: Int,
    onDismiss: () -> Unit,
    onSelect: (Config) -> Unit,
    onEdit: (Config) -> Unit,
    onDelete: (Config) -> Unit
) {
    var version by remember { mutableStateOf(0) }
    val list = remember(version) { ConfigStore.all(type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == Config.VOD) "点播订阅" else if (type == Config.LIVE) "直播订阅" else "壁纸订阅") },
        text = {
            if (list.isEmpty()) {
                Text("还没有保存的订阅", fontSize = 13.sp, color = IosSecondary)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(list) { config ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelect(config) }
                            ) {
                                Text(
                                    text = config.name.ifEmpty { config.url },
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = config.url, fontSize = 11.sp, color = IosSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Chip(text = "编辑", selected = false, onClick = { onEdit(config) })
                            IconButton(onClick = { onDelete(config); version++ }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 单选对话框（图片尺寸 / 主题 / DoH / 备份恢复等通用） */
@Composable
fun SingleChoiceDialog(
    title: String,
    items: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = item, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (index == selected) Text(text = "✓", color = Color(0xFF007AFF), fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 恢复对话框：列出备份文件 */
@Composable
fun RestoreDialog(
    files: List<File>,
    onDismiss: () -> Unit,
    onPick: (File) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择备份") },
        text = {
            if (files.isEmpty()) {
                Text("还没有备份文件", fontSize = 13.sp, color = IosSecondary)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(files) { file ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(file) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(text = file.name, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 通用：主页选择（站点 / 直播线路） */
@Composable
fun HomePickDialog(
    title: String,
    items: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(items.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = items[index], fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (items[index] == selected) Text(text = "✓", color = Color(0xFF007AFF))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 通用文本输入（弹幕 API、播放 UA、输入网址播放等） */
@Composable
fun TextInputDialog(
    title: String,
    value: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    extraLabel: String = "",
    onExtra: (() -> Unit)? = null
) {
    var text by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(placeholder, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (extraLabel.isNotEmpty() && onExtra != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Chip(text = extraLabel, selected = false, onClick = onExtra)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 磁力/多文件选集对话框 */
@Composable
fun MultiPickDialog(
    title: String,
    items: List<String>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(items.size) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(text = items[index], fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun EmptyBoxIcon() {
    Box(modifier = Modifier.size(1.dp))
}
