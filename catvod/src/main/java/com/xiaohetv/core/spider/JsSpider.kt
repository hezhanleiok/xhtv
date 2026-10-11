package com.xiaohetv.core.spider

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.crawler.SpiderNull
import com.xiaohetv.core.Core
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Path
import com.xiaohetv.core.utils.UriUtil
import com.xiaohetv.core.utils.Util
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeJavaObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.io.File

/**
 * 基于 Rhino 的 JavaScript Spider（api 以 .js 结尾）。
 */
class JsSpider(private val code: String) : Spider() {

    /** 需要在全局作用域可用的 spider 方法名（兼容 TVBox 原名与影視TV 的简写名）。 */
    private val METHODS = listOf(
        "init", "home", "homeVod", "homeContent", "homeVideoContent",
        "category", "categoryContent", "detail", "detailContent",
        "search", "searchContent", "play", "playerContent",
        "live", "liveContent", "proxy", "localProxy", "action", "destroy"
    )

    private var scope: ScriptableObject? = null
    private val handler = Handler(Looper.getMainLooper())

    fun setup() {
        if (scope != null) return
        withContext { ctx ->
            val root = ctx.initStandardObjects()
            ScriptableObject.putProperty(root, "globalThis", root)
            scope = root
            inject(ctx, root)
            val source = preprocess(code)
            ctx.evaluateString(root, source, "spider.js", 1, null)
            bindDefaultExport(ctx, root)
        }
    }

    /**
     * 影視TV 的 JS spider 是 **ES module**，写法为 `export default { home(){…}, … }`
     * 或 `export default function(){ return {…} }`；Rhino 不支持 module 语法，
     * 整份文件会直接判为语法错误 → 该 JS 站点全部功能失效。
     * 这里做最小改写（不改语义）：
     *   `export default X`            → `__spider = X`
     *   行首 `export const|let|var|function|class|async` → 去掉 `export `
     */
    private fun preprocess(src: String): String {
        var out = src
        out = out.replace(Regex("""export\s+default\s+"""), "__spider = ")
        out = out.replace(Regex("""(?m)^[ \t]*export[ \t]+(?=(const|let|var|function|class|async)\b)"""), "")
        if (out != src) SpiderDebug.log("JS spider: 已按 ES module 预处理 export/export default")
        return out
    }

    /** 把 `export default` 上的方法挂成全局函数（call() 就是按全局名查找的）。 */
    private fun bindDefaultExport(ctx: RhinoContext, root: ScriptableObject) {
        var exported: Any? = ScriptableObject.getProperty(root, "__spider")
        if (exported == ScriptableObject.NOT_FOUND || exported == null) return
        // 导出的是函数（工厂函数）时，先调用一次拿到对象
        if (exported is Function) {
            val result = runCatching { exported.call(ctx, root, root, emptyArray()) }.getOrNull()
            if (result is Scriptable && hasAnyMethod(result)) exported = result
        }
        if (exported !is Scriptable) return
        var bound = 0
        METHODS.forEach { name ->
            val value = runCatching { ScriptableObject.getProperty(exported as Scriptable, name) }.getOrNull()
            if (value is Function) {
                runCatching { ScriptableObject.putProperty(root, name, value) }
                bound++
            }
        }
        if (bound > 0) SpiderDebug.log("JS spider: 已绑定 export default 的 $bound 个方法")
    }

    private fun hasAnyMethod(target: Scriptable): Boolean = METHODS.any {
        runCatching { ScriptableObject.getProperty(target, it) is Function }.getOrDefault(false)
    }

    private fun <T> withContext(block: (RhinoContext) -> T): T {
        val ctx = RhinoContext.enter()
        ctx.optimizationLevel = -1
        ctx.languageVersion = RhinoContext.VERSION_ES6
        return try {
            block(ctx)
        } finally {
            RhinoContext.exit()
        }
    }

    private fun inject(ctx: RhinoContext, root: ScriptableObject) {
        define(root, "req") { args ->
            val url = args.getOrNull(0)?.toString() ?: return@define ""
            val option = args.getOrNull(1) as? Map<*, *>
            val headers = (option?.get("headers") as? Map<*, *>)?.mapNotNull { (k, v) ->
                if (k == null || v == null) null else k.toString() to v.toString()
            }?.toMap() ?: emptyMap()
            val method = option?.get("method")?.toString()?.uppercase().orEmpty()
            val body = option?.get("body")?.toString() ?: option?.get("data")?.toString()
            runCatching {
                if (body.isNullOrEmpty() || method == "GET") {
                    OkHttp.string(url, headers)
                } else {
                    val media = "application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull()
                    val builder = okhttp3.Request.Builder().url(url)
                    headers.forEach { (k, v) -> builder.header(k, v) }
                    if (method == "POST") builder.post(body.toRequestBody(media))
                    OkHttp.client().newCall(builder.build()).execute().use { it.body?.string().orEmpty() }
                }
            }.getOrDefault("")
        }
        define(root, "joinUrl") { args ->
            val base = args.getOrNull(0)?.toString() ?: return@define ""
            val rel = args.getOrNull(1)?.toString() ?: return@define base
            UriUtil.resolve(base, rel)
        }
        define(root, "md5X") { args -> Util.md5(args.getOrNull(0)?.toString().orEmpty()) }
        define(root, "base64Encode") { args -> Util.base64(args.getOrNull(0)?.toString().orEmpty()) }
        define(root, "base64Decode") { args -> Util.base64Decode(args.getOrNull(0)?.toString().orEmpty()) }
        define(root, "s2t") { args -> args.getOrNull(0)?.toString().orEmpty() }
        define(root, "t2s") { args -> args.getOrNull(0)?.toString().orEmpty() }
        define(root, "getProxy") { "" }
        define(root, "getPort") { "" }
        define(root, "setTimeout") { args ->
            val fn = args.getOrNull(0)
            val delay = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            if (fn is Function) {
                handler.postDelayed({
                    withContext { ctx -> runCatching { fn.call(ctx, scope, scope, emptyArray()) } }
                }, delay)
            }
            ""
        }
        define(root, "print") { args -> log(args) }
        define(root, "log") { args -> log(args) }
        val console = ctx.newObject(root, "Object", emptyArray())
        ScriptableObject.putProperty(console, "log", function { args -> log(args) })
        ScriptableObject.putProperty(root, "console", console)
    }

    private fun log(args: Array<Any?>) {
        SpiderDebug.log(args.joinToString(" ") { it?.toString().orEmpty() })
    }

    private fun function(block: (Array<Any?>) -> Any?): BaseFunction = object : BaseFunction() {
        override fun call(cx: RhinoContext, s: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
            val converted = args.map { toJava(it) }.toTypedArray()
            return RhinoContext.javaToJS(block(converted), s)
        }
    }

    private fun define(root: ScriptableObject, name: String, block: (Array<Any?>) -> Any?) {
        ScriptableObject.putProperty(root, name, function(block))
    }

    private fun call(name: String, args: Array<Any?> = emptyArray()): String {
        setup()
        val root = scope ?: return ""
        return withContext { ctx ->
            val value = ScriptableObject.getProperty(root, name)
            if (value !is Function) return@withContext ""
            val jsArgs = args.map { toJs(ctx, root, it) }.toTypedArray()
            runCatching { toJson(value.call(ctx, root, root, jsArgs)) }.getOrDefault("")
        }
    }

    private fun toJs(ctx: org.mozilla.javascript.Context, root: Scriptable, value: Any?): Any? = when (value) {
        null -> Undefined.instance
        is Map<*, *> -> {
            val obj = ctx.newObject(root, "Object", emptyArray())
            value.forEach { (k, v) -> ScriptableObject.putProperty(obj, k.toString(), toJs(ctx, root, v)) }
            obj
        }
        is List<*> -> ctx.newArray(root, value.map { toJs(ctx, root, it) }.toTypedArray())
        is Array<*> -> ctx.newArray(root, value.map { toJs(ctx, root, it) }.toTypedArray())
        else -> RhinoContext.javaToJS(value, root)
    }

    override fun init(context: Context, extend: String) {
        setup()
        call("init", arrayOf(extend))
    }

    override fun homeContent(filter: Boolean): String = call("home", arrayOf(filter))

    override fun homeVideoContent(): String = call("homeVod")

    override fun categoryContent(tid: String, pg: String, filter: Boolean, extend: HashMap<String, String>): String =
        call("category", arrayOf(tid, pg, filter, extend))

    override fun detailContent(ids: List<String>): String = call("detail", arrayOf(ids.firstOrNull().orEmpty()))

    override fun searchContent(key: String, quick: Boolean): String = call("search", arrayOf(key, quick))

    override fun searchContent(key: String, quick: Boolean, pg: String): String = call("search", arrayOf(key, quick, pg))

    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String =
        call("play", arrayOf(flag, id, vipFlags))

    override fun liveContent(url: String): String = call("live", arrayOf(url))

    override fun manualVideoCheck(): Boolean = call("sniffer").toBoolean()

    override fun isVideoFormat(url: String): Boolean = call("isVideo", arrayOf(url)).toBoolean()

    override fun action(action: String): String = call("action", arrayOf(action))

    override fun destroy() {
        call("destroy")
    }

    private fun toJava(value: Any?): Any? = when (value) {
        null -> null
        is NativeJavaObject -> value.unwrap()
        is Undefined -> null
        is Scriptable -> {
            if (isArray(value)) toList(value)
            else if (isObject(value)) toMap(value)
            else RhinoContext.toString(value)
        }
        else -> value
    }

    private fun isArray(value: Scriptable): Boolean =
        ScriptableObject.getProperty(value, "constructor")?.let { RhinoContext.toString(it) }?.contains("Array") == true

    private fun isObject(value: Scriptable): Boolean {
        val ids = ScriptableObject.getPropertyIds(value)
        return ids.isNotEmpty()
    }

    private fun toList(value: Scriptable): List<Any?> {
        val length = (ScriptableObject.getProperty(value, "length") as? Number)?.toInt() ?: 0
        return (0 until length).map { toJava(ScriptableObject.getProperty(value, it)) }
    }

    private fun toMap(value: Scriptable): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        ScriptableObject.getPropertyIds(value).forEach { id ->
            val key = id.toString()
            map[key] = toJava(ScriptableObject.getProperty(value, key))
        }
        return map
    }

    private fun toJson(value: Any?): String {
        val java = toJava(value) ?: return ""
        return when (java) {
            is String -> java
            is Number, is Boolean -> java.toString()
            is Map<*, *> -> runCatching { JSONObject(java as Map<Any?, Any?>).toString() }.getOrDefault(java.toString())
            is List<*> -> runCatching { JSONArray(java).toString() }.getOrDefault(java.toString())
            else -> java.toString()
        }
    }

    companion object {
        @JvmStatic
        fun create(api: String, ext: String): Spider {
            return runCatching {
                val text = when {
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
                JsSpider(text).apply { init(Core.context(), ext) }
            }.getOrElse {
                SpiderDebug.log("js load error: ${it.message}")
                SpiderNull()
            }
        }
    }
}
