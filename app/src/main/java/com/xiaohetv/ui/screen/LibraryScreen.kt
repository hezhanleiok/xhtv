package com.xiaohetv.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.xiaohetv.ui.Route
import com.xiaohetv.core.bean.Vod
import com.xiaohetv.core.config.VodConfig
import com.xiaohetv.data.Session
import com.xiaohetv.data.Store
import com.xiaohetv.ui.components.EmptyHint
import com.xiaohetv.ui.components.IosNavBar
import com.xiaohetv.ui.components.VodRow
import com.xiaohetv.vm.SiteVM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavHostController, sites: SiteVM) {
    val items = remember { mutableStateListOf<Store.History>().apply { addAll(Store.history) } }

    Scaffold(topBar = { IosNavBar("播放记录", onBack = { nav.popBackStack() }) }) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) { EmptyHint("还没有播放记录") }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(items, key = { it.siteKey + it.vodId }) { item ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        VodRow(
                            vod = Vod().apply {
                                vodId = item.vodId
                                vodName = item.name
                                vodPic = item.pic
                                vodRemarks = item.remark
                            },
                            extra = "上次看到 ${formatTime(item.position)}"
                        ) {
                            val site = VodConfig.getSite(item.siteKey) ?: return@VodRow
                            sites.select(site)
                            Session.site = site
                            Session.vod = Vod().apply {
                                vodId = item.vodId
                                vodName = item.name
                                vodPic = item.pic
                                vodRemarks = item.remark
                            }
                            Session.flagIndex = 0
                            Session.episodeIndex = 0
                            nav.navigate(Route.Detail)
                        }
                    }
                    IconButton(onClick = {
                        Store.removeHistory(item)
                        items.remove(item)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectScreen(nav: NavHostController, sites: SiteVM) {
    val items = remember { mutableStateListOf<Store.Collect>().apply { addAll(Store.collects) } }

    Scaffold(topBar = { IosNavBar("我的收藏", onBack = { nav.popBackStack() }) }) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) { EmptyHint("还没有收藏") }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(items, key = { it.siteKey + it.vodId }) { item ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        VodRow(
                            vod = Vod().apply {
                                vodId = item.vodId
                                vodName = item.name
                                vodPic = item.pic
                                vodRemarks = item.remark
                            }
                        ) {
                            val site = VodConfig.getSite(item.siteKey) ?: return@VodRow
                            sites.select(site)
                            Session.site = site
                            Session.vod = Vod().apply {
                                vodId = item.vodId
                                vodName = item.name
                                vodPic = item.pic
                                vodRemarks = item.remark
                            }
                            Session.flagIndex = 0
                            Session.episodeIndex = 0
                            nav.navigate(Route.Detail)
                        }
                    }
                    IconButton(onClick = {
                        Store.removeCollect(item)
                        items.remove(item)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFFF3B30))
                    }
                }
            }
        }
    }
}

fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val total = millis / 1000
    val hour = total / 3600
    val minute = (total % 3600) / 60
    val second = total % 60
    return if (hour > 0) String.format("%02d:%02d:%02d", hour, minute, second)
    else String.format("%02d:%02d", minute, second)
}
