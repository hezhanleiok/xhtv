package com.xiaohetv.core.spider

import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.crawler.SpiderNull
import com.xiaohetv.core.Core
import com.xiaohetv.core.bean.Site
import com.xiaohetv.core.utils.Diag
import java.util.concurrent.ConcurrentHashMap

object SpiderRouter {

    private val cache = ConcurrentHashMap<String, Spider>()
    private val locks = ConcurrentHashMap<String, Any>()

    /** 最近一次 spider 加载失败原因（供 UI 提示，便于自检）。 */
    @JvmStatic
    var lastError: String = ""
        private set

    @JvmStatic
    fun spider(site: Site): Spider = spider(
        key = site.key,
        api = site.api,
        ext = site.extString(),
        jar = site.jar,
        type = site.type,
        header = site.headerMap()
    )

    @JvmStatic
    fun spider(
        api: String,
        ext: String = "",
        jar: String? = null,
        type: Int = 3,
        header: Map<String, String> = emptyMap(),
        key: String = ""
    ): Spider {
        val cacheKey = key.ifEmpty { api }
        cache[cacheKey]?.let { return it }
        val lock = locks.getOrPut(cacheKey) { Any() }
        synchronized(lock) {
            cache[cacheKey]?.let { return it }
            val instance = runCatching {
                when {
                    api.isBlank() -> SpiderNull()
                    api.startsWith("csp_") -> JarLoader.load(key, api, ext, jar)
                    // JS spider：优先 QuickJS（现代 ES 语法，与参考项目 FongMi 同款同版本 3.2.3），
                    // 初始化失败时由 QuickJsSpider.create 内部自动回退 Rhino。
                    api.endsWith(".js") -> QuickJsSpider.create(api, ext)
                    api.endsWith(".py") -> SpiderNull()
                    else -> CmsSpider(api, type).apply {
                        setHeader(header)
                        init(Core.context(), ext)
                    }
                }
            }.getOrElse {
                // 解开 InvocationTargetException，拿到真正的底层原因（如 dlopen failed / NoSuchMethod）
                val cause = it.cause ?: it
                lastError = cause.message ?: cause.javaClass.simpleName
                SpiderDebug.log(cause)
                Diag.error("Spider", "[$key] 创建失败：$lastError", cause)
                SpiderNull()
            }
            instance.siteKey = key
            // 加载失败得到的 SpiderNull 不写缓存，避免一次失败后永久空白（可自愈）
            if (instance !is SpiderNull) {
                cache[cacheKey] = instance
                lastError = ""
            } else {
                Diag.warn("Spider", "[$key] 本次未拿到可用 spider（api=$api）")
            }
            return instance
        }
    }

    @JvmStatic
    fun clear() {
        cache.values.forEach { runCatching { it.destroy() } }
        cache.clear()
        JarLoader.clear()
    }
}
