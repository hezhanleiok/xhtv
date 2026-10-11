package com.xiaohetv.core.utils

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {

    @JvmStatic
    fun aesCbc(data: ByteArray, key: String, iv: String): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return cipher.doFinal(data)
    }

    @JvmStatic
    fun padEnd(text: String, size: Int = 16, pad: Char = '0'): String {
        if (text.length >= size) return text.substring(0, size)
        return text + pad.toString().repeat(size - text.length)
    }
}
