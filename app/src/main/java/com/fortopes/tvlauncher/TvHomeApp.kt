package com.fortopes.tvlauncher

import android.app.Application
import com.fortopes.tvlauncher.data.LauncherPrefs

/**
 * Saves the last crash so TV Home's settings can show it. There's no other way to see one on a TV
 * without ADB, and a crash in the Home button service makes Android switch the service off.
 */
class TvHomeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = LauncherPrefs(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { prefs.recordError("Crash", error, sync = true) }
            previous?.uncaughtException(thread, error)
        }
    }
}
