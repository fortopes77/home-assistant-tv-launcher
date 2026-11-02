package com.fortopes.tvlauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int, message: String) : Exception(message)

/** A tiny JSON-over-HTTP helper; enough for Jellyfin and Home Assistant without another library. */
internal object Http {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = 6000,
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray()) }
            } else if (method == "POST") {
                conn.setFixedLengthStreamingMode(0)
            }
            val code = conn.responseCode
            if (code !in 200..299) throw HttpException(code, "$method $url answered $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun obj(text: String): JSONObject = if (text.isBlank()) JSONObject() else JSONObject(text)
    fun arr(text: String): JSONArray = if (text.isBlank()) JSONArray() else JSONArray(text)
}
