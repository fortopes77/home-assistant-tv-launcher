package com.fortopes.tvlauncher.data

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.fortopes.tvlauncher.home.HomeButtonService

/** Small settings store shared by the home screen and the Home button service. */
class LauncherPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** The on/off toggle: when off, Home goes to the stock Google TV home as normal. */
    var overrideEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERRIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_OVERRIDE, value).apply()

    /**
     * Set by "Open Google TV home": the service leaves the stock home alone until you open another app
     * from it, so you can browse it once without turning the toggle off.
     */
    var bypassStockHome: Boolean
        get() = prefs.getBoolean(KEY_BYPASS, false)
        set(value) = prefs.edit().putBoolean(KEY_BYPASS, value).apply()

    /** When the Home button service last started, or 0 if it never has. */
    var serviceStartedAt: Long
        get() = prefs.getLong(KEY_SERVICE_STARTED, 0L)
        set(value) = prefs.edit().putLong(KEY_SERVICE_STARTED, value).apply()

    /** The last error, for the settings screen: when, where, and the start of the stack trace. */
    val lastError: String? get() = prefs.getString(KEY_LAST_ERROR, null)

    fun recordError(where: String, error: Throwable, sync: Boolean = false) {
        val text = "$where at ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\n" +
            error.stackTraceToString().take(1500)
        val edit = prefs.edit().putString(KEY_LAST_ERROR, text)
        if (sync) edit.commit() else edit.apply()
    }

    fun clearError() = prefs.edit().remove(KEY_LAST_ERROR).apply()

    /** Whether the accessibility service is switched on in Android's settings. */
    fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val ours = ComponentName(appContext, HomeButtonService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    companion object {
        private const val KEY_OVERRIDE = "override_enabled"
        private const val KEY_SERVICE_STARTED = "service_started_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_BYPASS = "bypass_stock_home"
    }
}
