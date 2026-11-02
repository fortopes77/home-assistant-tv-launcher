package com.fortopes.tvlauncher.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The Continue watching row: Jellyfin's resume list plus the TV's watch next list (Disney+, Binge, ...). */
class ContinueWatchingRepository(
    context: Context,
    private val jellyfin: JellyfinClient,
    private val watchNext: WatchNextRepository,
    private val prefs: LauncherPrefs,
) {
    private val appContext = context.applicationContext

    data class Result(val items: List<ContinueItem>, val jellyfinError: String?)

    fun load(): Result {
        var jellyfinError: String? = null
        val fromJellyfin = runCatching { jellyfin.resume() }
            .onFailure {
                jellyfinError = if (it is HttpException && it.code == 401) "Jellyfin sign-in expired" else "Couldn't reach Jellyfin"
            }
            .getOrDefault(emptyList())
        val fromApps = runCatching { watchNext.load() }.getOrDefault(emptyList())
        // Newest first across all apps; items with no time (rare) go last.
        val items = (fromJellyfin + fromApps).sortedByDescending { it.lastWatched }.take(20)
        return Result(items, jellyfinError)
    }

    /** Opens the item. Jellyfin is opened first and then told to play, in the background. */
    fun open(item: ContinueItem, scope: CoroutineScope): Boolean = when (val action = item.action) {
        is ContinueItem.Action.Launch -> runCatching {
            appContext.startActivity(Intent(action.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess

        is ContinueItem.Action.Jellyfin -> {
            val launch = appContext.packageManager.getLeanbackLaunchIntentForPackage(JellyfinClient.JELLYFIN_PACKAGE)
                ?: appContext.packageManager.getLaunchIntentForPackage(JellyfinClient.JELLYFIN_PACKAGE)
            if (launch == null) {
                false
            } else {
                val since = System.currentTimeMillis()
                appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                scope.launch {
                    runCatching { jellyfin.playOnTv(action.itemId, action.startTicks, since) }
                        .onFailure { prefs.recordError("Starting Jellyfin playback", it) }
                }
                true
            }
        }
    }
}
