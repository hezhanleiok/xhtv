package com.xiaohetv.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohetv.core.bean.Result
import com.xiaohetv.core.bean.Vod
import com.xiaohetv.core.config.LiveConfig
import com.xiaohetv.core.config.VodConfig
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.spider.SpiderRouter
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.core.utils.VodTrace
import com.xiaohetv.data.Session
import com.xiaohetv.player.Thunder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder

class PlayerVM : ViewModel() {

    data class PlayInfo(
        val url: String = "",
        val headers: Map<String, String> = emptyMap(),
        val format: String = "",
        val title: String = "",
        val episode: String = ""
    )

    val info = MutableStateFlow<PlayInfo?>(null)
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow("")

    /** 每次切换集数 / 重新解析都调用 start()。 */
    fun start() {
        viewModelScope.launch(Dispatchers.IO) {
            loading.value = true
            error.value = ""
            // ---- 阶段 1：播放入口（生成 playbackId，后续所有日志都带上它）----
            val vod = Session.vod
            val flag = vod?.flags()?.getOrNull(Session.flagIndex)
            val episode = flag?.episodes?.getOrNull(Session.episodeIndex)
            val scene = when {
                Session.directUrl.isNotEmpty() -> "direct"
                vod != null -> "vod"
                Session.live != null -> "live"
                else -> "unknown"
            }
            val entry = if (vod != null) {
                "movie=${vod.vodName} episode=${episode?.name.orEmpty()} site=${Session.site?.name.orEmpty()}" +
                    " line=${flag?.flag.orEmpty()} lineIndex=${Session.flagIndex} epIndex=${Session.episodeIndex}" +
                    " player=exoplayer"
            } else if (scene == "live") {
                "channel=${Session.channel?.name.orEmpty()} player=exoplayer"
            } else "direct=${VodTrace.mask(Session.directUrl)} player=exoplayer"
            VodTrace.begin(scene, entry)

            val at = System.currentTimeMillis()
            val outcome = runCatching { build() }
            withContext(Dispatchers.Main) {
                loading.value = false
                val value = outcome.getOrNull()
                if (value == null) {
                    val t = outcome.exceptionOrNull()
                    error.value = t?.message ?: "解析播放地址失败"
                    // 定位到这里：失败在"取地址/解析"阶段，原始异常链与堆栈都进日志
                    VodTrace.fail("entry", t, System.currentTimeMillis() - at)
                } else {
                    info.value = value
                    VodTrace.stage(
                        "play_info", "ok", System.currentTimeMillis() - at,
                        extra = "kind=${VodTrace.kindOf(value.url)} url=${VodTrace.mask(value.url)}" +
                            " format=${value.format.ifEmpty { "-" }} headers=${VodTrace.headerNames(value.headers)}"
                    )
                }
            }
        }
    }

    private suspend fun build(): PlayInfo {
        // 点播优先：看完直播后 Session.live / Session.channel 仍然残留，
        // 若先判直播，点播就会播放出直播内容（用户反馈"点播影片播放会错误播放直播内容"）。
        val item = Session.vod
        if (item != null) return buildVod(item)
        return buildOther()
    }

    /** 直播 / 直链 / 磁力 等非点播内容。 */
    private fun buildOther(): PlayInfo {
        // 1) 直播
        val live = Session.live
        val channel = Session.channel
        if (live != null && channel != null) {
            val headers = LinkedHashMap<String, String>()
            if (channel.ua.isNotEmpty()) headers["User-Agent"] = channel.ua
            if (channel.referer.isNotEmpty()) headers["Referer"] = channel.referer
            if (channel.origin.isNotEmpty()) headers["Origin"] = channel.origin
            channel.headerMap().forEach { (k, v) -> if (v.isNotEmpty()) headers[k] = v }
            if (headers.isEmpty()) headers.putAll(live.headerMap())
            return PlayInfo(channel.url(), headers, channel.format, channel.name, "")
        }

        // 2) 直接输入网址 / 磁力
        if (Session.directUrl.isNotEmpty()) {
            val raw = Session.directUrl
            val url = if (Thunder.isTorrentPlayUrl(raw)) Thunder.fetch(raw) else raw
            return PlayInfo(url, Session.directHeaders, "", Session.directTitle.ifEmpty { "网络播放" }, "")
        }

        throw Exception("没有可播放的内容")
    }

    /** 点播：把当前线路 / 集数解析成可播放地址。 */
    private suspend fun buildVod(vod: Vod): PlayInfo {
        val site = Session.site ?: throw Exception("没有选择站点")
        val flag = vod.flags().getOrNull(Session.flagIndex) ?: throw Exception("没有播放源")
        val episode = flag.episodes.getOrNull(Session.episodeIndex) ?: throw Exception("没有选集")
        val spider = SpiderRouter.spider(site)
        if (spider is com.github.catvod.crawler.SpiderNull) {
            VodTrace.stage(
                "spider", "failed", errorType = "SpiderNull",
                errorMessage = "站点 spider 未加载成功（Guard 未通过）",
                extra = "site=${site.name} api=${site.api} lastError=${SpiderRouter.lastError}"
            )
            throw Exception("站点「${site.name}」的 spider 没加载成功（Guard 未通过），换个线路/站源再试")
        }
        // ---- 阶段 2：播放地址获取（playerContent）----
        val at = System.currentTimeMillis()
        val raw = try {
            spider.playerContent(flag.flag, episode.url, VodConfig.flags)
        } catch (e: Throwable) {
            VodTrace.fail(
                "player_content", e, System.currentTimeMillis() - at,
                extra = "site=${site.name} line=${flag.flag} sourceKind=${VodTrace.kindOf(episode.url)}" +
                    " source=${VodTrace.mask(episode.url)}"
            )
            throw e
        }
        val result = Result.fromJson(raw)
        val realUrl = result.realUrl()
        val headers = result.header().ifEmpty { site.headerMap() }
        VodTrace.stage(
            "player_content", "ok", System.currentTimeMillis() - at,
            extra = "site=${site.name} len=${raw.length}B needParse=${result.needParse()}" +
                " format=${result.format.ifEmpty { "-" }} headers=${VodTrace.headerNames(headers)}" +
                " realUrlKind=${VodTrace.kindOf(realUrl)} realUrl=${VodTrace.mask(realUrl)}" +
                " playFlags=${VodConfig.flags.size} rawHead=${VodTrace.snippet(raw)}"
        )
        var url = realUrl.ifEmpty { episode.url }
        // ---- playUrl 前缀语义（配置字典）：json: = JSON 解析端点；parse: = 具名解析器 ----
        when (result.playUrlKind()) {
            1 -> {
                val at0 = System.currentTimeMillis()
                val text = runCatching {
                    OkHttp.string(result.playUrlValue() + URLEncoder.encode(url, "UTF-8"))
                }.getOrNull().orEmpty()
                val parsed = Result.fromJson(text).url()
                VodTrace.stage(
                    "play_url", if (parsed.isEmpty()) "empty" else "ok", System.currentTimeMillis() - at0,
                    extra = "kind=json endpoint=${VodTrace.mask(result.playUrlValue())}" +
                        " source=${VodTrace.mask(url)} result=${VodTrace.mask(parsed)}"
                )
                if (parsed.isNotEmpty()) url = parsed
            }
            2 -> {
                val named = VodConfig.parses.firstOrNull { it.name == result.playUrlValue() }
                if (named != null) {
                    url = parseOne(named, url)
                } else {
                    VodTrace.stage("play_url", "miss", extra = "kind=parse 找不到名为 ${result.playUrlValue()} 的解析器")
                }
            }
            else -> {}
        }
        // ---- 阶段 3：地址解析（需要解析时）----
        if (result.needParse()) url = parse(url, flag.flag)
        if (url.isEmpty()) {
            VodTrace.stage("resolve", "failed", errorType = "EmptyUrl", errorMessage = "没有获取到播放地址", extra = "rawHead=${VodTrace.snippet(raw)}")
            throw Exception("没有获取到播放地址")
        }
        // ---- 阶段 4：定址（判断拿到的到底是什么类型的地址）----
        if (VodTrace.looksPlayable(url)) {
            VodTrace.stage("resolve", "ok", extra = "kind=${VodTrace.kindOf(url)} url=${VodTrace.mask(url)}")
        } else {
            // needParse=false 也可能返回非直接播放地址（网盘元数据/JSON/错误页）
            VodTrace.stage(
                "resolve", "suspect", errorType = "NotPlayableUrl",
                errorMessage = "得到的地址不像可直接播放的媒体地址",
                extra = "kind=${VodTrace.kindOf(url)} url=${VodTrace.mask(url)} rawHead=${VodTrace.snippet(raw)}"
            )
            // 内置 WebView 嗅探解析：地址不像媒体（网页解析型源）时，把页面真实加载一遍把播放地址嗅出来
            if (url.startsWith("http")) {
                val sniffAt = System.currentTimeMillis()
                val candidates = com.xiaohetv.player.Sniffer.sniffApp(url)
                if (candidates.isNotEmpty()) {
                    url = candidates.first()
                    VodTrace.stage(
                        "sniff", "ok", System.currentTimeMillis() - sniffAt,
                        extra = "count=${candidates.size} pickedKind=${VodTrace.kindOf(url)}" +
                            " picked=${VodTrace.mask(url)}"
                    )
                } else {
                    VodTrace.stage(
                        "sniff", "empty", System.currentTimeMillis() - sniffAt,
                        extra = "page=${VodTrace.mask(url)} 没嗅到媒体地址（继续用原地址尝试播放）"
                    )
                }
            }
        }
        return PlayInfo(url, headers, result.format, vod.vodName, episode.name)
    }

    /**
     * 地址解析（对齐影視TV 配置字典里的 `parse.type`）：
     *   0 = WebView（用内置嗅探加载解析页取流）
     *   1 = JSON API（请求接口取 url）
     *   2 = JAR JSON / 3 = JAR 混合（JAR 解析器同样以 HTTP JSON 端点形式暴露，按 JSON 处理）
     *   4 = 并行尝试（并发跑所有解析器，谁先给出结果用谁）
     * 解析器选择：优先 flag 匹配的，否则第一个。
     */
    private suspend fun parse(url: String, flag: String): String {
        val all = VodConfig.parses
        if (all.isEmpty()) {
            VodTrace.stage("parse", "skipped", extra = "没有可用解析器，跳过解析")
            return url
        }
        val ordered = all.sortedByDescending { if (it.flags().contains(flag)) 1 else 0 }
        val pick = ordered.first()
        if (pick.type == 4) return parseParallel(ordered, url)
        return parseOne(pick, url)
    }

    /** 单个解析器：type 0 → WebView 嗅探；其余 → JSON 接口。 */
    private suspend fun parseOne(parse: Any, url: String): String {
        val p = parse as com.xiaohetv.core.bean.Parse
        val at = System.currentTimeMillis()
        val target = p.url + URLEncoder.encode(url, "UTF-8")
        if (p.type == 0) {
            val list = com.xiaohetv.player.Sniffer.sniffApp(target)
            val picked = list.firstOrNull().orEmpty()
            VodTrace.stage(
                "parse", if (picked.isEmpty()) "empty" else "ok", System.currentTimeMillis() - at,
                extra = "type=0(WebView) parser=${p.name} parserUrl=${VodTrace.mask(p.url)}" +
                    " source=${VodTrace.mask(url)} count=${list.size} result=${VodTrace.mask(picked)}"
            )
            return picked.ifEmpty { url }
        }
        return runCatching {
            val text = OkHttp.string(target, p.headerMap())
            val parsed = Result.fromJson(text).url()
            VodTrace.stage(
                "parse", if (parsed.isEmpty()) "empty" else "ok", System.currentTimeMillis() - at,
                extra = "type=${p.type} parser=${p.name} parserUrl=${VodTrace.mask(p.url)}" +
                    " source=${VodTrace.mask(url)} resultKind=${VodTrace.kindOf(parsed)}" +
                    " result=${VodTrace.mask(parsed)} bodyHead=${VodTrace.snippet(text)}"
            )
            parsed.ifEmpty { url }
        }.getOrElse { t ->
            VodTrace.fail(
                "parse", t, System.currentTimeMillis() - at,
                extra = "type=${p.type} parser=${p.name} parserUrl=${VodTrace.mask(p.url)} source=${VodTrace.mask(url)}"
            )
            url
        }
    }

    /** type 4：并行尝试所有解析器，取第一个成功的结果（配置字典的"并行尝试"语义）。 */
    private suspend fun parseParallel(all: List<com.xiaohetv.core.bean.Parse>, url: String): String =
        coroutineScope {
            val at = System.currentTimeMillis()
            val jobs = all.map { p -> async(Dispatchers.IO) { runCatching { parseOne(p, url) }.getOrDefault("") } }
            val picked = withTimeoutOrNull(15000L) {
                var out = ""
                for (job in jobs) {
                    val value = runCatching { job.await() }.getOrDefault("")
                    if (value.isNotEmpty()) {
                        out = value
                        break
                    }
                }
                out
            }.orEmpty()
            jobs.forEach { it.cancel() }
            VodTrace.stage(
                "parse", if (picked.isEmpty()) "empty" else "ok", System.currentTimeMillis() - at,
                extra = "type=4(parallel) parsers=${all.size} result=${VodTrace.mask(picked)}"
            )
            picked.ifEmpty { url }
        }

    /**
     * 直播自动换源：当前频道有多条 url 时，切到下一个再重新解析播放。
     * 返回 false 表示已是最后一条源。
     */
    fun nextSource(): Boolean {
        val channel = Session.channel ?: return false
        // ① 先在本频道自己的多条源之间切换
        if (channel.urls.isNotEmpty() && channel.index < channel.urls.size - 1) {
            channel.index = channel.index + 1
            Diag.log("Live", "换源：${
                channel.name
            } → 本频道第 ${channel.index + 1}/${channel.urls.size} 条")
            start()
            return true
        }
        // ② 本频道所有源都失败 → 同名频道跨源换源（并行预探测后取最快源，异步执行）
        viewModelScope.launch { switchSameNameChannel() }
        return true
    }

    /**
     * 同名频道跨源换源：当前频道所有源都播不了时，
     * 在当前订阅的**其它直播源**里找同名频道切过去（参考项目「换源」的强项）。
     * 从当前源的下一个开始绕一圈找，避免刚失败又切回同一个源。
     */
    /** 已经失败过的直播源（本次播放页会话内），避免反复切回同一个源。 */
    private val failedLives = mutableSetOf<String>()

    /** 已经试过的点播站点（本次播放页会话内）。 */
    private val failedVodSites = mutableSetOf<String>()

    /**
     * 点播跨站点换源：当前站点放不了（例如站点返回“没有选集 / 没有播放源”，或播放器报错）时，
     * 去**其它站点**搜同名影片，切过去从第 1 线路第 1 集继续播。
     *
     * 这就是参考项目 / TVBox 的「换源」核心：一个影片往往只有部分站点有可用源，
     * 只在一棵树上吊死必然大量“播不了”。日志实测：`原创┃不卡` 的失败原因就是
     * `stage=entry failed errorMessage=没有选集`，属于典型该自动换源的情形。
     */
    suspend fun switchSameVodSite(): Boolean {
        val vod = Session.vod ?: return false
        val name = vod.vodName.trim()
        if (name.isEmpty()) return false
        Session.site?.key?.let { failedVodSites.add(it) }
        val candidates = VodConfig.visibleSites().filter { it.key !in failedVodSites }
        if (candidates.isEmpty()) {
            Diag.warn("VOD", "跨站点换源：站点都试过了（$name）")
            failedVodSites.clear()
            return false
        }
        Diag.log("VOD", "跨站点换源开始：《$name》，候选站点 ${candidates.size} 个")
        val hit = coroutineScope {
            candidates.take(14).map { candidate ->
                async(Dispatchers.IO) {
                    runCatching {
                        val spider = SpiderRouter.spider(candidate)
                        if (spider is com.github.catvod.crawler.SpiderNull) return@runCatching null
                        val list = Result.fromJson(spider.searchContent(name, true)).listSafe()
                        val one = list.firstOrNull { it.vodName.trim() == name }
                            ?: list.firstOrNull {
                                it.vodName.trim().isNotEmpty() &&
                                    (it.vodName.contains(name) || name.contains(it.vodName.trim()))
                            }
                            ?: return@runCatching null
                        val detail = Result.fromJson(spider.detailContent(listOf(one.vodId)))
                            .listSafe().firstOrNull() ?: return@runCatching null
                        candidate to detail
                    }.getOrNull()
                }
            }.mapNotNull { runCatching { it.await() }.getOrNull() }
        }.firstOrNull()
        if (hit == null) {
            failedVodSites.addAll(candidates.map { it.key })
            Diag.warn("VOD", "跨站点换源失败：${candidates.size} 个站点里没有可用的《$name》")
            return false
        }
        Diag.log("VOD", "跨站点换源成功：《$name》→ 站点「${hit.first.name}」")
        Session.site = hit.first
        Session.vod = hit.second
        Session.flagIndex = 0
        Session.episodeIndex = 0
        start()
        return true
    }

    /** 本机是否有可用 IPv6（部分直播源要求 V6 网络环境）。 */
    private fun hasIpv6(): Boolean = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().any { nic ->
            nic.isUp && !nic.isLoopback && nic.inetAddresses.toList().any { addr ->
                addr is java.net.Inet6Address && !addr.isLinkLocalAddress && !addr.isLoopbackAddress
            }
        }
    }.getOrDefault(false)

    /** 源名里带 V6 / IPV6 / V6网络 字样的，视为需要 IPv6 环境。 */
    private fun needIpv6(live: com.xiaohetv.core.bean.Live): Boolean {
        val name = live.name.uppercase()
        return name.contains("V6") || name.contains("IPV6")
    }

    /** 探测一条直播地址是否可用；返回耗时毫秒，失败返回 null（3 秒超时）。 */
    private suspend fun probeMs(
        url: String,
        channel: com.xiaohetv.core.bean.Channel
    ): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val at = System.currentTimeMillis()
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Range", "bytes=0-2048")
            if (channel.ua.isNotEmpty()) conn.setRequestProperty("User-Agent", channel.ua)
            if (channel.referer.isNotEmpty()) conn.setRequestProperty("Referer", channel.referer)
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..399) System.currentTimeMillis() - at else null
        }.getOrNull()
    }

    private suspend fun switchSameNameChannel(): Boolean {
        val name = Session.channel?.name?.trim().orEmpty()
        if (name.isEmpty()) return false
        val lives = LiveConfig.lives
        if (lives.size <= 1) return false
        val ipv6 = hasIpv6()
        failedLives.add(LiveConfig.home?.name.orEmpty())
        Diag.log(
            "Live",
            "同名跨源换源开始：$name，本机 IPv6=${if (ipv6) "有" else "无"}，直播源 ${lives.size} 个，已试过 ${failedLives.size} 个"
        )
        val startIndex = lives.indexOfFirst { it.name == LiveConfig.home?.name }.coerceAtLeast(0)
        // 候选顺序：从当前源的下一个开始绕一圈 → 去掉试过的 → 没有 IPv6 时把要求 V6 的源排到最后
        val order = (1..lives.size)
            .map { lives[(startIndex + it) % lives.size] }
            .filter { it.name !in failedLives }
            .sortedBy { if (!ipv6 && needIpv6(it)) 1 else 0 }
        // 并行预探测每个候选源里「$name」的第一条地址，取**响应最快**的那个起播
        val candidates = order.mapNotNull { live ->
            val hit = live.groups.asSequence()
                .flatMap { it.channel.asSequence() }
                .firstOrNull { it.name.trim() == name && it.urls.isNotEmpty() }
                ?: return@mapNotNull null
            live to hit
        }
        if (candidates.isNotEmpty()) {
            Diag.log("Live", "预探测 ${candidates.size} 个源里「$name」的地址，取最快的一个…")
            val probed = coroutineScope {
                candidates.map { pair ->
                    async(Dispatchers.IO) { probeMs(pair.second.urls[0], pair.second)?.let { ms -> Triple(pair.first, pair.second, ms) } }
                }.mapNotNull { runCatching { it.await() }.getOrNull() }
            }
            val fastest = probed.minByOrNull { it.third }
            if (fastest != null) {
                Diag.log(
                    "Live",
                    "预探测结果：最快 直播源「${fastest.first.name}」响应 ${fastest.third}ms" +
                        "（共 ${probed.size}/${candidates.size} 个源有响应）"
                )
                LiveConfig.home = fastest.first
                Session.live = fastest.first
                fastest.second.index = 0
                Session.channel = fastest.second
                start()
                return true
            }
            Diag.warn("Live", "预探测：${candidates.size} 个源全部无响应")
        }
        Diag.warn("Live", "同名跨源换源失败：其它直播源里没有可用的「$name」，已试 ${failedLives.size} 个源")
        // 全部试完就清空，下次（用户手动再试/换频道）从干净状态重新来
        failedLives.clear()
        return false
    }
}
