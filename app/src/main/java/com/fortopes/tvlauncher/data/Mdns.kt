package com.fortopes.tvlauncher.data

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Looks up `name.local` addresses (like cunn-ha.local) with multicast DNS. Android's normal lookup often
 * can't, so a `.local` address that fails is asked for on the network directly, and the answer is
 * remembered for 10 minutes.
 */
object Mdns {
    private val cache = ConcurrentHashMap<String, Pair<String, Long>>()
    private const val TTL_MS = 10 * 60_000L

    /** [url] with a `.local` host swapped for its IP address when Android can't look it up itself. */
    fun rewrite(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val host = uri.host ?: return url
        if (!host.endsWith(".local", ignoreCase = true)) return url
        val ip = resolve(host) ?: return url
        return URI(uri.scheme, uri.userInfo, ip, uri.port, uri.path, uri.query, uri.fragment).toString()
    }

    fun resolve(host: String): String? {
        cache[host]?.let { (ip, at) -> if (System.currentTimeMillis() - at < TTL_MS) return ip }
        // Android sometimes manages it after all; that's quicker.
        val ip = runCatching { InetAddress.getByName(host).hostAddress }.getOrNull() ?: query(host) ?: return null
        cache[host] = ip to System.currentTimeMillis()
        return ip
    }

    private fun query(host: String, timeoutMs: Int = 2000): String? = runCatching {
        MulticastSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val group = InetAddress.getByName("224.0.0.251")
            val q = packet(host)
            socket.send(DatagramPacket(q, q.size, group, 5353))
            val buf = ByteArray(1500)
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                val p = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(p)
                } catch (e: SocketTimeoutException) {
                    break
                }
                answer(buf, p.length, host)?.let { return@use it }
            }
            null
        }
    }.getOrNull()

    /** A one-question query for the A record, asking for a direct (unicast) reply. */
    private fun packet(host: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0)) // id, flags, 1 question
        host.trimEnd('.').split('.').forEach { label ->
            val bytes = label.toByteArray()
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.write(byteArrayOf(0, 1, 0x80.toByte(), 1)) // type A, class IN with the unicast-reply bit
        return out.toByteArray()
    }

    private fun answer(b: ByteArray, len: Int, host: String): String? {
        fun u16(i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
        fun readName(start: Int): Pair<String, Int> {
            val labels = ArrayList<String>()
            var i = start
            var next = -1
            var hops = 0
            while (i < len && hops < 20) {
                val n = b[i].toInt() and 0xff
                when {
                    n == 0 -> { i++; break }
                    n and 0xc0 == 0xc0 -> { if (next < 0) next = i + 2; i = u16(i) and 0x3fff; hops++ }
                    else -> { labels += String(b, i + 1, n); i += n + 1 }
                }
            }
            return labels.joinToString(".") to (if (next >= 0) next else i)
        }
        if (len < 12) return null
        val questions = u16(4)
        val answers = u16(6) + u16(8) + u16(10)
        var i = 12
        repeat(questions) { i = readName(i).second + 4 }
        repeat(answers) {
            val (name, after) = readName(i)
            if (after + 10 > len) return null
            val type = u16(after)
            val rdLen = u16(after + 8)
            val data = after + 10
            if (type == 1 && rdLen == 4 && name.equals(host.trimEnd('.'), ignoreCase = true)) {
                return (0 until 4).joinToString(".") { (b[data + it].toInt() and 0xff).toString() }
            }
            i = data + rdLen
        }
        return null
    }
}
