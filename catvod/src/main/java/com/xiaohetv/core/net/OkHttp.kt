package com.xiaohetv.core.net

import com.xiaohetv.core.bean.Doh
import com.xiaohetv.core.bean.Header
import com.xiaohetv.core.bean.Proxy
import com.xiaohetv.core.utils.Util
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object OkHttp {

    private val dns = OkDns()
    private val selector = OkProxySelector()
    private val headerStore = HeaderStore()

    @Volatile
    private var instance: OkHttpClient? = null

    private val trustAll: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val socketFactory: javax.net.ssl.SSLSocketFactory by lazy {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        context.socketFactory
    }

    @JvmStatic
    fun dns(): OkDns = dns

    @JvmStatic
    fun selector(): OkProxySelector = selector

    @JvmStatic
    @Synchronized
    fun client(): OkHttpClient {
        instance?.let { return it }
        val built = builder(15_000L).build()
        instance = built
        return built
    }

    @JvmStatic
    fun client(timeoutMillis: Long): OkHttpClient = builder(timeoutMillis).build()

    private fun builder(timeout: Long): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(timeout, TimeUnit.MILLISECONDS)
        .readTimeout(timeout, TimeUnit.MILLISECONDS)
        .writeTimeout(timeout, TimeUnit.MILLISECONDS)
        .dns(dns)
        .proxySelector(selector)
        .sslSocketFactory(socketFactory, trustAll)
        .hostnameVerifier { _, _ -> true }
        .addInterceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            val extra = headerStore.find(host)
            if (extra.isEmpty()) {
                chain.proceed(request)
            } else {
                val builder = request.newBuilder()
                if (request.header("User-Agent").isNullOrEmpty()) builder.header("User-Agent", Util.CHROME)
                extra.forEach { (k, v) -> builder.header(k, v) }
                chain.proceed(builder.build())
            }
        }

    @JvmStatic
    fun setHeaders(list: List<Header>) {
        headerStore.set(list)
    }

    @JvmStatic
    fun setProxy(list: List<Proxy>) {
        selector.set(list)
    }

    @JvmStatic
    fun setHosts(list: List<String>) {
        dns.setHosts(list)
    }

    @JvmStatic
    fun setDoh(list: List<Doh>) {
        dns.setDoh(list.firstOrNull())
    }

    /** 带最终跳转地址的响应。 */
    class NetResult(val url: String, val bytes: ByteArray) {
        fun text(): String = String(bytes, Charsets.UTF_8)
    }

    private fun buildRequest(url: String, headers: Map<String, String>, userAgent: String?): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (k, v) -> if (v.isNotEmpty()) builder.header(k, v) }
        val hasUa = headers.keys.any { it.equals("User-Agent", true) }
        if (!hasUa && userAgent != null) builder.header("User-Agent", userAgent)
        return builder.build()
    }

    @JvmStatic
    @Throws(Exception::class)
    fun request(url: String, headers: Map<String, String> = emptyMap(), userAgent: String? = Util.OKHTTP): NetResult {
        client().newCall(buildRequest(url, headers, userAgent)).execute().use { response ->
            val finalUrl = response.request.url.toString()
            if (!response.isSuccessful) {
                throw IllegalStateException("服务器返回 HTTP ${response.code}（${response.request.url.host}）")
            }
            return NetResult(finalUrl, response.body?.bytes() ?: ByteArray(0))
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun string(url: String, headers: Map<String, String> = emptyMap(), userAgent: String? = Util.OKHTTP): String =
        request(url, headers, userAgent).text()

    @JvmStatic
    @Throws(Exception::class)
    fun bytes(url: String, headers: Map<String, String> = emptyMap(), userAgent: String? = Util.OKHTTP): ByteArray =
        request(url, headers, userAgent).bytes
}
