package com.xiaohetv.core.net

import com.xiaohetv.core.bean.Proxy
import com.xiaohetv.core.utils.Util
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

class OkProxySelector : ProxySelector() {

    private val rules = mutableListOf<Proxy>()

    @Synchronized
    fun set(list: List<Proxy>) {
        rules.clear()
        rules.addAll(list)
    }

    override fun select(uri: URI?): List<java.net.Proxy> {
        val host = uri?.host ?: return listOf(java.net.Proxy.NO_PROXY)
        val rule = rules.firstOrNull { item -> item.hosts.any { Util.containOrMatch(it, host) } }
            ?: return listOf(java.net.Proxy.NO_PROXY)
        val proxy = rule.resolve() ?: return listOf(java.net.Proxy.NO_PROXY)
        return listOf(proxy)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}

    companion object {
        fun toProxy(url: String): java.net.Proxy? = runCatching {
            val uri = URI.create(url)
            val type = if (url.startsWith("socks", true)) java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
            java.net.Proxy(type, InetSocketAddress(uri.host, uri.port))
        }.getOrNull()
    }
}

class HeaderStore {

    private val list = mutableListOf<com.xiaohetv.core.bean.Header>()

    @Synchronized
    fun set(items: List<com.xiaohetv.core.bean.Header>) {
        list.clear()
        list.addAll(items)
    }

    @Synchronized
    fun find(host: String): Map<String, String> {
        val rule = list.firstOrNull { item -> Util.containOrMatch(item.host, host) } ?: return emptyMap()
        return rule.headerMap
    }
}
