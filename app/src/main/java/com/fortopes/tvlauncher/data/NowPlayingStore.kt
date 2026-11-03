package com.fortopes.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import com.fortopes.tvlauncher.home.NowPlayingListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Remembers the last thing each app played (Disney+, Binge, ...), from Android's "now playing" info.
 * Those apps don't share a continue-watching list Fortopes Home can read, so this is the next best thing:
 * the show's name and picture, and OK opens the app.
 */
class NowPlayingStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("now_playing", Context.MODE_PRIVATE)
    private val artDir = File(appContext.filesDir, "now_playing").apply { mkdirs() }

    data class Entry(
        val pkg: String,
        val title: String,
        val subtitle: String,
        val artPath: String?,
        val artUri: String?,
        val positionMs: Long,
        val durationMs: Long,
        val at: Long,
    )

    @Synchronized
    fun save(entry: Entry, art: Bitmap?) {
        var artPath = entry.artPath
        if (art != null) {
            runCatching {
                val file = File(artDir, "${entry.pkg}.jpg")
                file.outputStream().use { art.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                artPath = file.absolutePath
            }
        }
        val all = load().filter { it.pkg != entry.pkg } + entry.copy(artPath = artPath)
        val kept = all.sortedByDescending { it.at }.take(MAX_APPS)
        val json = JSONArray()
        kept.forEach {
            json.put(
                JSONObject()
                    .put("pkg", it.pkg).put("title", it.title).put("subtitle", it.subtitle)
                    .put("artPath", it.artPath ?: JSONObject.NULL).put("artUri", it.artUri ?: JSONObject.NULL)
                    .put("pos", it.positionMs).put("dur", it.durationMs).put("at", it.at),
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    @Synchronized
    fun load(): List<Entry> {
        val text = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    pkg = o.getString("pkg"),
                    title = o.getString("title"),
                    subtitle = o.optString("subtitle"),
                    artPath = o.optString("artPath").takeIf { !o.isNull("artPath") && it.isNotBlank() },
                    artUri = o.optString("artUri").takeIf { !o.isNull("artUri") && it.isNotBlank() },
                    positionMs = o.optLong("pos"),
                    durationMs = o.optLong("dur"),
                    at = o.optLong("at"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** The remembered shows as Continue watching cards. OK opens the app. */
    fun items(): List<ContinueItem> {
        val pm = appContext.packageManager
        return load().mapNotNull { e ->
            val launch = pm.getLeanbackLaunchIntentForPackage(e.pkg) ?: pm.getLaunchIntentForPackage(e.pkg) ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(e.pkg, 0)).toString() }.getOrDefault(e.pkg)
            ContinueItem(
                key = "nowplaying:${e.pkg}",
                title = e.title,
                subtitle = e.subtitle,
                imageUrl = e.artPath?.let { "file://$it" } ?: e.artUri,
                progress = if (e.durationMs > 0) (e.positionMs.toFloat() / e.durationMs).coerceIn(0f, 1f) else null,
                sourceLabel = label,
                sourcePackage = e.pkg,
                lastWatched = e.at,
                action = ContinueItem.Action.Launch(launch),
            )
        }
    }

    /** Whether Android has given Fortopes Home notification access, which reading "now playing" needs. */
    fun hasAccess(): Boolean {
        val enabled = Settings.Secure.getString(appContext.contentResolver, "enabled_notification_listeners") ?: return false
        val ours = ComponentName(appContext, NowPlayingListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun accessSettingsIntent(): Intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

    companion object {
        private const val KEY = "entries"
        private const val MAX_APPS = 10
    }
}
