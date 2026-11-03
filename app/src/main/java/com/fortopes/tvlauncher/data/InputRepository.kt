package com.fortopes.tvlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** A TV input such as HDMI 1. Only TVs have these; a Chromecast or streaming box has none. */
data class TvInput(val id: String, val label: String, val type: Int)

class InputRepository(context: Context) {
    private val appContext = context.applicationContext

    fun loadInputs(): List<TvInput> {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)) return emptyList()
        val manager = appContext.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return runCatching {
            manager.tvInputList
                // HDMI ports and the like; a device seen over HDMI-CEC is a child of its port, so skip those.
                .filter { it.isPassthroughInput && it.parentId == null && !it.isHidden(appContext) }
                .map { info ->
                    TvInput(
                        id = info.id,
                        label = info.loadCustomLabel(appContext)?.toString()?.takeIf { it.isNotBlank() }
                            ?: info.loadLabel(appContext).toString(),
                        type = info.type,
                    )
                }
                .sortedWith(compareBy({ it.type != TvInputInfo.TYPE_HDMI }, { it.label }))
        }.getOrDefault(emptyList())
    }

    /**
     * The TV's own aerial tuner (free to air), plus any other live TV source built into the TV.
     * These are the inputs that aren't a socket on the back.
     */
    fun loadTuners(): List<TvInput> {
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)) return emptyList()
        val manager = appContext.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return runCatching {
            manager.tvInputList
                .filter { !it.isPassthroughInput && it.parentId == null && !it.isHidden(appContext) }
                .map { info ->
                    val label = info.loadCustomLabel(appContext)?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.loadLabel(appContext).toString()
                    TvInput(id = info.id, label = if (info.type == TvInputInfo.TYPE_TUNER) "Free to air" else label, type = info.type)
                }
                .sortedBy { it.type != TvInputInfo.TYPE_TUNER }
                .distinctBy { it.label }
        }.getOrDefault(emptyList())
    }

    /**
     * Opens live TV on a tuner, in the TV's own Live TV app. Tries that tuner's channels first,
     * then live TV in general, then the app behind the tuner.
     */
    fun openTuner(input: TvInput): Boolean {
        val tries = listOf(
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(input.id)),
            Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI),
        )
        for (intent in tries) {
            if (runCatching { appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        val pkg = input.id.substringBefore('/')
        val launch = appContext.packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: appContext.packageManager.getLaunchIntentForPackage(pkg)
            ?: return false
        return runCatching { appContext.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Switches the TV to the input, through the TV's own Live TV / inputs app. */
    fun open(input: TvInput): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }.isSuccess
}
