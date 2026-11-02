package com.fortopes.tvlauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Seerr (Jellyseerr or Overseerr) sign-in: its address and API key, saved on the TV. */
class SeerrStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("seerr", Context.MODE_PRIVATE)

    var url: String?
        get() = prefs.getString("url", null)
        set(value) = prefs.edit().putString("url", value).apply()

    var apiKey: String?
        get() = prefs.getString("api_key", null)
        set(value) = prefs.edit().putString("api_key", value).apply()

    val isSignedIn: Boolean get() = !url.isNullOrBlank() && !apiKey.isNullOrBlank()

    fun signOut() = prefs.edit().remove("api_key").apply()
}

/** A film or show Seerr recommends. */
data class SeerrItem(
    val id: Int,
    /** "movie" or "tv". */
    val mediaType: String,
    val title: String,
    val year: String,
    val overview: String,
    val backdropUrl: String?,
    val posterUrl: String?,
    val rating: Double,
    /** Seerr's media status: 5 available, 4 partly available, 3 processing, 2 pending, else not requested. */
    val status: Int,
    /** The Jellyfin item, when it's already in your library. */
    val jellyfinId: String?,
    /** Where it came from, such as "Trending". */
    val source: String,
) {
    val key: String get() = "$mediaType-$id"
    val kind: String get() = if (mediaType == "tv") "Series" else "Film"
    val available: Boolean get() = status == 5 || status == 4
    val requested: Boolean get() = status == 2 || status == 3
    val statusLabel: String get() = when (status) {
        5 -> "In your library"
        4 -> "Partly in your library"
        3 -> "Requested, downloading"
        2 -> "Requested, waiting for approval"
        else -> ""
    }
}

/** Seerr's API, with an API key (Seerr → Settings → General). */
class SeerrClient(private val store: SeerrStore) {
    private fun headers(key: String? = store.apiKey) = mapOf("X-Api-Key" to (key ?: ""))
    private val base: String get() = store.url ?: error("Seerr isn't set up")

    /** Checks an address and key work. Returns the signed-in user's name. */
    fun check(url: String, key: String): String {
        val me = Http.obj(Http.request("GET", "${normaliseUrl(url)}/api/v1/auth/me", headers(key)))
        return me.optString("displayName").ifBlank { me.optString("username").ifBlank { "Seerr" } }
    }

    /** Trending, then popular films and series, without repeats. */
    fun recommended(max: Int = 30): List<SeerrItem> {
        val lists = listOf(
            "/api/v1/discover/trending" to "Trending",
            "/api/v1/discover/movies" to "Popular film",
            "/api/v1/discover/tv" to "Popular series",
        ).map { (path, label) ->
            runCatching {
                val res = Http.obj(Http.request("GET", "$base$path?page=1", headers(), timeoutMs = 10_000)).optJSONArray("results") ?: JSONArray()
                (0 until res.length()).mapNotNull { parse(res.getJSONObject(it), label) }
            }.getOrDefault(emptyList())
        }
        // Take turns from each list so the row mixes trending, films and series.
        val out = LinkedHashMap<String, SeerrItem>()
        var i = 0
        while (out.size < max && lists.any { i < it.size }) {
            lists.forEach { l -> l.getOrNull(i)?.let { out.putIfAbsent(it.key, it) } }
            i++
        }
        return out.values.take(max)
    }

    /** The YouTube key of the item's trailer, if Seerr knows one. */
    fun trailerKey(item: SeerrItem): String? {
        val detail = Http.obj(Http.request("GET", "$base/api/v1/${item.mediaType}/${item.id}", headers()))
        val videos = detail.optJSONArray("relatedVideos") ?: return null
        val all = (0 until videos.length()).map { videos.getJSONObject(it) }.filter { it.optString("site") == "YouTube" }
        val best = all.firstOrNull { it.optString("type") == "Trailer" } ?: all.firstOrNull { it.optString("type") == "Teaser" } ?: all.firstOrNull()
        return best?.optString("key")?.takeIf { it.isNotBlank() }
    }

    /** Asks for it to be added; series ask for every season. */
    fun request(item: SeerrItem) {
        val body = JSONObject().put("mediaType", item.mediaType).put("mediaId", item.id)
        if (item.mediaType == "tv") body.put("seasons", "all")
        Http.request("POST", "$base/api/v1/request", headers(), body.toString())
    }

    private fun parse(o: JSONObject, source: String): SeerrItem? {
        val type = o.optString("mediaType")
        if (type != "movie" && type != "tv") return null // people in trending
        val title = o.optString("title").ifBlank { o.optString("name") }.ifBlank { return null }
        val date = o.optString("releaseDate").ifBlank { o.optString("firstAirDate") }
        val media = o.optJSONObject("mediaInfo")
        fun img(key: String, size: String) = o.optString(key).takeIf { it.isNotBlank() && it != "null" }?.let { "https://image.tmdb.org/t/p/$size$it" }
        return SeerrItem(
            id = o.optInt("id"),
            mediaType = type,
            title = title,
            year = date.take(4),
            overview = o.optString("overview"),
            backdropUrl = img("backdropPath", "w780"),
            posterUrl = img("posterPath", "w342"),
            rating = o.optDouble("voteAverage", 0.0),
            status = media?.optInt("status", 1) ?: 1,
            jellyfinId = media?.optString("jellyfinMediaId")?.takeIf { it.isNotBlank() && it != "null" },
            source = source,
        )
    }

    companion object {
        /** "cunn-ha.local" → "http://cunn-ha.local:5055". */
        fun normaliseUrl(input: String): String {
            var url = input.trim().trimEnd('/')
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
            if (url.startsWith("http://") && !Regex("^http://[^/]+:\\d+").containsMatchIn(url)) {
                val host = url.removePrefix("http://").substringBefore('/')
                url = "http://$host:5055"
            }
            return url
        }
    }
}
