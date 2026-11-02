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

    /** Switches the TV to the input, through the TV's own Live TV / inputs app. */
    fun open(input: TvInput): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }.isSuccess
}
