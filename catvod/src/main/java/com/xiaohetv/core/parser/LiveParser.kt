package com.xiaohetv.core.parser

import com.xiaohetv.core.bean.Channel
import com.xiaohetv.core.bean.Group
import com.xiaohetv.core.bean.Live
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Json

object LiveParser {

    private val M3U = Regex("^(?!.*#genre#).*#EXTM3U.*", RegexOption.MULTILINE)

    @JvmStatic
    @Throws(Exception::class)
    fun start(live: Live) {
        if (live.groups.isNotEmpty()) return
        val text = getText(live)
        if (Json.isArray(text)) json(live, text) else text(live, text)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getText(live: Live): String {
        if (live.api.isNotEmpty()) {
            val spider = com.xiaohetv.core.spider.SpiderRouter.spider(live.api, live.extString(), live.jar)
            return spider.liveContent(live.url)
        }
        val headers = LinkedHashMap<String, String>()
        if (live.ua.isNotEmpty()) headers["User-Agent"] = live.ua
        headers.putAll(live.headerMap())
        return OkHttp.string(live.url, headers)
    }

    @JvmStatic
    fun text(live: Live, data: String) {
        // 诊断：把"判定成什么格式"打出来，方便定位"为什么没有分组"
        com.xiaohetv.core.utils.Diag.log(
            "Live",
            "列表「${live.name}」${data.length} 字节，判定格式=" + if (M3U.containsMatchIn(data)) "M3U" else "TXT" +
                "，含 #genre#=${data.contains("#genre#")}，含 group-title=${data.contains("group-title")}"
        )
        if (M3U.containsMatchIn(data)) m3u(live, data) else txt(live, data)
        apply(live)
    }

    @JvmStatic
    fun json(live: Live, data: String) {
        val groups = runCatching {
            Json.gson.fromJson(data, Array<Group>::class.java).toList()
        }.getOrDefault(emptyList())
        com.xiaohetv.core.utils.Diag.log(
            "Live",
            "列表「${live.name}」${data.length} 字节，判定格式=JSON，groups=${groups.size}" +
                "，channel 总数=${groups.sumOf { it.channel.size }}"
        )
        live.groups = groups
        apply(live)
    }

    private fun m3u(live: Live, data: String) {
        val groups = LinkedHashMap<String, MutableList<Channel>>()
        var epg = ""
        var attrs = mutableMapOf<String, String>()
        var name = ""
        data.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXTM3U") -> {
                    epg = attr(line, "url-tvg") ?: attr(line, "x-tvg-url") ?: attr(line, "tvg-url").orEmpty()
                }
                line.startsWith("#EXTINF") -> {
                    attrs = extAttrs(line)
                    name = line.substringAfterLast(',', "").trim()
                }
                line.startsWith("#EXTVLCOPT:http-user-agent") -> attrs["ua"] = line.substringAfter('=').trim()
                line.startsWith("#EXTVLCOPT:http-referrer") -> attrs["referer"] = line.substringAfter('=').trim()
                line.startsWith("#EXTVLCOPT:http-origin") -> attrs["origin"] = line.substringAfter('=').trim()
                line.startsWith("#") -> Unit
                else -> {
                    // 分组名只取源里的 group-title（与参考项目一致）；
                    // 曾错误地用"直播源名"兜底，导致节目单里出现 Kim/范明/虎牙 这类源名当分组
                    // 分组名：group-title 为主，兼容 group / tvg-group 两种写法
                    val groupName = (attrs["group-title"] ?: attrs["group"] ?: attrs["tvg-group"])
                        .orEmpty().ifEmpty { "未分组" }
                    val channel = Channel()
                    channel.name = name.ifEmpty { attrs["tvg-name"].orEmpty() }
                    // 分组名直接落在频道上（实测 Group.name 写入后会变空，UI 必须按 channel.group 归组）
                    channel.group = groupName
                    channel.urls = listOf(line.substringBefore('|'))
                    channel.tvgId = attrs["tvg-id"].orEmpty()
                    channel.tvgName = attrs["tvg-name"].orEmpty()
                    channel.logo = attrs["tvg-logo"].orEmpty()
                    channel.number = attrs["tvg-chno"].orEmpty()
                    channel.ua = attrs["ua"].orEmpty()
                    channel.referer = attrs["referer"].orEmpty()
                    channel.origin = attrs["origin"].orEmpty()
                    channel.epg = attrs["tvg-url"].orEmpty().ifEmpty { epg }
                    groups.getOrPut(groupName) { mutableListOf() }.add(channel)
                    attrs = mutableMapOf()
                    name = ""
                }
            }
        }
        if (epg.isNotEmpty() && live.epg.isEmpty()) live.epg = epg
        // 诊断：直接把解析出来的原始分组 key 打出来（区分"没取到名字"还是"赋值丢了"）
        com.xiaohetv.core.utils.Diag.log(
            "Live",
            "M3U 原始分组 key(" + groups.size + ")=" + groups.keys.take(10).joinToString(" | ") { "[" + it + "]" }
        )
        live.groups = groups.map { (k, v) -> Group().apply { name = k; channel = v } }
        com.xiaohetv.core.utils.Diag.log(
            "Live",
            "M3U 写入 Group 后名字=" + live.groups.take(6).joinToString(" | ") { "[" + it.name + "]" }
        )
    }

    private fun txt(live: Live, data: String) {
        val groups = LinkedHashMap<String, MutableList<Channel>>()
        var groupName = "未分组"
        val setting = mutableMapOf<String, String>()
        data.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line.contains("#genre#")) {
                groupName = line.substringBefore("#genre#").trim().ifEmpty { "未分组" }
                return@forEach
            }
            if (isSetting(line)) {
                val index = line.indexOf('=')
                setting[line.substring(0, index).trim()] = line.substring(index + 1).trim()
                return@forEach
            }
            if (!line.contains(",")) return@forEach
            val split = line.split(",")
            val name = split[0].trim()
            val urls = split.getOrElse(1) { "" }.split("#").map { it.trim() }.filter { it.isNotEmpty() }
            if (urls.isEmpty()) return@forEach
            val channel = Channel()
            channel.name = name
            channel.group = groupName
            channel.urls = urls
            channel.ua = setting["ua"].orEmpty()
            channel.referer = setting["referer"].orEmpty()
            channel.origin = setting["origin"].orEmpty()
            channel.click = setting["click"].orEmpty()
            channel.format = setting["format"].orEmpty()
            channel.parse = setting["parse"]?.toIntOrNull() ?: 0
            groups.getOrPut(groupName) { mutableListOf() }.add(channel)
        }
        if (live.epg.isEmpty()) live.epg = setting["epg"].orEmpty()
        if (live.ua.isEmpty()) live.ua = setting["ua"].orEmpty()
        if (live.referer.isEmpty()) live.referer = setting["referer"].orEmpty()
        if (live.origin.isEmpty()) live.origin = setting["origin"].orEmpty()
        live.groups = groups.map { (k, v) -> Group().apply { name = k; channel = v } }
        com.xiaohetv.core.utils.Diag.log(
            "Live",
            "TXT 写入 Group 后名字=" + live.groups.take(6).joinToString(" | ") { "[" + it.name + "]" }
        )
    }

    private fun isSetting(line: String): Boolean {
        val key = line.substringBefore('=').trim()
        return key in setOf("ua", "parse", "click", "header", "format", "origin", "referer", "epg", "forceKey")
    }

    private fun extAttrs(line: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        // 双引号形式（标准）
        Regex("([a-zA-Z0-9\\-_]+)=\"([^\"]*)\"").findAll(line).forEach { match ->
            map[match.groupValues[1]] = match.groupValues[2]
        }
        // 单引号形式（部分源在用）
        Regex("([a-zA-Z0-9\\-_]+)='([^']*)'").findAll(line).forEach { match ->
            map.putIfAbsent(match.groupValues[1], match.groupValues[2])
        }
        // 无引号形式：group-title=央视（到空格/逗号/引号为止）
        Regex("([a-zA-Z0-9\\-_]+)=([^\"'\\s,]+)").findAll(line).forEach { match ->
            map.putIfAbsent(match.groupValues[1], match.groupValues[2])
        }
        return map
    }

    private fun attr(line: String, key: String): String? =
        Regex("$key=\"([^\"]*)\"").find(line)?.groupValues?.get(1)

    private fun apply(live: Live) {
        // 诊断：解析完把分组结果打出来（分组名 + 每组频道数），下次日志能直接看出分组有没有解析到
        com.xiaohetv.core.utils.Diag.log(
            "Live",
            "解析「${live.name}」结果：分组 ${live.groups.size} 个 → " +
                live.groups.take(10).joinToString(" | ") { "${it.name}(${it.channel.size})" }
        )
        var number = 0
        live.groups.forEach { group ->
            group.channel.forEach { channel ->
                if (channel.number.isEmpty()) channel.number = (++number).toString()
                if (channel.ua.isEmpty()) channel.ua = live.ua
                if (channel.referer.isEmpty()) channel.referer = live.referer
                if (channel.origin.isEmpty()) channel.origin = live.origin
                if (channel.epg.isEmpty()) channel.epg = live.epg
                if (channel.logo.isEmpty()) channel.logo = channel.logoOf(live.logo)
                if (channel.click.isEmpty()) channel.click = live.click
                // 解析阶段已经写好的分组名不要被空的 Group.name 覆盖
                if (channel.group.isEmpty()) channel.group = group.name
            }
        }
        // 源里完全没有分组信息时（只有一个"未分组"），按频道名做一次分组兜底：
        // 这样央视/卫视/4K8K/春晚 这类列表也能像参考项目那样看到分组，而不是一坨"未分组"。
        if (live.groups.size == 1 && live.groups[0].name == "未分组" && live.groups[0].channel.size > 1) {
            val regroup = LinkedHashMap<String, MutableList<Channel>>()
            live.groups[0].channel.forEach { channel ->
                regroup.getOrPut(autoGroup(channel)) { mutableListOf() }.add(channel)
            }
            live.groups = regroup.map { (key, value) ->
                Group().apply {
                    name = key
                    channel = value
                }.also { group -> group.channel.forEach { it.group = group.name } }
            }
        }
    }

    /** 只在源没有分组信息时使用：按频道名归类（央视/卫视/4K8K/春晚/港澳台/轮播…）。 */
    private fun autoGroup(channel: Channel): String {
        val name = channel.name.uppercase()
        return when {
            name.contains("CCTV") || name.contains("央视") -> "央视"
            name.contains("卫视") -> "卫视"
            name.contains("4K") || name.contains("8K") -> "4K8K频道"
            name.contains("春晚") -> "历年春晚"
            name.contains("轮播") -> "轮播"
            name.contains("港澳") || name.contains("香港") || name.contains("凤凰") ||
                name.contains("翡翠") || name.contains("东森") -> "港澳台"
            else -> "其它频道"
        }
    }
}
