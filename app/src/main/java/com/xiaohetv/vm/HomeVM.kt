package com.xiaohetv.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaohetv.core.bean.Filter
import com.xiaohetv.core.bean.Result
import com.xiaohetv.core.bean.Site
import com.xiaohetv.core.bean.Vod
import com.xiaohetv.core.bean.VodClass
import com.xiaohetv.core.spider.SpiderRouter
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 聚合搜索并发数与单站超时（对齐参考项目"线程池并发 + 单站超时"）。 */
private const val CONCURRENCY = 8
private const val SEARCH_TIMEOUT = 12_000L

class HomeVM : ViewModel() {

    val types = MutableStateFlow<List<VodClass>>(emptyList())
    val list = MutableStateFlow<List<Vod>>(emptyList())
    val filters = MutableStateFlow<LinkedHashMap<String, List<Filter>>>(LinkedHashMap())
    val picks = MutableStateFlow<Map<String, String>>(emptyMap())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow("")
    val end = MutableStateFlow(false)

    private var site: Site? = null
    private var tid = ""
    private var page = 1
    private var pagecount = 1

    fun home(site: Site) {
        this.site = site
        tid = ""
        page = 1
        end.value = false
        load()
    }

    fun category(site: Site, id: String) {
        this.site = site
        tid = id
        page = 1
        end.value = false
        load()
    }

    fun pick(key: String, value: String) {
        val map = picks.value.toMutableMap()
        map[key] = value
        picks.value = map
        page = 1
        end.value = false
        load()
    }

    fun more() {
        if (loading.value) return
        if (page >= pagecount) {
            end.value = true
            return
        }
        page++
        load(true)
    }

    fun retry() = load()

    private fun load(append: Boolean = false) {
        val current = site ?: return
        val extend = HashMap<String, String>()
        picks.value.forEach { (k, v) -> extend[k] = v }
        val currentTid = tid
        val currentPage = page
        viewModelScope.launch(Dispatchers.IO) {
            loading.value = true
            error.value = ""
            Diag.log("Home", "请求站点「${current.name}」(api=${current.api}) tid=${currentTid.ifEmpty { "首页" }} page=$currentPage")
            val outcome = runCatching {
                if (current.searchable != 0) Unit
                val spider = SpiderRouter.spider(current)
                val result = when {
                    currentTid.isNotEmpty() -> Result.fromJson(spider.categoryContent(currentTid, currentPage.toString(), true, extend))
                    extend.isNotEmpty() -> Result.fromJson(spider.categoryContent(types.value.firstOrNull()?.typeId.orEmpty(), currentPage.toString(), true, extend))
                    else -> {
                        // 首页：homeContent 为主，homeVideoContent 为辅（单独容错，避免它抛异常拖垮首页）
                        val rawHome = spider.homeContent(true)
                        val rawVideo = runCatching { spider.homeVideoContent() }.getOrDefault("")
                        Diag.log("Home", "homeContent 长度=${rawHome.length}｜头=${rawHome.take(160)}")
                        Diag.log("Home", "homeVideoContent 长度=${rawVideo.length}｜头=${rawVideo.take(120)}")
                        // 探查首条数据真实字段名（判断海报取的是 vod_pic 还是别的键）
                        runCatching {
                            val obj = com.google.gson.JsonParser.parseString(rawHome).asJsonObject
                            val first = obj.getAsJsonArray("list")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                            if (first == null) {
                                Diag.warn("Home", "list[0] 不存在")
                            } else {
                                Diag.log("Home", "list[0] 字段=${first.keySet().joinToString(",")}")
                                Diag.log("Home", "list[0] vod_name=${first.get("vod_name")} vod_pic=${first.get("vod_pic")}")
                            }
                        }.onFailure { Diag.warn("Home", "解析 list[0] 失败：${it.message}") }
                        val home = Result.fromJson(rawHome)
                        val video = Result.fromJson(rawVideo)
                        if (video.listSafe().isNotEmpty()) home.list = video.listSafe()
                        if (home.listSafe().isEmpty() && home.classElement == null) {
                            val hint = SpiderRouter.lastError
                            if (home.msg.isEmpty()) {
                                home.msg = if (hint.isNotEmpty()) "加载失败：$hint" else "该站点首页暂无可播放内容（请试试切换分类或搜索）"
                            }
                        }
                        home
                    }
                }
                result
            }
            val result = outcome.getOrNull()
            withContext(Dispatchers.Main) {
                loading.value = false
                if (result == null) {
                    val cause = outcome.exceptionOrNull()
                    error.value = cause?.message ?: "加载失败"
                    Diag.error("Home", "加载异常：${error.value}", cause)
                    return@withContext
                }
                Diag.log("Home", "返回：影片 ${result.listSafe().size} 条，分类 ${result.types.size} 个，pagecount=${result.pagecount}")
                if (result.msg.isNotEmpty() && result.listSafe().isEmpty()) error.value = result.msg
                pagecount = if (result.pagecount <= 0) 1 else result.pagecount
                if (result.types.isNotEmpty() && !append) types.value = result.types
                // 首页（未选分类）不设置筛选项：homeContent 会把所有分类的 filters 一起返回，
                // 在首页渲染会把海报挤出屏幕。
                if (currentTid.isEmpty()) filters.value = LinkedHashMap()
                else if (result.filters.isNotEmpty()) filters.value = result.filters
                list.value = if (append) list.value + result.listSafe() else result.listSafe()
                if (result.listSafe().isEmpty() && result.types.isEmpty() && !append) {
                    val hint = SpiderRouter.lastError
                    val who = "「${current.name}」(api=${current.api})"
                    error.value = if (hint.isNotEmpty()) "加载失败：$hint" else "$who 没有返回内容"
                    Diag.warn("Home", "内容为空：list=0 class=0 msg=${result.msg} lastError=${hint.ifEmpty { "无" }}")
                }
            }
        }
    }
}

class SearchVM : ViewModel() {

    val list = MutableStateFlow<List<Vod>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow("")

    /**
     * 结果态必须放在 VM 里：进详情页时搜索页的组合会被销毁，
     * 用 remember 保存的话返回后会复位成"输入态"，用户就看不到刚才的搜索结果了。
     */
    val key = MutableStateFlow("")
    val submitted = MutableStateFlow(false)

    private var page = 1
    private var pagecount = 1

    fun search(site: Site, key: String) {
        page = 1
        list.value = emptyList()
        load(site, key, false)
    }

    fun more(site: Site, key: String) {
        if (loading.value || page >= pagecount) return
        page++
        load(site, key, true)
    }

    private fun load(site: Site, key: String, append: Boolean) {
        val currentPage = page
        viewModelScope.launch(Dispatchers.IO) {
            loading.value = true
            error.value = ""
            Diag.log("Search", "单站搜索「$key」于「${site.name}」(api=${site.api}, searchable=${site.searchable})")
            val outcome = runCatching {
                val spider = SpiderRouter.spider(site)
                // 与参考项目 SiteApi.searchContent 一致：第 1 页调 2 参版本，翻页才用 3 参。
                // 大量 csp_ spider 只实现了 searchContent(key, quick)，调 3 参会走基类返回 ""（表现为"站点没结果"）。
                val raw = if (currentPage <= 1) spider.searchContent(key, false)
                else spider.searchContent(key, false, currentPage.toString())
                Diag.log("Search", "「${site.name}」原始返回长度=${raw.length}｜头=${raw.take(160)}")
                Result.fromJson(raw)
            }
            val result = outcome.getOrNull()
            withContext(Dispatchers.Main) {
                loading.value = false
                if (result == null) {
                    error.value = outcome.exceptionOrNull()?.message ?: "搜索失败"
                    return@withContext
                }
                pagecount = if (result.pagecount <= 0) 1 else result.pagecount
                result.listSafe().forEach { it.siteKey = site.key }
                list.value = if (append) list.value + result.listSafe() else result.listSafe()
                if (list.value.isEmpty()) error.value = "没有找到相关内容"
            }
        }
    }

    fun multi(sites: List<Site>, key: String) {
        list.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            loading.value = true
            error.value = ""
            val targets = sites.filter { it.searchable == 1 }
            Diag.log("Search", "跨站搜索「$key」，参与站点 ${targets.size} 个（并发 $CONCURRENCY）")
            // 与参考项目对齐（ViewModelSearchRunner：线程池并发 + 单站超时）。
            // 之前并发失败是因为「创建 spider」也并发，把 guard 的全局状态冲了；
            // 现在 JarLoader 内部用全局 bootLock + 同一 jar 只 Init 一次，创建是串行的，
            // 这里只并发网络请求，且单站超时后跳过，避免一个死站拖垮整轮。
            val limiter = Semaphore(CONCURRENCY)
            coroutineScope {
                targets.map { site ->
                    async {
                        limiter.withPermit {
                            val job = async(Dispatchers.IO) {
                                runCatching {
                                    val spider = SpiderRouter.spider(site)
                                    // 同上：聚合搜索永远从第 1 页开始，必须调 2 参版本
                                    Result.fromJson(spider.searchContent(key, false))
                                }
                            }
                            val outcome = withTimeoutOrNull(SEARCH_TIMEOUT) { job.await() }
                            if (outcome == null) {
                                Diag.warn("Search", "「${site.name}」超过 ${SEARCH_TIMEOUT / 1000}s，跳过")
                                job.cancel()
                                return@withPermit
                            }
                            outcome.onSuccess { result ->
                                result.listSafe().forEach { it.siteKey = site.key }
                                Diag.log("Search", "「${site.name}」返回 ${result.listSafe().size} 条")
                                withContext(Dispatchers.Main) { list.value = list.value + result.listSafe() }
                            }.onFailure {
                                Diag.error("Search", "「${site.name}」搜索失败", it)
                            }
                        }
                    }
                }.awaitAll()
            }
            withContext(Dispatchers.Main) {
                loading.value = false
                if (list.value.isEmpty()) {
                    error.value = "没有找到相关内容"
                    Diag.warn("Search", "全部站点均无结果（lastError=${SpiderRouter.lastError.ifEmpty { "无" }}）")
                }
            }
        }
    }
}

class DetailVM : ViewModel() {

    val vod = MutableStateFlow<Vod?>(null)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow("")

    fun load(site: Site, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            loading.value = true
            error.value = ""
            val outcome = runCatching {
                val spider = SpiderRouter.spider(site)
                Result.fromJson(spider.detailContent(listOf(id))).listSafe().firstOrNull()
            }
            withContext(Dispatchers.Main) {
                loading.value = false
                val item = outcome.getOrNull()
                if (item == null) error.value = outcome.exceptionOrNull()?.message ?: "详情加载失败"
                else {
                    item.siteKey = site.key
                    // 关键：详情才带回完整的播放源（vod_play_from/vod_play_url），必须写回 Session。
                    // 否则播放器拿到的还是搜索/首页那份只有标题海报的残缺 Vod → 一直"无法播放"。
                    Session.vod = item
                    vod.value = item
                    Diag.log("Detail", "详情就绪：「${item.vodName}」线路 ${item.flags().size} 条，已写回 Session")
                }
            }
        }
    }
}
