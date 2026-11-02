package com.fortopes.tvlauncher.home

import android.content.Context
import android.net.ConnectivityManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * A tiny web page on the TV for typing things on your phone instead of with the remote. The TV shows a QR
 * code for http://<tv>:<port>/<secret>/, you fill in the form, and [onSubmit] gets the fields. Only someone
 * who can see the QR code knows the secret, and the server stops when the setup screen closes.
 */
class PhoneSetupServer(
    private val title: String,
    private val fields: List<Field>,
    private val onSubmit: (Map<String, String>) -> Result<String>,
) {
    data class Field(val name: String, val label: String, val hint: String, val secret: Boolean = false)

    private val secret = ByteArray(9).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }
    private var socket: ServerSocket? = null

    /** Starts the server and returns the address to put in the QR code, or null if the TV has no network. */
    fun start(context: Context): String? {
        val ip = lanAddress(context) ?: return null
        val server = ServerSocket(0)
        socket = server
        thread(isDaemon = true, name = "phone-setup") {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(client) } }
            }
        }
        return "http://$ip:${server.localPort}/$secret/"
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) = client.use { s ->
        s.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
        val requestLine = reader.readLine() ?: return
        val (method, path) = requestLine.split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        var length = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val (status, body) = when {
            !path.startsWith("/$secret/") -> "404 Not Found" to page("Not found", "<p>Scan the QR code on the TV again.</p>")
            method == "POST" -> {
                val chars = CharArray(length.coerceAtMost(16_000))
                var read = 0
                while (read < chars.size) {
                    val n = reader.read(chars, read, chars.size - read)
                    if (n < 0) break
                    read += n
                }
                val values = String(chars, 0, read).split('&').mapNotNull { pair ->
                    val k = pair.substringBefore('=', "")
                    if (k.isEmpty()) null else URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                }.toMap()
                onSubmit(values).fold(
                    onSuccess = { "200 OK" to page("Done", "<p>$it</p><p>You can close this page.</p>") },
                    onFailure = { "200 OK" to form(error = it.message ?: "That didn't work", values = values) },
                )
            }
            else -> "200 OK" to form(error = null, values = emptyMap())
        }
        val bytes = body.toByteArray()
        val out = s.getOutputStream()
        out.write("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun form(error: String?, values: Map<String, String>): String {
        val inputs = fields.joinToString("") { f ->
            val value = if (f.secret) "" else esc(values[f.name] ?: "")
            """<label>${esc(f.label)}<input name="${f.name}" type="${if (f.secret) "password" else "text"}" placeholder="${esc(f.hint)}" value="$value" autocapitalize="off" autocorrect="off"></label>"""
        }
        val err = error?.let { "<p class=err>${esc(it)}</p>" } ?: ""
        return page(title, "$err<form method=post>$inputs<button>Send to the TV</button></form>")
    }

    private fun page(heading: String, content: String) = """<!doctype html><html><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>TV Home</title><style>
body{font:16px system-ui,sans-serif;background:#07070b;color:#f4f4f6;margin:0;padding:24px}
h1{font-size:22px}label{display:block;margin:16px 0;color:#a1a1aa}
input{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:12px;font-size:16px;border-radius:8px;border:1px solid #3f3f46;background:#1a1b22;color:#fff}
button{width:100%;padding:14px;font-size:16px;border:0;border-radius:8px;background:linear-gradient(90deg,#8b5cf6,#3b82f6);color:#fff}
.err{color:#fca5a5}</style></head><body><h1>${esc(heading)}</h1>$content</body></html>"""

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        /** The TV's address on the home network (ethernet or Wi-Fi). */
        fun lanAddress(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }
    }
}
