package com.xiaohetv.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohetv.core.bean.Channel
import com.xiaohetv.core.bean.Group
import com.xiaohetv.core.bean.Live
import com.xiaohetv.core.config.LiveConfig
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.parser.EpgParser
import com.xiaohetv.core.parser.LiveParser
import com.xiaohetv.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveVM : ViewModel() {

    val lives = MutableStateFlow<List<Live>>(emptyList())
    val groups = MutableStateFlow<List<Group>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow("")
    val epg = MutableStateFlow<Map<String, List<EpgParser.Programme>>>(emptyMap())

    var current: Live? = null

    fun refresh() {
        lives.value = LiveConfig.lives.toList()
        current = LiveConfig.home ?: lives.value.firstOrNull()
    }

    /**
     * 解析并把所有源（lives）的频道按「分组名 + 频道名」合并：
     * 同名频道（如 CCTV1）只保留一条，其 urls 累加多个源，播放时自动切换。
     * 合并结果写回 Session.live.groups，直播页与播放器换台面板共用同一份数据。
     */
    fun open() {
        val all = (lives.value.ifEmpty { LiveConfig.lives.toList() })
        if (all.isEmpty()) {
            groups.value = emptyList()
            error.value = "没有直播源，请到设置添加直播配置"
            return
        }
        val home = LiveConfig.home ?: all.first()
        current = home
        viewModelScope.launch(Dispatchers.IO) {
            loading.value = true
            error.value = ""
            val outcome = runCatching { mergeGroups(all) }
            val merged = outcome.getOrDefault(emptyList())
            home.groups = merged
            Session.live = home
            withContext(Dispatchers.Main) {
                loading.value = false
                groups.value = merged
                if (merged.isEmpty()) error.value = outcome.exceptionOrNull()?.message ?: "频道列表为空"
            }
            loadEpg(home)
        }
    }

    /** 选中单个源（线路）查看，不做合并。 */
    fun open(live: Live) {
        current = live
        viewModelScope.launch(Dispatchers.IO) {
            loading.value = true
            error.value = ""
            val outcome = runCatching { LiveParser.start(live) }
            val list = live.groups
            withContext(Dispatchers.Main) {
                loading.value = false
                groups.value = list
                if (list.isEmpty()) error.value = outcome.exceptionOrNull()?.message ?: "频道列表为空"
            }
            Session.live = live
            loadEpg(live)
        }
    }

    private fun mergeGroups(lives: List<Live>): List<Group> {
        val byKey = LinkedHashMap<String, Channel>()
        val result = LinkedHashMap<String, MutableList<Channel>>()
        for (live in lives) {
            runCatching { LiveParser.start(live) }
            for (group in live.groups) {
                val gname = (group.channel.firstOrNull()?.group ?: "").ifEmpty { group.name }.ifEmpty { live.name }.ifEmpty { "未分组" }
                val list = result.getOrPut(gname) { mutableListOf() }
                for (ch in group.channel) {
                    val cname = (ch.name.ifEmpty { ch.number }).ifEmpty { "未知频道" }
                    val key = "$gname||$cname"
                    val existing = byKey[key]
                    if (existing != null) {
                        val add = ch.urls.filter { it !in existing.urls }
                        if (add.isNotEmpty()) existing.urls = existing.urls + add
                    } else {
                        byKey[key] = ch
                        list.add(ch)
                    }
                }
            }
        }
        // 频道号按「组内」顺序补零重排（001、002…），对齐参考项目 adapter_channel 的显示
        return result.map { (name, chs) ->
            chs.forEachIndexed { index, channel ->
                channel.number = String.format(java.util.Locale.US, "%03d", index + 1)
            }
            Group().apply { this.name = name; channel = chs }
        }
    }

    private suspend fun loadEpg(live: Live) {
        val url = live.epg
        if (url.isEmpty()) return
        val headers = LinkedHashMap<String, String>()
        if (live.ua.isNotEmpty()) headers["User-Agent"] = live.ua
        headers.putAll(live.headerMap())
        val map = runCatching { EpgParser.parse(OkHttp.string(url, headers)) }.getOrDefault(emptyMap())
        withContext(Dispatchers.Main) { epg.value = map }
    }

    fun program(channelId: String): EpgParser.Programme? {
        if (channelId.isEmpty()) return null
        val value = epg.value[channelId] ?: epg.value[channelId.substringBefore('.')]
        return EpgParser.current(value)
    }
}
