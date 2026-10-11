package com.xiaohetv.core.utils

import android.util.Base64
import java.security.MessageDigest
import java.util.Locale

object Util {

    const val CHROME =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** TVBox 生态通用 UA：大量配置分发服务器只对 okhttp 开头的 UA 返回配置。 */
    const val OKHTTP = "okhttp/4.12.0"

    /** 配置请求的 UA 尝试顺序。 */
    @JvmField
    val USER_AGENTS: List<String?> = listOf(OKHTTP, "okhttp/3.12.0", null, CHROME)

    @JvmStatic
    fun md5(text: String): String = md5(text.toByteArray())

    @JvmStatic
    fun md5(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append("%02x".format(b))
        return sb.toString()
    }

    @JvmStatic
    fun base64(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    @JvmStatic
    fun base64Decode(text: String): String = runCatching {
        String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
    }.getOrDefault("")

    @JvmStatic
    fun hex2byte(text: String): ByteArray {
        val data = text.toCharArray()
        val out = ByteArray(data.size / 2)
        var i = 0
        while (i < data.size - 1) {
            out[i / 2] = ((Character.digit(data[i], 16) shl 4) + Character.digit(data[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }

    @JvmStatic
    fun containOrMatch(pattern: String, target: String): Boolean {
        if (pattern.isEmpty()) return false
        if (pattern.contains("*")) {
            val regex = pattern.replace(".", "\\.").replace("*", ".*")
            return target.matches(Regex(regex))
        }
        return target.contains(pattern)
    }

    @JvmStatic
    fun host(url: String?): String = runCatching {
        val clean = url.orEmpty().substringAfter("://")
        clean.substringBefore("/").substringBefore(":")
    }.getOrDefault("")

    @JvmStatic
    fun lower(text: String?): String = text.orEmpty().lowercase(Locale.getDefault())
}
