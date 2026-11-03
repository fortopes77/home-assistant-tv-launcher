package com.fortopes.tvlauncher.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Continue watching row: Jellyfin's resume list, the TV's watch next list when apps share to it, and
 * the last show each other app played (Disney+, Binge, ...).
 */
class ContinueWatchingRepository(
    context: Context,
    private val jellyfin: JellyfinClient,
    private val watchNext: WatchNextRepository,
    private val nowPlaying: NowPlayingStore,
    private val prefs: LauncherPrefs,
) {
    private val appContext = context.applicationContext

    data class Result(val items: List<ContinueItem>, val jellyfinError: String?)

    /** Asks Jellyfin, the TV's watch next list and the remembered shows at the same time. */
    suspend fun load(): Result = coroutineScope {
        val jellyfinAsk = async { runCatching { jellyfin.resume() } }
        val watchNextAsk = async { runCatching { watchNext.load() }.getOrDefault(emptyList()) }
        var jellyfinError: String? = null
        val fromJellyfin = jellyfinAsk.await()
            .onFailure {
                jellyfinError = if (it is HttpException && it.code == 401) "Jellyfin sign-in expired" else "Couldn't reach Jellyfin"
            }
            .getOrDefault(emptyList())
        // Jellyfin's own resume list is better when you're signed in; otherwise its watch next entries do.
        val fromWatchNext = watchNextAsk.await()
            .filter { fromJellyfin.isEmpty() || it.sourcePackage != JellyfinClient.JELLYFIN_PACKAGE }
        val fromNowPlaying = runCatching { nowPlaying.items() }.getOrDefault(emptyList())
        // An app in the watch next list is better (it opens the exact show), so it wins over a remembered one.
        val fromApps = fromWatchNext + fromNowPlaying.filter { np -> fromWatchNext.none { it.sourcePackage == np.sourcePackage } }
        // Newest first across all apps; items with no time (rare) go last.
        val items = (fromJellyfin + fromApps).sortedByDescending { it.lastWatched }.take(20)
        Result(items, jellyfinError)
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
