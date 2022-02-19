package com.github.sukhinin.proxier.http

import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object HttpUtils {

    private fun urlEncode(data: String): String {
        return URLEncoder.encode(data, StandardCharsets.UTF_8)
    }

    private fun urlDecode(data: String): String {
        return URLDecoder.decode(data, StandardCharsets.UTF_8)
    }

    fun formEncode(data: Map<String, String>): String {
        return data.asSequence()
            .map { (key, value) -> urlEncode(key) + "=" + urlEncode(value) }
            .joinToString("&")
    }

    fun formDecode(data: String): Map<String, String> {
        return data.trimStart('?').splitToSequence('&')
            .associate { urlDecode(it.substringBefore('=')) to urlDecode(it.substringAfter('=')) }
    }

    fun urlResolve(context: String, spec: String): String {
        return URL(URL(context), spec).toString()
    }
}
