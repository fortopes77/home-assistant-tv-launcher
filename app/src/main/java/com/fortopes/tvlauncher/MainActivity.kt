package com.fortopes.tvlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.fortopes.tvlauncher.data.AppRepository
import com.fortopes.tvlauncher.data.ContinueItem
import com.fortopes.tvlauncher.data.ContinueWatchingRepository
import com.fortopes.tvlauncher.data.JellyfinClient
import com.fortopes.tvlauncher.data.JellyfinStore
import com.fortopes.tvlauncher.data.NowPlayingStore
import com.fortopes.tvlauncher.data.WatchNextRepository
import com.fortopes.tvlauncher.ui.JellyfinSetupScreen
import com.fortopes.tvlauncher.data.InputRepository
import com.fortopes.tvlauncher.data.LauncherPrefs
import com.fortopes.tvlauncher.data.TvApp
import com.fortopes.tvlauncher.data.TvInput
import com.fortopes.tvlauncher.home.HomeButtonService
import com.fortopes.tvlauncher.ui.HomeScreen
import com.fortopes.tvlauncher.ui.SettingsScreen
import com.fortopes.tvlauncher.ui.TvHomeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var prefs: LauncherPrefs
    private lateinit var appRepo: AppRepository
    private lateinit var inputRepo: InputRepository
    private lateinit var jellyfinStore: JellyfinStore
    private lateinit var jellyfin: JellyfinClient
    private lateinit var watchNext: WatchNextRepository
    private lateinit var nowPlaying: NowPlayingStore
    private lateinit var continueRepo: ContinueWatchingRepository

    private val loadLock = Mutex()

    private var apps by mutableStateOf<List<TvApp>>(emptyList())
    private var inputs by mutableStateOf<List<TvInput>>(emptyList())
    private var loaded by mutableStateOf(false)
    private enum class Screen { Home, Settings, JellyfinSetup }

    private var screen by mutableStateOf(Screen.Home)
    private var continueItems by mutableStateOf<List<ContinueItem>>(emptyList())
    private var jellyfinStatus by mutableStateOf<String?>(null)
    private var nowPlayingAccess by mutableStateOf(false)
    private var overrideEnabled by mutableStateOf(true)
    private var serviceEnabled by mutableStateOf(false)
    private var serviceStartedAt by mutableStateOf(0L)
    private var lastError by mutableStateOf<String?>(null)
    private var resetSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)
        appRepo = AppRepository(this)
        inputRepo = InputRepository(this)
        jellyfinStore = JellyfinStore(this)
        jellyfin = JellyfinClient(jellyfinStore)
        watchNext = WatchNextRepository(this)
        nowPlaying = NowPlayingStore(this)
        continueRepo = ContinueWatchingRepository(this, jellyfin, watchNext, nowPlaying, prefs)

        setContent {
            TvHomeTheme {
                when (screen) {
                    Screen.JellyfinSetup -> {
                        BackHandler { screen = Screen.Settings }
                        JellyfinSetupScreen(client = jellyfin, onDone = {
                            screen = Screen.Settings
                            refreshStatus()
                            reloadContinue()
                        })
                    }
                    Screen.Settings -> {
                        BackHandler { screen = Screen.Home }
                        SettingsScreen(
                            overrideEnabled = overrideEnabled,
                            serviceEnabled = serviceEnabled,
                            serviceStartedAt = serviceStartedAt,
                            lastError = lastError,
                            jellyfinStatus = jellyfinStatus,
                            nowPlayingAccess = nowPlayingAccess,
                            version = BuildConfigVersion.name(this),
                            onToggleOverride = {
                                prefs.overrideEnabled = it
                                overrideEnabled = it
                            },
                            onOpenAccessibilitySettings = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            onOpenStockHome = ::openStockHome,
                            onOpenTvSettings = ::openTvSettings,
                            onOpenAppInfo = {
                                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                            },
                            onClearError = {
                                prefs.clearError()
                                lastError = null
                            },
                            onSetUpJellyfin = { screen = Screen.JellyfinSetup },
                            onSignOutJellyfin = {
                                jellyfinStore.signOut()
                                refreshStatus()
                                reloadContinue()
                            },
                            onAllowNowPlaying = {
                                if (nowPlayingAccess) {
                                    toast("Already on")
                                } else {
                                    // Most TVs don't have this settings page; then the README's ADB command is the way.
                                    runCatching { startActivity(nowPlaying.accessSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                        .onFailure { toast("This TV has no page for it. Use the ADB command in the README.") }
                                }
                            },
                        )
                    }
                    Screen.Home -> {
                        // This is the home screen, so Back has nowhere to go.
                        BackHandler { }
                        HomeScreen(
                            continueItems = continueItems,
                            continueNeedsSetup = jellyfinStatus == null || !nowPlayingAccess,
                            apps = apps,
                            inputs = inputs,
                            loaded = loaded,
                            resetSignal = resetSignal,
                            onOpenContinue = {
                                if (!continueRepo.open(it, (application as TvHomeApp).scope)) toast("Couldn't open ${it.sourceLabel}")
                            },
                            onOpenApp = { if (!appRepo.launch(it)) toast("Couldn't open ${it.label}") },
                            onOpenInput = { if (!inputRepo.open(it)) toast("This TV didn't open ${it.label}") },
                            onOpenTvSettings = ::openTvSettings,
                            onOpenLauncherSettings = { screen = Screen.Settings },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.bypassStockHome = false
        overrideEnabled = prefs.overrideEnabled
        serviceEnabled = prefs.isServiceEnabled()
        refreshStatus()
        reload() // picks up apps installed or removed while we were away
        reloadContinue()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while we're already showing: close settings and jump back to the first card.
        if (intent.getBooleanExtra(EXTRA_FROM_HOME_BUTTON, false) || intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            screen = Screen.Home
            resetSignal++
        }
    }

    private fun reload() {
        lifecycleScope.launch {
            loadLock.withLock {
                val (newApps, newInputs) = withContext(Dispatchers.IO) {
                    appRepo.loadApps() to inputRepo.loadInputs()
                }
                apps = newApps
                inputs = newInputs
                loaded = true
            }
        }
    }

    private fun refreshStatus() {
        serviceStartedAt = prefs.serviceStartedAt
        lastError = prefs.lastError
        nowPlayingAccess = nowPlaying.hasAccess()
        jellyfinStatus = if (jellyfinStore.isSignedIn) {
            "Signed in to ${jellyfinStore.serverName ?: jellyfinStore.url} as ${jellyfinStore.userName ?: "you"}."
        } else {
            null
        }
    }

    private fun reloadContinue() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { continueRepo.load() }
            continueItems = result.items
            if (result.jellyfinError != null && jellyfinStore.isSignedIn) {
                jellyfinStatus = "${result.jellyfinError}. Press OK to sign in again."
            }
        }
    }

    private fun openTvSettings() = open(Intent(Settings.ACTION_SETTINGS))

    private fun openStockHome() {
        val intent = HomeButtonService.stockHomeIntent(this) ?: return
        prefs.bypassStockHome = true
        if (!open(intent)) prefs.bypassStockHome = false
    }

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        toast("This TV can't open that")
        false
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_FROM_HOME_BUTTON = "from_home_button"
    }
}

private object BuildConfigVersion {
    fun name(activity: ComponentActivity): String = runCatching {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
