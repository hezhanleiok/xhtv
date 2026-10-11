package com.xiaohetv.core.parser

import android.util.Xml
import com.xiaohetv.core.bean.Result
import com.xiaohetv.core.bean.Vod
import com.xiaohetv.core.bean.VodClass
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * 解析苹果 CMS（MacCMS）XML 接口返回。
 */
object XmlResult {

    @JvmStatic
    fun parse(text: String): Result {
        val result = Result()
        val videos = mutableListOf<Vod>()
        val classes = mutableListOf<VodClass>()
        var vod: Vod? = null
        var clazz: VodClass? = null
        var flags = mutableListOf<String>()
        var urls = mutableListOf<String>()
        var pending = ""

        val parser = Xml.newPullParser()
        parser.setInput(StringReader(text))
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "video" -> vod = Vod()
                        "ty" -> {
                            clazz = VodClass()
                            clazz.typeId = parser.getAttributeValue(null, "id").orEmpty()
                            clazz.typeName = parser.getAttributeValue(null, "name").orEmpty()
                        }
                        "dd" -> {
                            pending = parser.getAttributeValue(null, "flag").orEmpty()
                        }
                        "list" -> {
                            result.pagecount = parser.getAttributeValue(null, "pagecount")?.toIntOrNull() ?: 1
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text?.trim().orEmpty()
                    if (value.isEmpty()) Unit else when (parser.name) {
                        "id" -> vod?.vodId = value
                        "name" -> if (vod != null) vod?.vodName = value else clazz?.typeName = value
                        "type" -> vod?.typeName = value
                        "pic" -> vod?.vodPic = value
                        "note" -> vod?.vodRemarks = value
                        "state" -> if (vod?.vodRemarks.isNullOrEmpty()) vod?.vodRemarks = value
                        "year" -> vod?.vodYear = value
                        "area" -> vod?.vodArea = value
                        "actor" -> vod?.vodActor = value
                        "director" -> vod?.vodDirector = value
                        "des" -> vod?.vodContent = value
                        "dd" -> {
                            flags.add(pending)
                            urls.add(value)
                        }
                        "ty" -> if (clazz?.typeName.isNullOrEmpty()) clazz?.typeName = value
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "video" -> {
                            vod?.let {
                                it.vodPlayFrom = flags.joinToString("$$$")
                                it.vodPlayUrl = urls.joinToString("$$$")
                                videos.add(it)
                            }
                            vod = null
                            flags = mutableListOf()
                            urls = mutableListOf()
                        }
                        "ty" -> {
                            clazz?.let { if (it.typeId.isNotEmpty() || it.typeName.isNotEmpty()) classes.add(it) }
                            clazz = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        result.list = videos
        result.types = classes
        return result
    }
}
