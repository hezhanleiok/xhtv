package com.xiaohetv.core.spider

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.crawler.SpiderNull
import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.JSCallFunction
import com.whl.quickjs.wrapper.JSFunction
import com.whl.quickjs.wrapper.JSObject
import com.whl.quickjs.wrapper.QuickJSContext
import com.xiaohetv.core.Core
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Path
import com.xiaohetv.core.utils.UriUtil
import com.xiaohetv.core.utils.Util
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * 基于 **QuickJS** 的 JavaScript Spider（`api` 以 `.js` 结尾）。
 *
 * ## 为什么用它
 * 参考项目 FongMi 的 JS spider 就是跑在 QuickJS 上（其 `quickjs` 模块：
 * `implementation libs.quickjs.android / .java`，即 `wang.harlon.quickjs:wrapper-android:3.2.3`）。
 * 我们此前用 Rhino，能力等价但**语法偏旧**：可选链 `?.`、空值合并 `??`、`?.()`、
 * 模板里的现代写法等会让解析直接失败（Rhino 只到 ES6）。QuickJS 支持现代 ES 语法，
 * 能显著减少"这个 JS 站点整站失效"的情况。
 *
 * ## 实现要点
 * 1. **单线程亲和**：QuickJS 运行时不是线程安全的，且上下文绑定创建它的线程。
 *    所以所有调用都串行派发到本实例专属的 [HandlerThread] 上（见 [onJs]）。
 * 2. **宿主注入**：`req / joinUrl / md5X / base64Encode·Decode / s2t·t2s /
 *    getProxy·getPort / setTimeout / print·log / console.log`，与 Rhino 版保持一致。
 * 3. **失败自动回退**：初始化或首次调用失败时，[create] 会回退到 [JsSpider]（Rhino），
 *    保证"QuickJS 不出画面"不会导致站点直接挂掉。
 * 4. 不改语义地兼容 ES module 写法（`export default {…}`），同 Rhino 版一样做最小改写。
 */
class QuickJsSpider(private val code: String) : Spider() {

    /** 需要在全局作用域可用的 spider 方法名（兼容 TVBox 原名与影視TV 的简写名）。 */
    private val methods = listOf(
        "init", "home", "homeVod", "homeContent", "homeVideoContent",
        "category", "categoryContent", "detail", "detailContent",
        "search", "searchContent", "play", "playerContent",
        "live", "liveContent", "proxy", "localProxy", "action", "destroy"
    )

    @Volatile private var engine: QuickJSContext? = null
    @Volatile private var global: JSObject? = null
    @Volatile private var failed = false

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    /** 是否已经真正跑在 QuickJS 上（未就绪时由 [create] 回退 Rhino）。 */
    fun isReady(): Boolean = global != null

    // ---------------------------------------------------------------- 线程调度

    private fun ensureThread(): Handler {
        handler?.let { return it }
        val t = HandlerThread("quickjs-spider").apply { start() }
        val h = Handler(t.looper)
        thread = t
        handler = h
        return h
    }

    /**
     * 把一段操作派发到 QuickJS 专属线程执行并同步等结果。
     * 已在 JS 线程上（例如 `setTimeout` 回调里再调 JS）时直接执行，避免自锁。
     */
    private fun onJs(timeoutSec: Long = 45, block: () -> Any?): Any? {
        val h = ensureThread()
        if (Looper.myLooper() === thread?.looper) return runCatching { block() }.getOrNull()
        val task = FutureTask<Any?> { block() }
        h.post(task)
        return runCatching { task.get(timeoutSec, TimeUnit.SECONDS) }.getOrElse {
            SpiderDebug.log("QuickJS 调用超时/异常：${it.message}")
            null
        }
    }

    // ---------------------------------------------------------------- 初始化

    private fun ensureReady(): Boolean {
        if (global != null) return true
        if (failed) return false
        val ok = onJs(30) {
            runCatching {
                QuickJSLoader.init()                       // System.loadLibrary("quickjs-android-wrapper")
                val context = QuickJSContext.create()
                context.setConsole(object : QuickJSContext.Console {
                    override fun log(info: String) = SpiderDebug.log(info)
                    override fun info(info: String) = SpiderDebug.log(info)
                    override fun warn(info: String) = SpiderDebug.log("warn: $info")
                    override fun error(info: String) = SpiderDebug.log("error: $info")
                })
                val scope = context.globalObject
                inject(context, scope)
                context.evaluate(preprocess(code), "spider.js")
                bindDefaultExport(scope)
                engine = context
                global = scope
                true
            }.onFailure {
                SpiderDebug.log("QuickJS 初始化失败：${it.javaClass.simpleName} ${it.message}")
            }.getOrDefault(false)
        }
        if (ok != true) {
            failed = true
            closeQuietly()
            return false
        }
        SpiderDebug.log("JS Spider：已启用 QuickJS 3.2.3 原生引擎（libquickjs-android-wrapper.so）")
        return true
    }

    /**
     * 影視TV 的 JS spider 是 ES module（`export default {…}`）。QuickJS 的 `evaluate`
     * 走的是脚本模式，直接执行会报语法错误 —— 这里做与 Rhino 版一致的最小改写：
     *   `export default X` → `__spider = X`
     *   行首 `export const|let|var|function|class|async` → 去掉 `export `
     */
    private fun preprocess(src: String): String {
        var out = src
        out = out.replace(Regex("""export\s+default\s+"""), "__spider = ")
        out = out.replace(Regex("""(?m)^[ \t]*export[ \t]+(?=(const|let|var|function|class|async)\b)"""), "")
        if (out != src) SpiderDebug.log("JS spider(QuickJS): 已按 ES module 预处理 export/export default")
        return out
    }

    /** 把 `export default` 上的方法挂成全局函数（调用是按全局名查找的）。 */
    private fun bindDefaultExport(scope: JSObject) {
        var exported = runCatching { scope.getProperty("__spider") }.getOrNull() ?: return
        if (exported is JSFunction) {
            val called = runCatching { exported.call() }.getOrNull()
            if (called is JSObject) exported = called
        }
        if (exported !is JSObject) return
        var bound = 0
        methods.forEach { name ->
            val fn = runCatching { exported.getJSFunction(name) }.getOrNull() ?: return@forEach
            runCatching { scope.setProperty(name, fn) }.onSuccess { bound++ }
        }
        if (bound > 0) SpiderDebug.log("JS spider(QuickJS): 已绑定 export default 的 $bound 个方法")
    }

    // ---------------------------------------------------------------- 宿主函数注入

    /** Java 侧回调包装（`Object call(Object... args)`）。 */
    private class Fn(private val block: (Array<out Any?>) -> Any?) : JSCallFunction {
        override fun call(vararg args: Any?): Any? = block(args)
    }

    private fun inject(context: QuickJSContext, scope: JSObject) {
        scope.setProperty("req", Fn { args -> req(args) })
        scope.setProperty("joinUrl", Fn { args ->
            val base = args.getOrNull(0)?.toString().orEmpty()
            val rel = args.getOrNull(1)?.toString().orEmpty()
            if (base.isEmpty()) "" else UriUtil.resolve(base, rel)
        })
        scope.setProperty("md5X", Fn { args -> Util.md5(args.getOrNull(0)?.toString().orEmpty()) })
        scope.setProperty("md5", Fn { args -> Util.md5(args.getOrNull(0)?.toString().orEmpty()) })
        scope.setProperty("base64Encode", Fn { args -> Util.base64(args.getOrNull(0)?.toString().orEmpty()) })
        scope.setProperty("btoa", Fn { args -> Util.base64(args.getOrNull(0)?.toString().orEmpty()) })
        scope.setProperty("base64Decode", Fn { args -> Util.base64Decode(args.getOrNull(0)?.toString().orEmpty()) })
        scope.setProperty("atob", Fn { args -> Util.base64Decode(args.getOrNull(0)?.toString().orEmpty()) })
        scope.setProperty("s2t", Fn { args -> args.getOrNull(0)?.toString().orEmpty() })
        scope.setProperty("t2s", Fn { args -> args.getOrNull(0)?.toString().orEmpty() })
        scope.setProperty("getProxy", Fn { "" })
        scope.setProperty("getPort", Fn { "" })
        scope.setProperty("print", Fn { args -> logArgs(args) })
        scope.setProperty("log", Fn { args -> logArgs(args) })
        scope.setProperty("setTimeout", Fn { args ->
            val fn = args.getOrNull(0) as? JSFunction
            val delay = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            // 回到 JS 专属线程执行，保证线程亲和
            if (fn != null) handler?.postDelayed({ runCatching { fn.call() } }, delay)
            null
        })
        val console = context.createNewJSObject()
        console.setProperty("log", Fn { args -> logArgs(args) })
        scope.setProperty("console", console)
    }

    private fun logArgs(args: Array<out Any?>): Any? {
        SpiderDebug.log(args.joinToString(" ") { js2str(it) })
        return null
    }

    /** 和 Rhino 版行为一致：GET 或未指定 body 走 GET，否则 POST。 */
    private fun req(args: Array<out Any?>): Any? {
        val url = args.getOrNull(0)?.toString() ?: return ""
        val option = args.getOrNull(1)
        val headers = asMap(asMap(option)["headers"]).mapNotNull { (k, v) ->
            if (v == null) null else k to v.toString()
        }.toMap()
        val method = asMap(option)["method"]?.toString()?.uppercase().orEmpty()
        val body = asMap(option)["body"]?.toString() ?: asMap(option)["data"]?.toString()
        return runCatching {
            if (body.isNullOrEmpty() || method == "GET") {
                OkHttp.string(url, headers)
            } else {
                val media = "application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull()
                val builder = okhttp3.Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                if (method == "POST") builder.post(body.toRequestBody(media))
                OkHttp.client().newCall(builder.build()).execute().use { it.body?.string().orEmpty() }
            }
        }.getOrElse {
            SpiderDebug.log("js req 失败：${it.message}  $url")
            ""
        }
    }

    /** 把 JS 侧传入的普通对象安全地转成 Map（兼容已是 Java Map 的情况）。 */
    private fun asMap(value: Any?): Map<String, Any?> = when (value) {
        null -> emptyMap()
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to v }
        is JSObject -> runCatching {
            value.toMap().entries.associate { (k, v) -> k to v }
        }.getOrDefault(emptyMap())
        else -> emptyMap()
    }

    // ---------------------------------------------------------------- Java ⇄ JS 转换

    private fun toJs(value: Any?): Any? = when (value) {
        null -> null
        is JSObject -> value
        is Map<*, *> -> {
            val obj = engine?.createNewJSObject() ?: return null
            value.forEach { (k, v) -> put(obj, k.toString(), v) }
            obj
        }
        is List<*> -> {
            val arr = engine?.createNewJSArray() ?: return null
            value.forEachIndexed { index, v -> arr.set(toJs(v), index) }
            arr
        }
        is Array<*> -> toJs(value.toList())
        is String, is Boolean -> value
        is Number -> value
        else -> value.toString()
    }

    /** JSObject.setProperty 只接受固定几种类型，这里做一次分派。 */
    private fun put(obj: JSObject, key: String, value: Any?) {
        when (val v = toJs(value)) {
            null -> obj.setProperty(key, "")            // JS 侧无 null 重载，用空串（与 Rhino 的 Undefined 行为一致）
            is String -> obj.setProperty(key, v)
            is Boolean -> obj.setProperty(key, v)
            is Int -> obj.setProperty(key, v)
            is Long -> obj.setProperty(key, v)
            is Number -> obj.setProperty(key, v.toDouble())
            is ByteArray -> obj.setProperty(key, v)
            is JSObject -> obj.setProperty(key, v)
            else -> obj.setProperty(key, v.toString())
        }
    }

    private fun js2str(value: Any?): String = when (value) {
        null -> "null"
        is JSObject -> runCatching { value.stringify() }.getOrDefault("[object]")
        else -> value.toString()
    }

    private fun toJson(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Number, is Boolean -> value.toString()
        is JSObject -> runCatching { value.stringify() }.getOrDefault("")
        else -> value.toString()
    }

    // ---------------------------------------------------------------- 调用入口

    private fun call(name: String, args: Array<Any?> = emptyArray()): String {
        if (!ensureReady()) return ""
        val scope = global ?: return ""
        val out = onJs(60) {
            runCatching {
                val fn = scope.getJSFunction(name) ?: return@runCatching ""
                val jsArgs = args.map { toJs(it) }.toTypedArray()
                toJson(fn.call(*jsArgs))
            }.onFailure {
                SpiderDebug.log("JS $name() 执行异常：${it.javaClass.simpleName} ${it.message}")
            }.getOrDefault("")
        }
        return out?.toString().orEmpty()
    }

    private fun closeQuietly() {
        runCatching { engine?.destroy() }
        engine = null
        global = null
        runCatching { thread?.quitSafely() }
        thread = null
        handler = null
    }

    // ---------------------------------------------------------------- Spider 接口

    override fun init(context: Context, extend: String) {
        ensureReady()
        call("init", arrayOf(extend))
    }

    override fun homeContent(filter: Boolean): String = call("home", arrayOf(filter))

    override fun homeVideoContent(): String = call("homeVod")

    override fun categoryContent(tid: String, pg: String, filter: Boolean, extend: HashMap<String, String>): String =
        call("category", arrayOf(tid, pg, filter, extend))

    override fun detailContent(ids: List<String>): String = call("detail", arrayOf(ids.firstOrNull().orEmpty()))

    override fun searchContent(key: String, quick: Boolean): String = call("search", arrayOf(key, quick))

    override fun searchContent(key: String, quick: Boolean, pg: String): String =
        call("search", arrayOf(key, quick, pg))

    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String =
        call("play", arrayOf(flag, id, vipFlags))

    override fun liveContent(url: String): String = call("live", arrayOf(url))

    override fun manualVideoCheck(): Boolean = call("sniffer").toBoolean()

    override fun isVideoFormat(url: String): Boolean = call("isVideo", arrayOf(url)).toBoolean()

    override fun action(action: String): String = call("action", arrayOf(action))

    override fun destroy() {
        call("destroy")
        closeQuietly()
        SpiderDebug.log("JS Spider：QuickJS 上下文已释放")
    }

    companion object {

        @JvmStatic
        fun create(api: String, ext: String): Spider {
            val quick = runCatching {
                val spider = QuickJsSpider(readSource(api))
                spider.init(Core.context(), ext)
                if (spider.isReady()) spider else null
            }.getOrNull()
            if (quick != null) return quick
            SpiderDebug.log("QuickJS 不可用 → 回退 Rhino 引擎（站点仍可用）")
            return JsSpider.create(api, ext)
        }

        private fun readSource(api: String): String = when {
            api.startsWith("assets://") -> Core.context().assets.open(api.removePrefix("assets://"))
                .bufferedReader().use { it.readText() }
            api.startsWith("file://") -> File(api.removePrefix("file://")).readText()
            api.startsWith("http") -> {
                val file = Path.js(api)
                if (!file.exists()) file.writeText(OkHttp.string(api))
                file.readText()
            }
            else -> File(api).readText()
        }
    }
}
