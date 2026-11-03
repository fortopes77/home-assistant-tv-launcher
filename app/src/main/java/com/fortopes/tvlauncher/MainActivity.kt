package com.fortopes.tvlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
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
import com.fortopes.tvlauncher.data.LiveChannel
import com.fortopes.tvlauncher.data.NowPlayingStore
import com.fortopes.tvlauncher.data.WatchNextRepository
import com.fortopes.tvlauncher.ui.JellyfinSetupScreen
import com.fortopes.tvlauncher.data.InputRepository
import com.fortopes.tvlauncher.data.LauncherPrefs
import com.fortopes.tvlauncher.data.TvApp
import com.fortopes.tvlauncher.data.SeerrClient
import com.fortopes.tvlauncher.data.SeerrItem
import com.fortopes.tvlauncher.data.SeerrStore
import com.fortopes.tvlauncher.ui.SeerrSetupScreen
import com.fortopes.tvlauncher.data.TvInput
import com.fortopes.tvlauncher.home.HomeButtonService
import com.fortopes.tvlauncher.ui.HomeScreen
import com.fortopes.tvlauncher.ui.SettingsScreen
import com.fortopes.tvlauncher.ui.TvHomeTheme
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.fortopes.tvlauncher.data.HaClient
import com.fortopes.tvlauncher.data.HaEntity
import com.fortopes.tvlauncher.data.HaLive
import com.fortopes.tvlauncher.data.HttpException
import com.fortopes.tvlauncher.data.HaStore
import com.fortopes.tvlauncher.data.HomeRepository
import com.fortopes.tvlauncher.data.HomeState
import com.fortopes.tvlauncher.ui.CameraScreen
import com.fortopes.tvlauncher.ui.DashboardScreen
import com.fortopes.tvlauncher.ui.HaSetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val STALE_REPEAT_MS = 60L
private const val REPEAT_GAP_MS = 110L

class MainActivity : ComponentActivity() {
    private var lastRepeatAt = 0L

    /**
     * Holding an arrow sends a repeat about every 50 ms. A slow TV can't move and scroll that fast,
     * so the repeats queue up and focus keeps sliding after the button is let go. Drop repeats that
     * waited in the queue (the button may already be up) and space the rest out, so focus stops
     * when the button does.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount > 0 && isArrow(event.keyCode)) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - event.eventTime > STALE_REPEAT_MS || now - lastRepeatAt < REPEAT_GAP_MS) return true
            lastRepeatAt = now
        }
        val handled = super.dispatchKeyEvent(event)
        if (handled && event.action == android.view.KeyEvent.ACTION_DOWN) playKeySound(event)
        return handled
    }

    /**
     * The TV's own little click as you move around and press OK, like the stock home screen. Android
     * plays it only when System sounds is on in the TV's settings, so turning that off silences it.
     * Only for keys that did something, so the end of a row stays quiet.
     */
    private fun playKeySound(event: android.view.KeyEvent) {
        val effect = when (event.keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> android.view.SoundEffectConstants.NAVIGATION_LEFT
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> android.view.SoundEffectConstants.NAVIGATION_RIGHT
            android.view.KeyEvent.KEYCODE_DPAD_UP -> android.view.SoundEffectConstants.NAVIGATION_UP
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> android.view.SoundEffectConstants.NAVIGATION_DOWN
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (event.repeatCount == 0) android.view.SoundEffectConstants.CLICK else return
            else -> return
        }
        window.decorView.playSoundEffect(effect)
    }

    private fun isArrow(code: Int) = code == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
        code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ||
        code == android.view.KeyEvent.KEYCODE_DPAD_UP ||
        code == android.view.KeyEvent.KEYCODE_DPAD_DOWN


    private lateinit var prefs: LauncherPrefs
    private lateinit var appRepo: AppRepository
    private lateinit var inputRepo: InputRepository
    private lateinit var jellyfinStore: JellyfinStore
    private lateinit var jellyfin: JellyfinClient
    private lateinit var watchNext: WatchNextRepository
    private lateinit var nowPlaying: NowPlayingStore
    private lateinit var continueRepo: ContinueWatchingRepository
    private lateinit var haStore: HaStore
    private lateinit var ha: HaClient
    private lateinit var homeRepo: HomeRepository
    private lateinit var seerrStore: SeerrStore
    private lateinit var seerr: SeerrClient
    private var recommended by mutableStateOf<List<SeerrItem>>(emptyList())
    private var seerrStatus by mutableStateOf<String?>(null)
    private var recommendedAt = 0L
    private var homePolling: Job? = null
    private lateinit var haLive: HaLive
    /** True while Home Assistant's live connection is up, so changes arrive by themselves. */
    @Volatile private var haLiveUp = false
    private var appsLoaded = false
    private var inputsAt = 0L
    private var channelsAt = 0L

    private val loadLock = Mutex()

    private var apps by mutableStateOf<List<TvApp>>(emptyList())
    private var inputs by mutableStateOf<List<TvInput>>(emptyList())
    private var tuners by mutableStateOf<List<TvInput>>(emptyList())
    private var channels by mutableStateOf<List<LiveChannel>>(emptyList())
    private var loaded by mutableStateOf(false)
    /** Every app, before your order and hidden apps are applied; [apps] is what the row shows. */
    private var allApps: List<TvApp> = emptyList()
    private var hiddenApps by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var lightMode by mutableStateOf(false)
    private enum class Screen { Home, Settings, JellyfinSetup, HaSetup, SeerrSetup, Camera, Dashboard }

    private var screen by mutableStateOf(Screen.Home)
    private var continueItems by mutableStateOf<List<ContinueItem>>(emptyList())
    private var jellyfinStatus by mutableStateOf<String?>(null)
    private var nowPlayingAccess by mutableStateOf(false)
    private var overrideEnabled by mutableStateOf(true)
    private var serviceEnabled by mutableStateOf(false)
    private var serviceStartedAt by mutableStateOf(0L)
    private var lastError by mutableStateOf<String?>(null)
    private var resetSignal by mutableIntStateOf(0)
    private var home by mutableStateOf<HomeState?>(null)
    private var haStatus by mutableStateOf<String?>(null)
    private var cameraStart by mutableIntStateOf(0)
    private var dashboardUrl = ""
    /** Where Back from Home Assistant setup goes: the screen it was opened from. */
    private var haSetupReturn = Screen.Home

    /** Asks for "TV listings", which lets the Continue watching row show Google TV's watch next entries. */
    private val askTvListings = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        tvListingsAllowed = it
        reloadContinue()
    }
    private var tvListingsAllowed by mutableStateOf(false)

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
        haStore = HaStore(this)
        ha = HaClient(haStore)
        homeRepo = HomeRepository(haStore, ha)
        haLive = HaLive(haStore)
        seerrStore = SeerrStore(this)
        seerr = SeerrClient(seerrStore)

        setContent {
            TvHomeTheme {
                when (screen) {
                    Screen.HaSetup -> {
                        BackHandler { screen = haSetupReturn }
                        HaSetupScreen(store = haStore, client = ha, onDone = {
                            screen = haSetupReturn
                            refreshStatus()
                            startHomePolling()
                        })
                    }
                    Screen.SeerrSetup -> {
                        BackHandler { screen = Screen.Settings }
                        SeerrSetupScreen(store = seerrStore, client = seerr, onDone = {
                            screen = Screen.Home
                            refreshStatus()
                            reloadRecommended(force = true)
                        })
                    }
                    Screen.Camera -> {
                        BackHandler { screen = Screen.Home }
                        CameraScreen(cameras = home?.cameras.orEmpty(), start = cameraStart, loadImage = ::loadCamera)
                    }
                    Screen.Dashboard -> {
                        DashboardScreen(url = dashboardUrl, onClose = { screen = Screen.Home })
                    }
                    Screen.JellyfinSetup -> {
                        BackHandler { screen = Screen.Settings }
                        JellyfinSetupScreen(client = jellyfin, onDone = {
                            screen = Screen.Settings
                            refreshStatus()
                            channelsAt = 0L
                            reloadContinue()
                        })
                    }
                    // Settings is a flyout over the home screen, so the rows stay where you left them.
                    Screen.Home, Screen.Settings -> {
                        // On the home screen Back has nowhere to go; with settings open it closes them.
                        BackHandler { if (screen == Screen.Settings) screen = Screen.Home }
                        Box(Modifier.fillMaxSize()) {
                            HomeScreen(
                            continueItems = continueItems,
                            continueNeedsSetup = jellyfinStatus == null || !nowPlayingAccess,
                            apps = apps,
                            recommended = recommended,
                            seerrNeedsSetup = seerrStatus == null,
                            canPlayJellyfin = jellyfinStore.isSignedIn,
                            inputs = inputs,
                            tuners = tuners,
                            channels = channels,
                            home = home,
                            loadCamera = ::loadCameraThumb,
                            loaded = loaded,
                            lightMode = lightMode,
                            settingsOpen = screen == Screen.Settings,
                            resetSignal = resetSignal,
                            onMoveApp = ::moveApp,
                            onHideApp = {
                                appRepo.hide(it)
                                applyArrangement()
                                toast("${it.label} hidden. Show it again from the gear.")
                            },
                            onAppInfo = {
                                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.component.packageName, null)))
                            },
                            onOpenContinue = {
                                if (!continueRepo.open(it, (application as TvHomeApp).scope)) toast("Couldn't open ${it.sourceLabel}")
                            },
                            onOpenApp = { if (!appRepo.launch(it)) toast("Couldn't open ${it.label}") },
                            onOpenInput = { if (!inputRepo.open(it)) toast("This TV didn't open ${it.label}") },
                            onOpenTuner = { if (!inputRepo.openTuner(it)) toast("This TV didn't open ${it.label}") },
                            onOpenChannel = { channel ->
                                val play = ContinueItem(
                                    key = "channel:${channel.id}", title = channel.name, subtitle = "", imageUrl = null, progress = null,
                                    sourceLabel = "Jellyfin", sourcePackage = JellyfinClient.JELLYFIN_PACKAGE, lastWatched = 0L,
                                    action = ContinueItem.Action.Jellyfin(channel.id, 0L),
                                )
                                if (!continueRepo.open(play, (application as TvHomeApp).scope)) toast("Couldn't open Jellyfin")
                            },
                            onOpenJellyfinGuide = {
                                val launch = packageManager.getLeanbackLaunchIntentForPackage(JellyfinClient.JELLYFIN_PACKAGE)
                                    ?: packageManager.getLaunchIntentForPackage(JellyfinClient.JELLYFIN_PACKAGE)
                                if (launch == null) toast("Jellyfin isn't installed") else open(launch)
                            },
                            onOpenLauncherSettings = { screen = Screen.Settings },
                            onHaRun = ::runHa,
                            onOpenCamera = {
                                cameraStart = it
                                screen = Screen.Camera
                            },
                            onOpenDashboard = {
                                // Looking up a .local address can touch the network, so not on the main thread.
                                lifecycleScope.launch {
                                    dashboardUrl = withContext(Dispatchers.IO) { ha.dashboardUrl() }
                                    screen = Screen.Dashboard
                                }
                            },
                            onSetUpHa = {
                                haSetupReturn = Screen.Home
                                screen = Screen.HaSetup
                            },
                            onSetUpSeerr = { screen = Screen.SeerrSetup },
                            onSeerrTrailer = ::playTrailer,
                            onSeerrPlay = { item ->
                                val id = item.jellyfinId.orEmpty()
                                val play = ContinueItem(
                                    key = item.key, title = item.title, subtitle = "", imageUrl = null, progress = null,
                                    sourceLabel = "Jellyfin", sourcePackage = JellyfinClient.JELLYFIN_PACKAGE, lastWatched = 0L,
                                    action = ContinueItem.Action.Jellyfin(id, 0L),
                                )
                                if (id.isEmpty() || !continueRepo.open(play, (application as TvHomeApp).scope)) toast("Couldn't open Jellyfin")
                            },
                            onSeerrRequest = ::requestSeerr,
                        )
                            if (screen == Screen.Settings) {
                                SettingsScreen(
                            overrideEnabled = overrideEnabled,
                            serviceEnabled = serviceEnabled,
                            serviceStartedAt = serviceStartedAt,
                            lastError = lastError,
                            jellyfinStatus = jellyfinStatus,
                            nowPlayingAccess = nowPlayingAccess,
                            tvListingsAllowed = tvListingsAllowed,
                            onAllowTvListings = {
                                if (tvListingsAllowed) {
                                    toast("Already on")
                                } else if (shouldShowRequestPermissionRationale(WatchNextRepository.PERMISSION) ||
                                    !getPreferences(MODE_PRIVATE).getBoolean("asked_tv_listings_twice", false)
                                ) {
                                    getPreferences(MODE_PRIVATE).edit().putBoolean("asked_tv_listings_twice", true).apply()
                                    askTvListings.launch(WatchNextRepository.PERMISSION)
                                } else {
                                    // Android won't ask again; the app's permissions page can turn it on.
                                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                                }
                            },
                            haStatus = haStatus,
                            seerrStatus = seerrStatus,
                            onSetUpSeerr = { screen = Screen.SeerrSetup },
                            onSignOutSeerr = {
                                seerrStore.signOut()
                                recommended = emptyList()
                                refreshStatus()
                            },
                            lightMode = lightMode,
                            hiddenApps = hiddenApps,
                            onToggleLightMode = {
                                prefs.lightMode = it
                                lightMode = it
                            },
                            onUnhideApp = {
                                appRepo.unhide(it)
                                applyArrangement()
                            },
                            version = BuildConfigVersion.name(this@MainActivity),
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
                            onSetUpHa = {
                                haSetupReturn = Screen.Settings
                                screen = Screen.HaSetup
                            },
                            onSignOutHa = {
                                haStore.signOut()
                                homePolling?.cancel()
                                home = null
                                refreshStatus()
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
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.bypassStockHome = false
        overrideEnabled = prefs.overrideEnabled
        lightMode = prefs.lightMode
        serviceEnabled = prefs.isServiceEnabled()
        refreshStatus()
        reload() // picks up apps installed or removed while we were away
        tvListingsAllowed = watchNext.hasPermission
        // Ask once, the first time; after that it's in the gear if you said no.
        if (!tvListingsAllowed && !getPreferences(MODE_PRIVATE).getBoolean("asked_tv_listings", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("asked_tv_listings", true).apply()
            runCatching { askTvListings.launch(WatchNextRepository.PERMISSION) }
        }
        reloadContinue()
        reloadRecommended()
        startHomePolling()
    }

    override fun onPause() {
        super.onPause()
        homePolling?.cancel()
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
                // A fresh start: show the apps from last time at once, then check what's installed now.
                if (allApps.isEmpty()) {
                    val cached = withContext(Dispatchers.IO) { appRepo.loadCached() }
                    if (cached.isNotEmpty()) {
                        allApps = cached
                        applyArrangement()
                        loaded = true
                    }
                }
                // Pressing Home shouldn't mean asking every app about itself: only look again when Android
                // says something was installed, updated or removed.
                val appsChanged = !appsLoaded || withContext(Dispatchers.IO) { appRepo.appsChanged() }
                if (appsChanged) {
                    val newApps = withContext(Dispatchers.IO) { appRepo.loadApps() }
                    appsLoaded = true
                    if (newApps != allApps) {
                        allApps = newApps
                        applyArrangement()
                    }
                }
                // Inputs hardly ever change; look again every few minutes, or when apps did.
                val now = System.currentTimeMillis()
                if (appsChanged || now - inputsAt > INPUTS_EVERY_MS) {
                    val (newInputs, newTuners) = withContext(Dispatchers.IO) { inputRepo.loadInputs() to inputRepo.loadTuners() }
                    if (newInputs != inputs) inputs = newInputs
                    if (newTuners != tuners) tuners = newTuners
                    inputsAt = now
                }
                loaded = true
            }
        }
    }

    /** Applies your order and hidden apps to [allApps]. */
    private fun applyArrangement() {
        apps = appRepo.arrange(allApps)
        val hide = appRepo.hidden
        hiddenApps = allApps.filter { it.key in hide }.map { it.key to it.label }
    }

    /** Moves an app one place left (-1) or right (+1) in the row, and remembers the order. */
    private fun moveApp(app: TvApp, by: Int) {
        val list = apps.toMutableList()
        val i = list.indexOfFirst { it.key == app.key }
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        apps = list
        appRepo.saveOrder(list)
    }

    /** Seerr's picks change slowly, so ask at most every half hour unless [force]d. */
    private fun reloadRecommended(force: Boolean = false) {
        if (!seerrStore.isSignedIn) return
        if (!force && System.currentTimeMillis() - recommendedAt < 30 * 60_000L && recommended.isNotEmpty()) return
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { runCatching { seerr.recommended() }.getOrNull() }
            if (!items.isNullOrEmpty()) {
                recommended = items
                recommendedAt = System.currentTimeMillis()
            }
        }
    }

    /** Opens the trailer in the YouTube app (or anything that plays YouTube links). */
    private fun playTrailer(item: SeerrItem) {
        lifecycleScope.launch {
            val key = withContext(Dispatchers.IO) { runCatching { seerr.trailerKey(item) }.getOrNull() }
            if (key == null) {
                toast("No trailer for ${item.title}")
                return@launch
            }
            val link = Uri.parse("https://www.youtube.com/watch?v=$key")
            val youtube = Intent(Intent.ACTION_VIEW, link).setPackage("com.google.android.youtube.tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { startActivity(youtube) }.isSuccess ||
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (!ok) toast("Couldn't open YouTube")
        }
    }

    private fun requestSeerr(item: SeerrItem) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { seerr.request(item) } }
            if (result.isSuccess) {
                toast("Requested ${item.title}")
                // Show it as requested straight away; the next reload brings Seerr's own status.
                recommended = recommended.map { if (it.key == item.key) it.copy(status = 2) else it }
            } else {
                toast("Seerr didn't take the request")
            }
        }
    }

    private fun refreshStatus() {
        serviceStartedAt = prefs.serviceStartedAt
        lastError = prefs.lastError
        nowPlayingAccess = nowPlaying.hasAccess()
        haStatus = if (haStore.isSignedIn) {
            "Connected to ${haStore.url}" + (haStore.room?.let { ". TV's room: $it." } ?: ".")
        } else {
            null
        }
        seerrStatus = if (seerrStore.isSignedIn) "Connected to ${seerrStore.url}." else null
        jellyfinStatus = if (jellyfinStore.isSignedIn) {
            "Signed in to ${jellyfinStore.serverName ?: jellyfinStore.url} as ${jellyfinStore.userName ?: "you"}."
        } else {
            null
        }
    }

    /**
     * Keeps the Home row fresh while Fortopes Home is showing: lights, what's playing, the weather. It uses
     * Home Assistant's live connection, which sends only what changed. If that can't connect, it asks for
     * everything every 30 seconds instead, and tries the live connection again each time.
     */
    private fun startHomePolling() {
        homePolling?.cancel()
        if (!haStore.isSignedIn) return
        homePolling = lifecycleScope.launch {
            while (isActive) {
                val ended = runCatching {
                    haLive.updates().collect { states ->
                        haLiveUp = true
                        home = withContext(Dispatchers.IO) { homeRepo.build(states) }
                        // Several changes at once (a scene turning on many lights) become one redraw.
                        delay(300)
                    }
                }.exceptionOrNull()
                haLiveUp = false
                if (!isActive) break
                if (ended is HttpException && ended.code == 401) {
                    home = homeRepo.failed(ended)
                    break
                }
                reloadHomeNow()
                delay(30_000)
            }
        }
    }

    private suspend fun reloadHomeNow() {
        home = withContext(Dispatchers.IO) { runCatching { homeRepo.load() }.getOrNull() }
    }

    private fun runHa(entity: HaEntity) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { homeRepo.run(entity) }.isSuccess }
            if (!ok) {
                toast("Home Assistant didn't answer")
            } else if (entity.domain == "script" || entity.domain == "scene") {
                toast("${entity.name}: done")
            }
            // The live connection shows the change by itself; otherwise ask once Home Assistant has caught up.
            if (!haLiveUp) {
                delay(600)
                reloadHomeNow()
            }
        }
    }

    /**
     * A camera picture small enough for a card. Home Assistant scales it down, and it's decoded smaller too,
     * so a 4K doorbell doesn't take tens of megabytes every 15 seconds.
     */
    private suspend fun loadCameraThumb(entityId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = ha.cameraImage(entityId, width = CAMERA_THUMB_WIDTH)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= CAMERA_THUMB_WIDTH) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
        }.getOrNull()
    }

    private suspend fun loadCamera(entityId: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = ha.cameraImage(entityId)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    private fun reloadContinue() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { continueRepo.load() }
            // Only redraw the row when something in it changed.
            if (result.items.map { it.looks() } != continueItems.map { it.looks() }) continueItems = result.items
            if (result.jellyfinError != null && jellyfinStore.isSignedIn) {
                jellyfinStatus = "${result.jellyfinError}. Press OK to sign in again."
            }
        }
        // Live TV channels and what's on them, from the same Jellyfin sign-in. What's on changes every half
        // hour or so, so there's no need to ask every time Home is pressed.
        val now = System.currentTimeMillis()
        if (now - channelsAt > CHANNELS_EVERY_MS) {
            channelsAt = now
            lifecycleScope.launch {
                val fresh = withContext(Dispatchers.IO) { runCatching { jellyfin.liveChannels() }.getOrNull() }
                if (fresh == null) channelsAt = 0L else if (fresh != channels) channels = fresh
            }
        }
    }

    /** What a Continue watching card shows (the open action isn't comparable). */
    private fun ContinueItem.looks() = listOf(key, title, subtitle, imageUrl, progress, sourceLabel)

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
        private const val INPUTS_EVERY_MS = 5 * 60_000L
        private const val CHANNELS_EVERY_MS = 10 * 60_000L
        private const val CAMERA_THUMB_WIDTH = 480
    }
}

private object BuildConfigVersion {
    fun name(activity: ComponentActivity): String = runCatching {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
    }.getOrNull() ?: "?"
}
