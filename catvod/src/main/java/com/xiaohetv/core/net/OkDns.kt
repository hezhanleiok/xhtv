package com.xiaohetv.core.net

import com.xiaohetv.core.bean.Doh
import com.xiaohetv.core.utils.Util
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class OkDns : Dns {

    private val hosts = LinkedHashMap<String, String>()
    private var doh: Doh? = null

    private val plain: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun setHosts(list: List<String>) {
        hosts.clear()
        list.forEach { item ->
            val index = item.indexOf('=')
            if (index > 0) hosts[item.substring(0, index).trim()] = item.substring(index + 1).trim()
        }
    }

    @Synchronized
    fun setDoh(item: Doh?) {
        doh = item
    }

    override fun lookup(hostname: String): List<InetAddress> {
        hosts.forEach { (pattern, target) ->
            if (Util.containOrMatch(pattern, hostname)) {
                return runCatching { InetAddress.getAllByName(target).toList() }.getOrNull() ?: emptyList()
            }
        }
        doh?.let { item ->
            val ips = query(item, hostname)
            if (ips.isNotEmpty()) return ips
        }
        return Dns.SYSTEM.lookup(hostname)
    }

    private fun query(item: Doh, hostname: String): List<InetAddress> {
        if (item.url.isEmpty()) return emptyList()
        return runCatching {
            val url = if (item.url.contains("?")) "${item.url}&name=$hostname&type=A" else "${item.url}?name=$hostname&type=A"
            val request = Request.Builder().url(url).header("Accept", "application/dns-json").build()
            plain.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val text = response.body?.string().orEmpty()
                val result = mutableListOf<InetAddress>()
                val regex = Regex("\"data\"\\s*:\\s*\"([0-9.]+)\"")
                regex.findAll(text).forEach { match ->
                    runCatching { result.add(InetAddress.getByName(match.groupValues[1])) }
                }
                result
            }
        }.getOrDefault(emptyList())
    }
}
