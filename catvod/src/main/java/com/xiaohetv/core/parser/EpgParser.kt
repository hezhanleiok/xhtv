package com.xiaohetv.core.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

object EpgParser {

    class Programme(val start: Long, val stop: Long, val title: String)

    @JvmStatic
    fun parse(xml: String): Map<String, List<Programme>> {
        val map = LinkedHashMap<String, MutableList<Programme>>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var channel = ""
        var start = 0L
        var stop = 0L
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "programme") {
                        channel = parser.getAttributeValue(null, "channel").orEmpty()
                        start = time(parser.getAttributeValue(null, "start"))
                        stop = time(parser.getAttributeValue(null, "stop"))
                    }
                }
                XmlPullParser.TEXT -> {
                    if (parser.name == "title" && channel.isNotEmpty()) {
                        val title = parser.text?.trim().orEmpty()
                        if (title.isNotEmpty()) map.getOrPut(channel) { mutableListOf() }.add(Programme(start, stop, title))
                    }
                }
            }
            event = parser.next()
        }
        return map
    }

    @JvmStatic
    fun current(list: List<Programme>?, now: Long = System.currentTimeMillis()): Programme? =
        list?.firstOrNull { it.start <= now && it.stop >= now }

    private fun time(value: String?): Long {
        if (value.isNullOrEmpty()) return 0L
        return runCatching {
            val clean = value.substringBefore(" ").trim()
            val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val millis = format.parse(clean)?.time ?: return 0L
            val offset = value.substringAfter(" ", "").trim()
            if (offset.length >= 5) {
                val sign = if (offset.startsWith("-")) -1 else 1
                val hours = offset.substring(1, 3).toIntOrNull() ?: 0
                val minutes = offset.substring(3, 5).toIntOrNull() ?: 0
                millis - sign * (hours * 3600_000L + minutes * 60_000L)
            } else millis
        }.getOrDefault(0L)
    }
}
