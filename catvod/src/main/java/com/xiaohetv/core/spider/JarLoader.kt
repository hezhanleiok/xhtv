package com.xiaohetv.core.spider

import android.content.Context
import com.github.catvod.crawler.Spider
import com.xiaohetv.core.Core
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.utils.Diag
import com.xiaohetv.core.utils.Path
import com.xiaohetv.core.utils.Util
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 加载 CatVod Java Spider（csp_ 开头的 jar）。
 *
 * 与 FongMi/TV 的 JarLoader 保持一致：
 * 1. DexClassLoader 的 optimizedDir 与 librarySearchPath 都指向 jar 所在目录（Guard 类 spider 需要在这里解压/加载 libwexguard.so）。
 * 2. 加载后反射调用 jar 自带的 `com.github.catvod.spider.Init.init(Context)` 与 `com.github.catvod.spider.Proxy.proxy(Map)`。
 *    注意：host 绝不能定义 `com.github.catvod.spider.Init`，否则父类加载器会命中 host 的 stub，把 jar 自带 Init 屏蔽掉。
 *
 * 加载失败时直接抛异常，由 SpiderRouter 记录原因并返回 SpiderNull（不再静默失败）。
 */
object JarLoader {

    private val loaders = ConcurrentHashMap<String, DexClassLoader>()
    private val spiders = ConcurrentHashMap<String, Spider>()
    private val proxies = ConcurrentHashMap<String, Method>()
    private val locks = ConcurrentHashMap<String, Any>()
    /** 已经跑过 Init.init / Proxy 的 jar（同一个 jar 只初始化一次，避免重复 init 干扰 guard 全局状态）。 */
    private val inited = ConcurrentHashMap.newKeySet<String>()

    /**
     * jar 的初始化（jar 自带 Init.init / Guard 解密 / getSpider）依赖 jar 内部的**全局静态状态**，
     * 并发初始化会互相污染：实测并发时 31 个站点里 24 个 Guard 自检返回空、25 个创建失败，搜索全为 0 条。
     * 所以创建阶段全局串行（只锁创建，不锁之后的 searchContent 请求）。
     */
    private val bootLock = Any()

    @JvmStatic
    fun load(key: String, api: String, ext: String, jar: String?): Spider {
        val cacheKey = "$key|$api"
        spiders[cacheKey]?.let { return it }
        val lock = locks.getOrPut(cacheKey) { Any() }
        synchronized(lock) {
            spiders[cacheKey]?.let { return it }
            synchronized(bootLock) {
                return boot(key, api, ext, jar, cacheKey)
            }
        }
    }

    private fun boot(key: String, api: String, ext: String, jar: String?, cacheKey: String): Spider {
        Diag.log("Spider", "[$key] 开始加载 $api，jar=${Diag.safeUrl(jar.orEmpty())}")
        val file = parseJar(jar) ?: throw IllegalStateException("spider jar 未下载成功：$jar")
        Diag.log("Spider", "[$key] jar 就绪：${file.name}（${file.length()} 字节）")
        val loader = loaders.getOrPut(file.absolutePath) { createLoader(file) }
        val name = api.substringAfter("csp_")
        if (inited.add(file.absolutePath)) {
            guardBoot(loader, name)
            invokeProxy(key, loader)
        } else {
            // 后续站点只做自检记录，不重复 Init.init（既提速也避免干扰 guard 全局状态）
            probeGuard(loader, name)
        }
        val clazz = runCatching { loader.loadClass("com.github.catvod.spider.$name") }
            .getOrElse {
                Diag.error("Spider", "[$key] jar 中没有找到类 com.github.catvod.spider.$name", it)
                throw IllegalStateException("jar 中没有找到类 com.github.catvod.spider.$name")
            }
        Diag.log("Spider", "[$key] 已取得类 $name")
        val spider = clazz.getDeclaredConstructor().newInstance() as Spider
        spider.siteKey = key
        spider.init(Core.context(), ext)
        Diag.log("Spider", "[$key] spider 构造 + init 完成")
        spiders[cacheKey] = spider
        return spider
    }

    private fun createLoader(file: File): DexClassLoader {
        runCatching { file.setReadOnly() }
        val dir = file.parentFile?.absolutePath ?: Path.jar().absolutePath
        return DexClassLoader(file.absolutePath, dir, dir, Core.context().classLoader)
    }

    /**
     * 跑 Guard 壳的 init + 自检。壳内部会 try/catch 掉自己的失败，只往 System.err 打印堆栈，
     * 所以这里临时接管 System.err，把壳打印的真实原因（如 dlopen failed…）一起抛出，
     * 否则永远只能看到"返回空"。
     */
    private fun guardBoot(loader: ClassLoader, name: String) {
        val buffer = java.io.ByteArrayOutputStream()
        val original = System.err
        System.setErr(java.io.PrintStream(buffer, true))
        var failure: Throwable? = null
        try {
            Diag.log("Guard", "调用 jar 自带 Init.init(Context) …")
            invokeInit(loader)
            Diag.log("Guard", "Init.init 完成")
            probeGuard(loader, name)
        } catch (t: Throwable) {
            failure = t
        } finally {
            System.setErr(original)
        }
        val shell = buffer.toString().trim().replace(Regex("\\s+"), " ").take(500)
        if (shell.isNotEmpty()) {
            Diag.error("Guard", "壳自身输出（native 层的真实原因）：$shell")
        }
        if (failure != null) {
            val cause = failure.cause ?: failure
            val message = cause.message ?: cause.javaClass.simpleName
            Diag.error("Guard", "Init.init 失败：$message", cause)
            throw IllegalStateException(if (shell.isEmpty()) message else "$message｜壳日志：$shell", cause)
        }
    }

    /**
     * jar 自带的初始化入口。Guard 类 spider 靠它解压/加载 native 库并解密自身 dex。
     * 这里**故意不吞异常**：一旦它失败，真实 spider 拿不到，必须把原因冒泡给 SpiderRouter，
     * 否则首页只会显示"没有返回内容"，无法定位（曾因此排查十几轮）。
     */
    private fun invokeInit(loader: ClassLoader) {
        val clz = runCatching { loader.loadClass("com.github.catvod.spider.Init") }.getOrNull() ?: return
        clz.getMethod("init", Context::class.java).invoke(null, Core.context())
    }

    /**
     * Guard 壳自检（依据反汇编：壳的 BaseSpiderGuard 构造时调用 Init.getSpider(名字) 取回解密后的真实 spider）。
     * 返回 null 就说明 native 解密链失败（.so 未加载 / 加密体损坏）——
     * 此时壳对象虽然能 new 出来，但所有方法都返回空，必须明确报错，不能静默成"没有返回内容"。
     */
    private fun probeGuard(loader: ClassLoader, name: String) {
        val init = runCatching { loader.loadClass("com.github.catvod.spider.Init") }.getOrNull() ?: return
        val method = runCatching { init.getMethod("getSpider", String::class.java) }.getOrNull() ?: return
        // 重要：壳传入的是 getClass().getName()，即「全类名」，不是短名
        // （依据 BaseSpiderGuard.<init> 反汇编：getClass → Class.getName → Init.getSpider）。
        // 这里只记录、绝不抛出：spider 能不能用必须由真实行为决定，不能由自检判死。
        val full = "com.github.catvod.spider.$name"
        val real = runCatching { method.invoke(null, full) }.getOrNull()
        if (real == null) {
            Diag.warn("Guard", "自检：Init.getSpider(\"$full\") 返回空（可能解密失败），仍继续尝试真实类")
        } else {
            Diag.log("Guard", "自检通过：getSpider(\"$full\") → ${real.javaClass.name}")
        }
    }

    private fun invokeProxy(key: String, loader: ClassLoader) {
        runCatching {
            val clz = loader.loadClass("com.github.catvod.spider.Proxy")
            proxies[key] = clz.getMethod("proxy", Map::class.java)
            recent = key
        }
    }

    /** 最近一次使用的 jar（对应参考项目 BaseLoader 的 setRecent）。 */
    @Volatile
    private var recent: String? = null

    /** 本地代理路由（spider 里 do=jar 的请求）。 */
    @JvmStatic
    fun proxy(key: String, params: Map<String, String>): Array<Any>? {
        val method = proxies[key] ?: recent?.let { proxies[it] } ?: proxies.values.firstOrNull() ?: return null
        return runCatching { method.invoke(null, params) as? Array<Any> }.getOrNull()
    }

    /** 本地代理服务回调入口（对齐参考项目 BaseLoader.proxy(params)：用"最近使用的 jar"）。 */
    @JvmStatic
    fun proxy(params: Map<String, String>): Array<Any>? {
        val method = recent?.let { proxies[it] } ?: proxies.values.firstOrNull() ?: return null
        return runCatching { method.invoke(null, params) as? Array<Any> }.getOrNull()
    }

    @JvmStatic
    fun parseJar(jar: String?): File? {
        if (jar.isNullOrBlank()) return null
        return runCatching {
            val url = jar.substringBefore(";").trim()
            val md5 = Regex("md5[;:=]?([0-9a-zA-Z]{32})", RegexOption.IGNORE_CASE).find(jar)?.groupValues?.getOrNull(1)
            val file = when {
                url.startsWith("http", true) -> {
                    val target = Path.jar(url)
                    if (!target.exists() || (!md5.isNullOrEmpty() && Util.md5(target.readBytes()) != md5)) {
                        target.writeBytes(OkHttp.bytes(url))
                    }
                    target
                }
                url.startsWith("file://") -> File(url.removePrefix("file://"))
                url.startsWith("assets://") -> copyAsset(url.removePrefix("assets://"), Path.jar(url))
                else -> File(url)
            }
            file.takeIf { it.exists() }
        }.getOrNull()
    }

    private fun copyAsset(name: String, target: File): File {
        if (!target.exists()) {
            Core.context().assets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        return target
    }

    @JvmStatic
    fun clear() {
        spiders.values.forEach { runCatching { it.destroy() } }
        spiders.clear()
        loaders.clear()
        proxies.clear()
        locks.clear()
        inited.clear()
        recent = null
    }
}
