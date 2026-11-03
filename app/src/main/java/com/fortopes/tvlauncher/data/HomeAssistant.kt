package com.fortopes.tvlauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Home Assistant sign-in (address and long-lived token), saved on the TV. */
class HaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("home_assistant", Context.MODE_PRIVATE)

    var url: String?
        get() = prefs.getString("url", null)
        set(value) = prefs.edit().putString("url", value).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    /** The room the TV is in (a Home Assistant area name). Its lights and blinds show first. */
    var room: String?
        get() = prefs.getString("room", null)
        set(value) = prefs.edit().putString("room", value).apply()

    val isSignedIn: Boolean get() = !url.isNullOrBlank() && !token.isNullOrBlank()

    /** The doorbell (or person sensor) that brings up the camera pop-up. Null means the pop-up is off. */
    var doorbellSensor: String?
        get() = prefs.getString("doorbell_sensor", null)
        set(value) = prefs.edit().putString("doorbell_sensor", value).apply()

    /** The camera the pop-up shows. Null picks the one that goes with the sensor. */
    var doorbellCamera: String?
        get() = prefs.getString("doorbell_camera", null)
        set(value) = prefs.edit().putString("doorbell_camera", value).apply()

    fun signOut() = prefs.edit().remove("token").apply()
}

/** One Home Assistant entity's current state. */
data class HaEntity(
    val id: String,
    val state: String,
    val name: String,
    val attributes: JSONObject,
) {
    val domain: String get() = id.substringBefore('.')
    val isOn: Boolean get() = state == "on" || state == "playing" || state == "open" || state == "opening"

    /** A blind, curtain or shade, which the Home row offers next to lights. Garage doors and gates are left out. */
    val isBlind: Boolean get() = domain == "cover" &&
        attributes.optString("device_class", "") in setOf("", "blind", "curtain", "shade", "shutter", "window", "awning")
    val unavailable: Boolean get() = state == "unavailable" || state == "unknown"
}

/** Home Assistant's REST API, with a long-lived token. */
class HaClient(private val store: HaStore) {

    private fun headers(token: String? = store.token) = mapOf("Authorization" to "Bearer $token")

    private val base: String get() = store.url ?: error("Home Assistant isn't set up")

    /** Checks an address and token work. Returns Home Assistant's location name. */
    fun check(url: String, token: String): String {
        val clean = normaliseUrl(url)
        val config = Http.obj(Http.request("GET", "$clean/api/config", headers(token)))
        return config.optString("location_name", "Home Assistant")
    }

    fun states(): List<HaEntity> {
        val arr = Http.arr(Http.request("GET", "$base/api/states", headers(), timeoutMs = 10_000))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val attrs = o.optJSONObject("attributes") ?: JSONObject()
            HaEntity(
                id = o.getString("entity_id"),
                state = o.optString("state"),
                name = attrs.optString("friendly_name").ifBlank { o.getString("entity_id") },
                attributes = attrs,
            )
        }
    }

    fun call(domain: String, service: String, data: JSONObject = JSONObject()) {
        Http.request("POST", "$base/api/services/$domain/$service", headers(), data.toString())
    }

    /** Renders a Jinja template on the server, e.g. to find the entities in an area. */
    fun template(template: String): String =
        Http.request("POST", "$base/api/template", headers(), JSONObject().put("template", template).toString())

    /** Entity ids in an area, by the area's name or id. */
    fun areaEntities(area: String): List<String> = runCatching {
        val text = template("{{ area_entities(${JSONObject.quote(area)}) | tojson }}")
        val arr = JSONArray(text)
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    /** All area names, for the room picker. */
    fun areas(): List<String> = runCatching {
        val arr = JSONArray(template("{{ areas() | map('area_name') | list | tojson }}"))
        (0 until arr.length()).map { arr.getString(it) }.sorted()
    }.getOrDefault(emptyList())

    /** A camera's current picture as JPEG bytes, scaled down by Home Assistant to [width] when given. */
    fun cameraImage(entityId: String, width: Int? = null): ByteArray {
        val size = if (width != null) "&width=$width" else ""
        val conn = java.net.URL(Mdns.rewrite("$base/api/camera_proxy/$entityId?t=${System.currentTimeMillis()}$size")).openConnection() as java.net.HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Authorization", "Bearer ${store.token}")
            if (conn.responseCode !in 200..299) throw HttpException(conn.responseCode, "camera answered ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** The dashboard's TV screen, for the full dashboard card. */
    fun dashboardUrl(): String = Mdns.rewrite("$base/local/dashboard/index.html?tv&open=intent&token=${store.token}")

    companion object {
        fun normaliseUrl(input: String): String {
            var url = input.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            if (url.startsWith("http://") && !Regex("^http://[^/]+:\\d+").containsMatchIn(url)) {
                val host = url.removePrefix("http://").substringBefore('/')
                url = "http://$host:8123"
            }
            return url
        }
    }
}

/** What the Home row shows, picked from Home Assistant's states. */
data class HomeState(
    val weather: String?,
    val cameras: List<HaEntity>,
    val actions: List<HaEntity>,
    /** Lights and blinds in the TV's room, shown first. */
    val roomControls: List<HaEntity>,
    /** The rest of the house's lights and blinds, after the scenes. */
    val otherControls: List<HaEntity>,
    val nowPlaying: HaEntity?,
    val error: String? = null,
)

class HomeRepository(private val store: HaStore, private val client: HaClient) {
    /** Area membership doesn't change often, so ask once per room. */
    private var roomEntities: Pair<String, Set<String>>? = null

    fun load(): HomeState? {
        if (!store.isSignedIn) return null
        val states = try {
            client.states()
        } catch (e: Exception) {
            return failed(e)
        }
        return build(states)
    }

    /** The Home row when Home Assistant can't be reached. */
    fun failed(e: Throwable): HomeState {
        val msg = if (e is HttpException && e.code == 401) "Home Assistant token stopped working" else "Couldn't reach Home Assistant"
        return HomeState(null, emptyList(), emptyList(), emptyList(), emptyList(), null, msg)
    }

    /** Picks the Home row out of [states] (from a full download, or the live connection). */
    fun build(states: List<HaEntity>): HomeState? {
        if (!store.isSignedIn) return null
        val room = store.room
        val inRoom: Set<String> = when {
            room == null -> emptySet()
            roomEntities?.first == room -> roomEntities!!.second
            else -> client.areaEntities(room).toSet().also { roomEntities = room to it }
        }

        val weather = states.firstOrNull { it.domain == "weather" && !it.unavailable }?.let { w ->
            val t = w.attributes.optDouble("temperature")
            val unit = w.attributes.optString("temperature_unit", "°")
            val temp = if (t.isNaN()) "" else "${Math.round(t)}${if (unit.startsWith("°")) "°" else unit} "
            temp + w.state.replace('-', ' ').replaceFirstChar { it.uppercase() }
        }
        // The doorbell or front door first, since that's the one you'd check from the couch.
        val cameras = states.filter { it.domain == "camera" && !it.unavailable }
            .sortedByDescending { c -> listOf("doorbell", "front door", "front").indexOfFirst { c.name.contains(it, true) }.let { if (it < 0) -1 else 10 - it } }
        // Scenes first, then scripts.
        val actions = states.filter { (it.domain == "script" || it.domain == "scene") && !it.unavailable }
            .sortedWith(compareBy<HaEntity> { if (it.domain == "scene") 0 else 1 }.thenBy { it.name.lowercase() })
            .take(16)
        // Lights, then blinds, by name.
        val controls = states.filter { (it.domain == "light" || it.isBlind) && !it.unavailable }
            .sortedWith(compareBy<HaEntity> { if (it.domain == "light") 0 else 1 }.thenBy { it.name.lowercase() })
        val (roomControls, otherControls) = controls.partition { it.id in inRoom }
        val players = states.filter { it.domain == "media_player" && (it.state == "playing" || it.state == "paused") }
        val nowPlaying = players.firstOrNull { it.id in inRoom && it.state == "playing" }
            ?: players.firstOrNull { it.state == "playing" }
            ?: players.firstOrNull()
        return HomeState(weather, cameras, actions, roomControls, otherControls, nowPlaying)
    }

    fun run(entity: HaEntity) = when (entity.domain) {
        "script" -> client.call("script", "turn_on", JSONObject().put("entity_id", entity.id))
        "scene" -> client.call("scene", "turn_on", JSONObject().put("entity_id", entity.id))
        "light" -> client.call("light", "toggle", JSONObject().put("entity_id", entity.id))
        "cover" -> client.call("cover", "toggle", JSONObject().put("entity_id", entity.id))
        "media_player" -> client.call("media_player", "media_play_pause", JSONObject().put("entity_id", entity.id))
        else -> client.call("homeassistant", "toggle", JSONObject().put("entity_id", entity.id))
    }
}
