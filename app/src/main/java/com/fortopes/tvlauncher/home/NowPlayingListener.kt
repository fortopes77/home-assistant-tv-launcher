package com.fortopes.tvlauncher.home

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import com.fortopes.tvlauncher.data.JellyfinClient
import com.fortopes.tvlauncher.data.LauncherPrefs
import com.fortopes.tvlauncher.data.NowPlayingStore

/**
 * Watches Android's "now playing" sessions (the info behind play/pause controls) and remembers the last
 * show each app played. Android only lets a notification listener see other apps' media sessions, which
 * is why this is one. It doesn't read or change notifications.
 */
class NowPlayingListener : NotificationListenerService() {

    private lateinit var store: NowPlayingStore
    private lateinit var prefs: LauncherPrefs
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private var sessionManager: MediaSessionManager? = null

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        safely { watch(controllers.orEmpty()) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        store = NowPlayingStore(this)
        prefs = LauncherPrefs(this)
        safely {
            val manager = getSystemService(MediaSessionManager::class.java) ?: return@safely
            sessionManager = manager
            val me = ComponentName(this, NowPlayingListener::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsChanged, me, handler)
            watch(manager.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        safely {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsChanged)
            callbacks.forEach { (c, cb) -> c.unregisterCallback(cb) }
            callbacks.clear()
        }
        super.onListenerDisconnected()
    }

    private fun watch(controllers: List<MediaController>) {
        callbacks.keys.filter { old -> controllers.none { it.sessionToken == old.sessionToken } }.forEach { gone ->
            callbacks.remove(gone)?.let { gone.unregisterCallback(it) }
        }
        controllers.filter { c -> callbacks.keys.none { it.sessionToken == c.sessionToken } }.forEach { c ->
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = safely { remember(c) }
                override fun onMetadataChanged(metadata: MediaMetadata?) = safely { remember(c) }
            }
            c.registerCallback(cb, handler)
            callbacks[c] = cb
            safely { remember(c) }
        }
    }

    private fun remember(c: MediaController) {
        val pkg = c.packageName ?: return
        if (pkg == packageName || pkg == JellyfinClient.JELLYFIN_PACKAGE) return // Jellyfin comes from its own API
        val state = c.playbackState ?: return
        if (state.state != PlaybackState.STATE_PLAYING && state.state != PlaybackState.STATE_PAUSED) return
        val md = c.metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: return
        val subtitle = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val artUri = md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        val previous = store.load().firstOrNull { it.pkg == pkg }
        val sameShow = previous?.title == title
        store.save(
            NowPlayingStore.Entry(
                pkg = pkg,
                title = title,
                subtitle = subtitle,
                artPath = if (sameShow && art == null) previous?.artPath else null,
                artUri = artUri,
                positionMs = state.position.coerceAtLeast(0),
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
                at = System.currentTimeMillis(),
            ),
            art,
        )
    }

    // An error here must never crash TV Home.
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            if (::prefs.isInitialized) runCatching { prefs.recordError("Now playing", e) }
        }
    }
}
