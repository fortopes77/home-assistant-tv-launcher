package com.fortopes.tvlauncher.data

import android.content.Intent

/** One thing you're part-way through, from Jellyfin or from another app's "watch next" list. */
data class ContinueItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    /** 0 to 1, or null when the app didn't say. */
    val progress: Float?,
    val sourceLabel: String,
    val sourcePackage: String,
    val lastWatched: Long,
    val action: Action,
) {
    sealed interface Action {
        /** Open the Jellyfin app and start [itemId] at [startTicks]. */
        data class Jellyfin(val itemId: String, val startTicks: Long) : Action

        /** The app's own "carry on watching" intent. */
        data class Launch(val intent: Intent) : Action
    }
}
