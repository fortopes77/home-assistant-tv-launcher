package com.fortopes.tvlauncher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Home Assistant's live connection (its websocket API). Instead of downloading every entity in the house
 * every 30 seconds, it asks once, then Home Assistant sends only the changes to the entities the Home row
 * uses. Lights show their new state as soon as they change, and the TV does far less work.
 */
class HaLive(private val store: HaStore) {

    /**
     * The Home row's entities: everything once, then again after each change. Ends when the connection
     * drops, with an [HttpException] (401) if the token stopped working. Collect it while the screen shows.
     */
    fun updates(): Flow<List<HaEntity>> = callbackFlow {
        val base = store.url ?: error("Home Assistant isn't set up")
        val token = store.token ?: error("Home Assistant isn't set up")
        val url = Mdns.rewrite("$base/api/websocket").replaceFirst("http", "ws")
        // Only touched on OkHttp's reader thread, which handles one message at a time.
        val entities = LinkedHashMap<String, HaEntity>()

        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (msg.optString("type")) {
                    "auth_required" -> webSocket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                    "auth_invalid" -> close(HttpException(401, "Home Assistant token stopped working"))
                    "auth_ok" -> webSocket.send(JSONObject().put("id", GET_STATES).put("type", "get_states").toString())
                    "result" -> {
                        if (!msg.optBoolean("success")) {
                            close(IllegalStateException("Home Assistant said no: ${msg.optJSONObject("error")}"))
                            return
                        }
                        if (msg.optInt("id") != GET_STATES) return
                        val arr = msg.optJSONArray("result") ?: JSONArray()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val id = o.getString("entity_id")
                            if (!isUsed(id)) continue
                            entities[id] = entity(id, o.optString("state"), o.optJSONObject("attributes") ?: JSONObject())
                        }
                        trySend(entities.values.toList())
                        // An empty list would mean "everything", so there's nothing to follow.
                        if (entities.isEmpty()) return
                        // From now on, only changes to these entities.
                        webSocket.send(
                            JSONObject()
                                .put("id", SUBSCRIBE)
                                .put("type", "subscribe_entities")
                                .put("entity_ids", JSONArray(entities.keys.toList()))
                                .toString(),
                        )
                    }
                    "event" -> if (msg.optInt("id") == SUBSCRIBE) {
                        msg.optJSONObject("event")?.let { if (apply(entities, it)) trySend(entities.values.toList()) }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }
        })
        awaitClose { socket.close(1000, null) }
    }.flowOn(Dispatchers.IO).conflate()

    /**
     * Applies a subscribe_entities event: "a" is whole entities, "c" is changes ("+" new values, "-" attributes
     * that went away), "r" is removed entities. Returns whether anything changed.
     */
    private fun apply(entities: LinkedHashMap<String, HaEntity>, event: JSONObject): Boolean {
        var changed = false
        event.optJSONObject("a")?.let { added ->
            added.keys().forEach { id ->
                val o = added.getJSONObject(id)
                entities[id] = entity(id, o.optString("s"), o.optJSONObject("a") ?: JSONObject())
                changed = true
            }
        }
        event.optJSONObject("c")?.let { changes ->
            changes.keys().forEach { id ->
                val old = entities[id] ?: return@forEach
                val diff = changes.getJSONObject(id)
                val plus = diff.optJSONObject("+")
                val minus = diff.optJSONObject("-")?.optJSONArray("a")
                val state = plus?.optString("s")?.takeIf { plus.has("s") } ?: old.state
                val attrs = if (plus?.has("a") == true || minus != null) {
                    val copy = JSONObject()
                    old.attributes.keys().forEach { copy.put(it, old.attributes.get(it)) }
                    plus?.optJSONObject("a")?.let { a -> a.keys().forEach { copy.put(it, a.get(it)) } }
                    if (minus != null) for (i in 0 until minus.length()) copy.remove(minus.getString(i))
                    copy
                } else {
                    old.attributes
                }
                if (state != old.state || attrs !== old.attributes) {
                    entities[id] = entity(id, state, attrs)
                    changed = true
                }
            }
        }
        event.optJSONArray("r")?.let { removed ->
            for (i in 0 until removed.length()) if (entities.remove(removed.getString(i)) != null) changed = true
        }
        return changed
    }

    private fun entity(id: String, state: String, attrs: JSONObject) =
        HaEntity(id = id, state = state, name = attrs.optString("friendly_name").ifBlank { id }, attributes = attrs)

    private companion object {
        const val GET_STATES = 1
        const val SUBSCRIBE = 2

        /** The kinds of entity the Home row shows; changes to anything else (sensors, mostly) aren't sent. */
        val USED = setOf("light", "cover", "scene", "script", "camera", "weather", "media_player")

        fun isUsed(id: String) = id.substringBefore('.') in USED

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            // Notices a connection that silently died (Wi-Fi blip, Home Assistant restart).
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }
}
