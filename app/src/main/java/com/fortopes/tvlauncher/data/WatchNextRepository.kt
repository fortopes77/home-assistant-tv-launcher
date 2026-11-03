package com.fortopes.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.tv.TvContract
import androidx.core.content.ContextCompat

/**
 * Reads the TV's shared "watch next" list (Android's TvProvider). Streaming apps such as Disney+ put what
 * you're part-way through there for the home screen to show. It needs the TV listings permission
 * (READ_TV_LISTINGS), which Android asks about once. What shows depends on each app: an app that doesn't publish to this list
 * won't appear.
 */
class WatchNextRepository(context: Context) {
    private val appContext = context.applicationContext

    val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(appContext, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun load(limit: Int = 20): List<ContinueItem> {
        if (!hasPermission) return emptyList()
        val pm = appContext.packageManager
        val labels = HashMap<String, String>()
        fun label(pkg: String) = labels.getOrPut(pkg) {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }

        val cursor = runCatching {
            appContext.contentResolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI,
                null,
                null,
                null,
                "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC",
            )
        }.getOrNull() ?: return emptyList()

        return cursor.use { c ->
            val out = ArrayList<ContinueItem>()
            while (c.moveToNext() && out.size < limit) {
                runCatching { c.toItem(::label) }.getOrNull()?.let(out::add)
            }
            out
        }
    }

    /**
     * What Fortopes Home can see in the list, for the settings screen: "3 items: YouTube (2), Disney+ (1)".
     * Nothing at all from any app means Android only shows Fortopes Home its own entries.
     */
    fun summary(): String {
        if (!hasPermission) return "not allowed"
        val pm = appContext.packageManager
        val counts = LinkedHashMap<String, Int>()
        val cursor = runCatching {
            appContext.contentResolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI,
                arrayOf(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME),
                null,
                null,
                null,
            )
        }.getOrElse { return "couldn't read it (${it.javaClass.simpleName})" } ?: return "couldn't read it"
        cursor.use { c ->
            while (c.moveToNext()) {
                val pkg = c.getString(0) ?: "?"
                counts[pkg] = (counts[pkg] ?: 0) + 1
            }
        }
        if (counts.isEmpty()) return "Fortopes Home can see 0 items from any app"
        val parts = counts.entries.joinToString(", ") { (pkg, n) ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            "$label ($n)"
        }
        return "Fortopes Home can see ${counts.values.sum()} items: $parts"
    }

    private fun Cursor.toItem(label: (String) -> String): ContinueItem? {
        val pkg = string(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME) ?: return null
        if (pkg == appContext.packageName) return null
        if (int(TvContract.WatchNextPrograms.COLUMN_BROWSABLE) == 0) return null
        val intentUri = string(TvContract.WatchNextPrograms.COLUMN_INTENT_URI) ?: return null
        val intent = runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null

        val title = string(TvContract.WatchNextPrograms.COLUMN_TITLE) ?: return null
        val episodeTitle = string(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE)
        val season = string(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER)
        val episode = string(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)
        val subtitle = listOfNotNull(
            if (season != null && episode != null) "S$season E$episode" else null,
            episodeTitle,
        ).joinToString(" · ")

        val position = long(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
        val duration = long(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS)
        val progress = if (position != null && duration != null && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null

        return ContinueItem(
            key = "watchnext:${long(TvContract.WatchNextPrograms._ID)}",
            title = title,
            subtitle = subtitle,
            imageUrl = string(TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI)
                ?: string(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI),
            progress = progress,
            sourceLabel = label(pkg),
            sourcePackage = pkg,
            lastWatched = long(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS) ?: 0L,
            action = ContinueItem.Action.Launch(intent),
        )
    }

    private fun Cursor.string(col: String): String? =
        getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }?.takeIf { it.isNotBlank() }

    private fun Cursor.long(col: String): Long? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) }

    private fun Cursor.int(col: String): Int? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }

    companion object {
        /** "TV listings": Android asks once. Without it, the list only holds Fortopes Home's own entries. */
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
    }
}
