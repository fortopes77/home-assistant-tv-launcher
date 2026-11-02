package com.fortopes.tvlauncher.home

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.fortopes.tvlauncher.MainActivity
import com.fortopes.tvlauncher.data.LauncherPrefs

/**
 * Brings up TV Home instead of the stock Google TV home, the way Projectivy does, without
 * replacing the system's home screen. It does two things while the toggle is on:
 *
 * 1. Catches the remote's Home button and opens TV Home straight away.
 * 2. Watches which app comes to the front. When the stock home appears anyway (Back out of an app,
 *    or a remote whose Home press doesn't reach accessibility services), it opens TV Home over it.
 *
 * With the toggle off it does nothing, so Home behaves exactly as stock.
 */
class HomeButtonService : AccessibilityService() {

    private lateinit var prefs: LauncherPrefs
    private var stockHomePackages: Set<String> = emptySet()
    private var lastOpenedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = LauncherPrefs(this)
        stockHomePackages = findStockHomePackages(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || !active() || prefs.bypassStockHome) return false
        // Swallow both the press and the release so the stock home never sees it; open on release.
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openLauncher()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || !active()) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in stockHomePackages) {
            if (!prefs.bypassStockHome) openLauncher()
        } else if (prefs.bypassStockHome && pkg !in PASSING_WINDOWS && pkg != packageName) {
            // An app was opened from the stock home, so the next return to it comes back to TV Home.
            prefs.bypassStockHome = false
        }
    }

    override fun onInterrupt() = Unit

    private fun active(): Boolean = ::prefs.isInitialized && prefs.overrideEnabled

    private fun openLauncher() {
        val now = SystemClock.uptimeMillis()
        if (now - lastOpenedAt < 600) return // the key press and the window change often arrive together
        lastOpenedAt = now
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
            .putExtra(MainActivity.EXTRA_FROM_HOME_BUTTON, true)
        runCatching { startActivity(intent) }
    }

    companion object {
        /** Overlays that pop up over whatever is showing, such as the volume bar and the keyboard. */
        private val PASSING_WINDOWS = setOf(
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.google.android.leanback.ime",
            "android",
        )

        /** Stock launchers: Google TV, Android TV, and the old Leanback launcher. */
        private val KNOWN_STOCK_HOMES = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.amazon.tv.launcher",
        )

        /** The known stock launchers plus whatever the TV says its home screen is. */
        fun findStockHomePackages(context: Context): Set<String> {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val current = context.packageManager
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            return buildSet {
                addAll(KNOWN_STOCK_HOMES)
                // "android" is the app chooser, shown when no home is set as the default.
                if (current != null && current != "android" && current != context.packageName) add(current)
            }
        }

        /** The stock home's launch intent, for the "Open Google TV home" button. */
        fun stockHomeIntent(context: Context): Intent? {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pkg = context.packageManager
                .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.takeIf { it != "android" && it != context.packageName }
            return home.apply {
                if (pkg != null) setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
