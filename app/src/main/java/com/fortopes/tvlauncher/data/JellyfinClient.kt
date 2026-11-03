package com.fortopes.tvlauncher.data

import android.content.Context
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

/** A Jellyfin server found on the network. */
data class JellyfinServer(val name: String, val url: String)

/** Jellyfin sign-in, saved on the TV. */
class JellyfinStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("jellyfin", Context.MODE_PRIVATE)

    var url: String? by stringPref("url")
    var token: String? by stringPref("token")
    var userId: String? by stringPref("user_id")
    var userName: String? by stringPref("user_name")
    var serverName: String? by stringPref("server_name")

    val deviceId: String
        get() = prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }

    val isSignedIn: Boolean get() = !url.isNullOrBlank() && !token.isNullOrBlank() && !userId.isNullOrBlank()

    fun signOut() = prefs.edit().remove("token").remove("user_id").remove("user_name").apply()

    private fun stringPref(key: String) = object : kotlin.properties.ReadWriteProperty<Any?, String?> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String? = prefs.getString(key, null)
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String?) {
            prefs.edit().putString(key, value).apply()
        }
    }
}

/**
 * Jellyfin's API: finding the server, signing in with Quick Connect (a code you approve in the Jellyfin
 * app on your phone, so nothing is typed on the TV), the resume list, and starting playback on the TV.
 */
class JellyfinClient(private val store: JellyfinStore) {

    private fun authHeader(token: String? = store.token): Map<String, String> {
        val value = buildString {
            append("MediaBrowser Client=\"Fortopes Home\", Device=\"Google TV\", DeviceId=\"${store.deviceId}\", Version=\"1.0\"")
            if (!token.isNullOrBlank()) append(", Token=\"$token\"")
        }
        // Newer servers read Authorization, older ones X-Emby-Authorization.
        return mapOf("Authorization" to value, "X-Emby-Authorization" to value)
    }

    /** Asks the network "who is a Jellyfin server?" the way Jellyfin's own apps do (UDP port 7359). */
    fun discover(waitMs: Int = 2500): List<JellyfinServer> {
        val found = LinkedHashMap<String, JellyfinServer>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = waitMs
                val msg = "Who is JellyfinServer?".toByteArray()
                socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), 7359))
                val buf = ByteArray(4096)
                val end = System.currentTimeMillis() + waitMs
                while (System.currentTimeMillis() < end) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    runCatching {
                        val json = JSONObject(String(packet.data, 0, packet.length))
                        val url = json.optString("Address").trimEnd('/')
                        if (url.isNotBlank()) found[url] = JellyfinServer(json.optString("Name", url), url)
                    }
                }
            }
        }
        return found.values.toList()
    }

    /** Checks an address is a Jellyfin server; returns its name. Adds http:// and :8096 when left off. */
    fun checkServer(input: String): JellyfinServer {
        val url = normaliseUrl(input)
        val info = Http.obj(Http.request("GET", "$url/System/Info/Public"))
        return JellyfinServer(info.optString("ServerName", url), url)
    }

    data class QuickConnect(val code: String, val secret: String)

    fun startQuickConnect(url: String): QuickConnect {
        val text = try {
            Http.request("POST", "$url/QuickConnect/Initiate", authHeader(token = null))
        } catch (e: HttpException) {
            if (e.code == 401 || e.code == 403) throw IllegalStateException("Quick Connect is turned off on this server.")
            Http.request("GET", "$url/QuickConnect/Initiate", authHeader(token = null)) // Jellyfin 10.8
        }
        val json = Http.obj(text)
        return QuickConnect(code = json.getString("Code"), secret = json.getString("Secret"))
    }

    /** True once you've entered the code in Jellyfin on your phone. */
    fun isQuickConnectApproved(url: String, secret: String): Boolean {
        val json = Http.obj(Http.request("GET", "$url/QuickConnect/Connect?Secret=${enc(secret)}", authHeader(token = null)))
        return json.optBoolean("Authenticated", false)
    }

    /** Finishes Quick Connect and saves the sign-in. */
    fun finishQuickConnect(server: JellyfinServer, secret: String) {
        val body = JSONObject().put("Secret", secret).toString()
        val json = Http.obj(Http.request("POST", "${server.url}/Users/AuthenticateWithQuickConnect", authHeader(token = null), body))
        val user = json.getJSONObject("User")
        store.url = server.url
        store.serverName = server.name
        store.token = json.getString("AccessToken")
        store.userId = user.getString("Id")
        store.userName = user.optString("Name")
    }

    /** What you're part-way through, most recent first. */
    fun resume(limit: Int = 12): List<ContinueItem> {
        if (!store.isSignedIn) return emptyList()
        val url = store.url!!
        val uid = store.userId!!
        val q = "Limit=$limit&MediaTypes=Video&EnableImageTypes=Thumb,Backdrop,Primary&ImageTypeLimit=1&Fields=UserData"
        val text = try {
            Http.request("GET", "$url/UserItems/Resume?userId=$uid&$q", authHeader())
        } catch (e: HttpException) {
            if (e.code == 401) throw e
            Http.request("GET", "$url/Users/$uid/Items/Resume?$q", authHeader()) // before Jellyfin 10.9
        }
        val items = Http.obj(text).optJSONArray("Items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val it = items.getJSONObject(i)
            val id = it.getString("Id")
            val series = it.optString("SeriesName").takeIf { s -> s.isNotBlank() }
            val name = it.optString("Name")
            val season = it.optInt("ParentIndexNumber", -1)
            val episode = it.optInt("IndexNumber", -1)
            val userData = it.optJSONObject("UserData")
            val subtitle = if (series != null) {
                (if (season >= 0 && episode >= 0) "S$season E$episode · " else "") + name
            } else {
                it.optInt("ProductionYear", 0).takeIf { y -> y > 0 }?.toString() ?: ""
            }
            ContinueItem(
                key = "jellyfin:$id",
                title = series ?: name,
                subtitle = subtitle,
                imageUrl = imageUrl(url, it),
                progress = userData?.optDouble("PlayedPercentage")?.takeIf { p -> !p.isNaN() }?.let { p -> (p / 100).toFloat() },
                sourceLabel = "Jellyfin",
                sourcePackage = JELLYFIN_PACKAGE,
                lastWatched = userData?.optString("LastPlayedDate")?.let { d -> runCatching { Instant.parse(d).toEpochMilli() }.getOrNull() } ?: 0L,
                action = ContinueItem.Action.Jellyfin(id, userData?.optLong("PlaybackPositionTicks") ?: 0L),
            )
        }
    }

    /**
     * Starts an item on the Jellyfin app on this TV: call after opening the app. Waits for the app to show up
     * as a remote-controllable session, then tells it to play. Returns false if it never showed up.
     */
    fun playOnTv(itemId: String, startTicks: Long, since: Long, waitMs: Long = 20_000): Boolean {
        if (!store.isSignedIn) return false
        val url = store.url!!
        val end = System.currentTimeMillis() + waitMs
        while (System.currentTimeMillis() < end) {
            val sessions = runCatching {
                Http.arr(Http.request("GET", "$url/Sessions?controllableByUserId=${store.userId}", authHeader()))
            }.getOrNull()
            if (sessions != null) {
                val tv = (0 until sessions.length())
                    .map { sessions.getJSONObject(it) }
                    .filter { s ->
                        s.optBoolean("SupportsRemoteControl") &&
                            s.optString("DeviceId") != store.deviceId &&
                            Regex("android ?tv|google ?tv", RegexOption.IGNORE_CASE)
                                .containsMatchIn("${s.optString("Client")} ${s.optString("DeviceName")}") &&
                            (runCatching { Instant.parse(s.optString("LastActivityDate")).toEpochMilli() }.getOrDefault(0L) >= since - 5000)
                    }
                    .maxByOrNull { it.optString("LastActivityDate") }
                if (tv != null) {
                    Http.request(
                        "POST",
                        "$url/Sessions/${tv.getString("Id")}/Playing?playCommand=PlayNow&itemIds=$itemId&startPositionTicks=$startTicks",
                        authHeader(),
                    )
                    return true
                }
            }
            Thread.sleep(1000)
        }
        return false
    }

    private fun imageUrl(base: String, it: JSONObject): String {
        fun at(id: String, kind: String) = "$base/Items/$id/Images/$kind?maxWidth=480&quality=85"
        val id = it.getString("Id")
        return when {
            it.optJSONObject("ImageTags")?.has("Thumb") == true -> at(id, "Thumb")
            it.has("ParentThumbItemId") -> at(it.getString("ParentThumbItemId"), "Thumb")
            (it.optJSONArray("BackdropImageTags")?.length() ?: 0) > 0 -> at(id, "Backdrop")
            it.has("ParentBackdropItemId") -> at(it.getString("ParentBackdropItemId"), "Backdrop")
            else -> at(id, "Primary")
        }
    }

    companion object {
        const val JELLYFIN_PACKAGE = "org.jellyfin.androidtv"

        fun normaliseUrl(input: String): String {
            var url = input.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            // No port given on plain http: Jellyfin's default is 8096.
            if (url.startsWith("http://") && !Regex("^http://[^/]+:\\d+").containsMatchIn(url)) {
                val rest = url.removePrefix("http://")
                val host = rest.substringBefore('/')
                val path = rest.removePrefix(host)
                url = "http://$host:8096$path"
            }
            return url
        }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}
