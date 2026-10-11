package com.xiaohetv.core.spider

import android.content.Context
import com.github.catvod.crawler.Spider
import com.xiaohetv.core.bean.Result
import com.xiaohetv.core.net.OkHttp
import com.xiaohetv.core.parser.XmlResult
import com.xiaohetv.core.utils.Json
import java.net.URLEncoder

/**
 * 苹果 CMS 兼容接口：type 0 = XML，type 1 / 4 = JSON。
 */
class CmsSpider(private val api: String, private val type: Int) : Spider() {

    private var headers: Map<String, String> = emptyMap()
    private var params: LinkedHashMap<String, String> = LinkedHashMap()

    override fun init(context: Context, extend: String) {
        if (extend.isEmpty()) return
        val map = runCatching { Json.toMap(Json.parse(extend)) }.getOrDefault(emptyMap())
        if (map.isNotEmpty()) {
            params.putAll(map)
            return
        }
        extend.split("&").forEach { item ->
            val index = item.indexOf('=')
            if (index > 0) params[item.substring(0, index)] = item.substring(index + 1)
        }
    }

    fun setHeader(header: Map<String, String>) {
        headers = header
    }

    override fun homeContent(filter: Boolean): String {
        val result = call("ac=videolist")
        if (filter && result.types.isEmpty()) {
            val classes = runCatching { call("ac=list") }.getOrNull()
            classes?.let { result.types = it.types }
        }
        return Json.gson.toJson(result)
    }

    override fun categoryContent(tid: String, pg: String, filter: Boolean, extend: HashMap<String, String>): String {
        val query = StringBuilder("ac=videolist&t=$tid&pg=$pg")
        params.forEach { (k, v) -> query.append("&").append(k).append("=").append(v) }
        extend.forEach { (k, v) -> query.append("&").append(k).append("=").append(v) }
        return Json.gson.toJson(call(query.toString()))
    }

    override fun detailContent(ids: List<String>): String {
        val id = ids.firstOrNull() ?: return Json.gson.toJson(Result.empty())
        var result = call("ac=videolist&ids=$id")
        if (result.listSafe().isEmpty()) result = call("ac=detail&ids=$id")
        return Json.gson.toJson(result)
    }

    override fun searchContent(key: String, quick: Boolean): String = searchContent(key, quick, "1")

    override fun searchContent(key: String, quick: Boolean, pg: String): String {
        val wd = URLEncoder.encode(key, "UTF-8")
        return Json.gson.toJson(call("ac=videolist&wd=$wd&pg=$pg"))
    }

    override fun playerContent(flag: String, id: String, vipFlags: List<String>): String {
        val result = Result()
        result.urlElement = Json.primitive(id)
        result.playUrl = ""
        result.flag = flag
        result.headerElement = Json.gson.toJsonTree(headers)
        return Json.gson.toJson(result)
    }

    private fun call(query: String): Result {
        val url = if (api.contains("?")) "$api&$query" else "$api?$query"
        val text = OkHttp.string(url, headers)
        return if (type == 0) XmlResult.parse(text) else Result.fromJson(text)
    }
}
