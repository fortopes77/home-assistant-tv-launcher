package com.fortopes.tvlauncher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.ListItem
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import com.fortopes.tvlauncher.R
import com.fortopes.tvlauncher.data.ContinueItem
import com.fortopes.tvlauncher.data.HaEntity
import com.fortopes.tvlauncher.data.HomeState
import com.fortopes.tvlauncher.data.SeerrItem
import com.fortopes.tvlauncher.data.TvApp
import com.fortopes.tvlauncher.data.TvInput
import kotlinx.coroutines.delay
import java.util.Date

/** What the big title at the top shows: whatever the remote is on. */
private data class Spotlight(val title: String, val subtitle: String, val color: Color)

/**
 * The rows on the home screen, top to bottom.
 */
private sealed interface HomeRow {
    val key: String
    val title: String

    data class ContinueWatching(val items: List<ContinueItem>, val showSetup: Boolean) : HomeRow {
        override val key = "continue"
        override val title = "Continue watching"
    }

    data class Apps(val apps: List<TvApp>) : HomeRow {
        override val key = "apps"
        override val title = "Apps"
    }

    /** Seerr's trending and popular films and series. */
    data class Recommended(val items: List<SeerrItem>, val showSetup: Boolean) : HomeRow {
        override val key = "recommended"
        override val title = "Recommended"
    }

    /** Home Assistant: a camera, what's playing, the TV room's lights and blinds, scenes and scripts, the rest of the house's lights and blinds, the full dashboard. */
    data class Home(val state: HomeState?) : HomeRow {
        override val key = "home"
        override val title = "Home"
    }

    data class Inputs(val inputs: List<TvInput>) : HomeRow {
        override val key = "inputs"
        override val title = "Inputs"
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    continueItems: List<ContinueItem>,
    continueNeedsSetup: Boolean,
    apps: List<TvApp>,
    recommended: List<SeerrItem>,
    seerrNeedsSetup: Boolean,
    canPlayJellyfin: Boolean,
    inputs: List<TvInput>,
    home: HomeState?,
    loadCamera: suspend (String) -> ImageBitmap?,
    loaded: Boolean,
    lightMode: Boolean,
    settingsOpen: Boolean,
    resetSignal: Int,
    onMoveApp: (TvApp, Int) -> Unit,
    onHideApp: (TvApp) -> Unit,
    onAppInfo: (TvApp) -> Unit,
    onOpenContinue: (ContinueItem) -> Unit,
    onOpenApp: (TvApp) -> Unit,
    onOpenInput: (TvInput) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onHaRun: (HaEntity) -> Unit,
    onOpenCamera: (Int) -> Unit,
    onOpenDashboard: () -> Unit,
    onSetUpHa: () -> Unit,
    onSetUpSeerr: () -> Unit,
    onSeerrTrailer: (SeerrItem) -> Unit,
    onSeerrPlay: (SeerrItem) -> Unit,
    onSeerrRequest: (SeerrItem) -> Unit,
) {
    val rows = remember(continueItems, continueNeedsSetup, apps, recommended, seerrNeedsSetup, inputs, home) {
        buildList {
            if (continueItems.isNotEmpty() || continueNeedsSetup) {
                add(HomeRow.ContinueWatching(continueItems, showSetup = continueItems.isEmpty()))
            }
            if (apps.isNotEmpty()) add(HomeRow.Apps(apps))
            if (recommended.isNotEmpty() || seerrNeedsSetup) add(HomeRow.Recommended(recommended, showSetup = seerrNeedsSetup))
            add(HomeRow.Home(home))
            if (inputs.isNotEmpty()) add(HomeRow.Inputs(inputs))
        }
    }

    var spotlight by remember { mutableStateOf<Spotlight?>(null) }
    // Light mode keeps one colour, so the background never has to redraw.
    val accent by animateColorAsState(
        targetValue = if (lightMode) TvColors.Purple else spotlight?.color ?: TvColors.Purple,
        animationSpec = if (lightMode) snap<Color>() else tween<Color>(durationMillis = 600),
        label = "accent",
    )

    // Hold OK on an app: a small menu, then Move (left and right) or Hide.
    var menuApp by remember { mutableStateOf<TvApp?>(null) }
    var movingKey by remember { mutableStateOf<String?>(null) }
    var menuReturnKey by remember { mutableStateOf<String?>(null) }
    val appFocus = remember { HashMap<String, FocusRequester>() }
    val gearFocus = remember { FocusRequester() }
    var settingsWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) settingsWasOpen = true
        else if (settingsWasOpen) {
            delay(50)
            runCatching { gearFocus.requestFocus() }
        }
    }
    BackHandler(enabled = menuApp != null) { menuApp = null }

    // OK on a Recommended card: its details in a flyout. Kept by key so it updates after a request.
    var seerrKey by remember { mutableStateOf<String?>(null) }
    val seerrFocus = remember { HashMap<String, FocusRequester>() }
    BackHandler(enabled = seerrKey != null) { seerrKey = null }

    val columnState = rememberLazyListState()
    val rowStates = remember { HashMap<String, LazyListState>() }
    val firstCard = remember { FocusRequester() }

    // Start on the first card, and go back to it whenever Home is pressed while we're showing.
    LaunchedEffect(resetSignal, loaded) {
        if (!loaded) return@LaunchedEffect
        columnState.scrollToItem(0)
        rowStates.values.forEach { it.scrollToItem(0) }
        delay(50)
        runCatching { firstCard.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .drawBehind {
                // Deep purple into deep blue, with a purple glow bottom left and a blue one top right.
                drawRect(
                    Brush.linearGradient(
                        listOf(TvColors.BgPurple, TvColors.BgIndigo, TvColors.BgBlue),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(TvColors.Purple.copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.95f),
                        radius = size.width * 0.55f,
                    ),
                )
                // The focused app's colour (blue in light mode), top right.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf((if (lightMode) TvColors.Blue else accent).copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.05f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, TvColors.Background.copy(alpha = 0.45f))))
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(home?.weather, onOpenLauncherSettings, gearFocus)

            // The spotlight: the name of what's focused. Step 2 puts the continue-watching artwork here.
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 48.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = spotlight?.title ?: "",
                    color = TvColors.Text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = spotlight?.subtitle ?: "", color = TvColors.Muted, fontSize = 15.sp)
            }

            LazyColumn(
                state = columnState,
                contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(rows, key = { it.key }) { row ->
                    val isFirstRow = row.key == rows.first().key
                    val rowState = rowStates.getOrPut(row.key) { LazyListState() }
                    Column {
                        Text(
                            text = row.title,
                            color = TvColors.Text.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                        LazyRow(
                            state = rowState,
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            // Coming back to a row lands on the card you left, like Projectivy.
                            modifier = Modifier.focusRestorer(),
                        ) {
                            when (row) {
                                is HomeRow.ContinueWatching -> {
                                    itemsIndexed(row.items, key = { _, item -> item.key }) { i, item ->
                                        ContinueCard(
                                            item = item,
                                            onClick = { onOpenContinue(item) },
                                            modifier = Modifier
                                                .firstIf(isFirstRow && i == 0, firstCard)
                                                .onFocusChanged {
                                                    if (it.isFocused) {
                                                        val sub = listOf(item.subtitle, item.sourceLabel).filter { s -> s.isNotBlank() }
                                                        spotlight = Spotlight(item.title, sub.joinToString(" · "), TvColors.Purple)
                                                    }
                                                },
                                        )
                                    }
                                    if (row.showSetup) {
                                        item(key = "continue-setup") {
                                            GlyphCard(
                                                label = "Set up",
                                                icon = R.drawable.ic_settings,
                                                onClick = onOpenLauncherSettings,
                                                modifier = Modifier
                                                    .firstIf(isFirstRow, firstCard)
                                                    .onFocusChanged {
                                                        if (it.isFocused) {
                                                            spotlight = Spotlight(
                                                                "Continue watching",
                                                                "Sign in to Jellyfin and let TV Home see what other apps are playing",
                                                                TvColors.Purple,
                                                            )
                                                        }
                                                    },
                                            )
                                        }
                                    }
                                }
                                is HomeRow.Apps -> itemsIndexed(row.apps, key = { _, app -> app.key }) { i, app ->
                                    val moving = movingKey == app.key
                                    val bringIntoView = remember { BringIntoViewRequester() }
                                    // Keep the card in view as it moves along the row.
                                    if (moving) LaunchedEffect(i) { runCatching { bringIntoView.bringIntoView() } }
                                    AppCard(
                                        app = app,
                                        onClick = { onOpenApp(app) },
                                        onLongClick = {
                                            menuReturnKey = app.key
                                            menuApp = app
                                        },
                                        moving = moving,
                                        modifier = Modifier
                                            .firstIf(isFirstRow && i == 0, firstCard)
                                            .focusRequester(appFocus.getOrPut(app.key) { FocusRequester() })
                                            .bringIntoViewRequester(bringIntoView)
                                            .onPreviewKeyEvent { e ->
                                                if (!moving) return@onPreviewKeyEvent false
                                                when (e.key) {
                                                    Key.DirectionLeft, Key.DirectionRight -> {
                                                        if (e.type == KeyEventType.KeyDown) onMoveApp(app, if (e.key == Key.DirectionLeft) -1 else 1)
                                                        true
                                                    }
                                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
                                                        if (e.type == KeyEventType.KeyUp) {
                                                            movingKey = null
                                                            spotlight = Spotlight(app.label, "App", app.color)
                                                        }
                                                        true
                                                    }
                                                    else -> true // stay on the card until you're done
                                                }
                                            }
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    spotlight = if (moving) Spotlight(app.label, "Moving · left and right to move it · OK when done", app.color)
                                                    else Spotlight(app.label, "App · hold OK to move or hide it", app.color)
                                                }
                                            },
                                    )
                                }
                                is HomeRow.Recommended -> {
                                    itemsIndexed(row.items, key = { _, item -> item.key }) { i, item ->
                                        RecommendedCard(
                                            item = item,
                                            onClick = { seerrKey = item.key },
                                            modifier = Modifier
                                                .firstIf(isFirstRow && i == 0, firstCard)
                                                .focusRequester(seerrFocus.getOrPut(item.key) { FocusRequester() })
                                                .onFocusChanged {
                                                    if (it.isFocused) {
                                                        val sub = listOfNotNull(item.kind, item.year.ifBlank { null }, item.source, item.statusLabel.ifBlank { null })
                                                        spotlight = Spotlight(item.title, sub.joinToString(" · ") + " · OK for trailer and more", TvColors.Blue)
                                                    }
                                                },
                                        )
                                    }
                                    if (row.showSetup) {
                                        item(key = "seerr-setup") {
                                            GlyphCard(
                                                label = "Set up Seerr",
                                                icon = R.drawable.ic_movie,
                                                onClick = onSetUpSeerr,
                                                modifier = Modifier
                                                    .firstIf(isFirstRow, firstCard)
                                                    .onFocusChanged {
                                                        if (it.isFocused) spotlight = Spotlight("Recommended", "Connect Seerr for trending films and series, trailers and requests", TvColors.Blue)
                                                    },
                                            )
                                        }
                                    }
                                }
                                is HomeRow.Home -> {
                                    val state = row.state
                                    fun Modifier.spot(title: String, sub: String, i: Int) = this
                                        .firstIf(isFirstRow && i == 0, firstCard)
                                        .onFocusChanged { if (it.isFocused) spotlight = Spotlight(title, sub, TvColors.Cyan) }
                                    if (state == null) {
                                        item(key = "ha-setup") {
                                            GlyphCard(
                                                label = "Connect",
                                                icon = R.drawable.ic_home_glyph,
                                                onClick = onSetUpHa,
                                                modifier = Modifier.spot("Home Assistant", "Connect from your phone with a QR code", 0),
                                            )
                                        }
                                    } else if (state.error != null) {
                                        item(key = "ha-error") {
                                            GlyphCard(
                                                label = "Reconnect",
                                                icon = R.drawable.ic_home_glyph,
                                                onClick = onSetUpHa,
                                                modifier = Modifier.spot(state.error, "Press OK to connect again", 0),
                                            )
                                        }
                                    } else {
                                        var n = 0
                                        state.cameras.firstOrNull()?.let { cam ->
                                            val i = n++
                                            item(key = "cam") {
                                                CameraCard(
                                                    name = cam.name,
                                                    loadImage = { loadCamera(cam.id) },
                                                    onClick = { onOpenCamera(0) },
                                                    modifier = Modifier.spot(cam.name, "Camera · OK for full screen, left and right for others", i),
                                                )
                                            }
                                        }
                                        state.nowPlaying?.let { p ->
                                            val i = n++
                                            val title = p.attributes.optString("media_title").ifBlank { p.name }
                                            item(key = "playing") {
                                                GlyphCard(
                                                    label = title,
                                                    icon = R.drawable.ic_music,
                                                    onClick = { onHaRun(p) },
                                                    active = p.state == "playing",
                                                    detail = p.name,
                                                    modifier = Modifier.spot(title, "${p.name} · OK to ${if (p.state == "playing") "pause" else "play"}", i),
                                                )
                                            }
                                        }
                                        state.roomControls.forEach { c ->
                                            val i = n++
                                            item(key = c.id) { ControlCard(c, onClick = { onHaRun(c) }, modifier = Modifier.spot(c.name, controlHint(c), i)) }
                                        }
                                        state.actions.forEach { a ->
                                            val i = n++
                                            item(key = a.id) {
                                                val look = actionStyle(a)
                                                GlyphCard(
                                                    label = a.name,
                                                    icon = look.icon,
                                                    colors = look.colors,
                                                    onClick = { onHaRun(a) },
                                                    modifier = Modifier.spot(a.name, if (a.domain == "scene") "Scene · OK to set it" else "Script · OK to run it", i),
                                                )
                                            }
                                        }
                                        state.otherControls.forEach { c ->
                                            val i = n++
                                            item(key = c.id) { ControlCard(c, onClick = { onHaRun(c) }, modifier = Modifier.spot(c.name, controlHint(c), i)) }
                                        }
                                        val i = n
                                        item(key = "dashboard") {
                                            GlyphCard(
                                                label = "Full dashboard",
                                                icon = R.drawable.ic_dashboard,
                                                onClick = onOpenDashboard,
                                                modifier = Modifier.spot("Full dashboard", "Your Home Assistant TV screen: cameras, calendar, solar, rooms", i),
                                            )
                                        }
                                    }
                                }
                                is HomeRow.Inputs -> {
                                    itemsIndexed(row.inputs, key = { _, input -> input.id }) { i, input ->
                                        GlyphCard(
                                            label = input.label,
                                            icon = R.drawable.ic_input,
                                            onClick = { onOpenInput(input) },
                                            modifier = Modifier
                                                .firstIf(isFirstRow && i == 0, firstCard)
                                                .onFocusChanged {
                                                    if (it.isFocused) spotlight = Spotlight(input.label, "TV input", TvColors.Blue)
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        seerrKey?.let { key ->
            val item = recommended.firstOrNull { it.key == key }
            if (item != null) {
                SeerrPanel(
                    item = item,
                    canPlay = canPlayJellyfin && item.jellyfinId != null,
                    onTrailer = { onSeerrTrailer(item) },
                    onPlay = {
                        seerrKey = null
                        onSeerrPlay(item)
                    },
                    onRequest = { onSeerrRequest(item) },
                )
            }
        }

        menuApp?.let { app ->
            AppMenu(
                app = app,
                onMove = {
                    menuApp = null
                    movingKey = app.key
                    spotlight = Spotlight(app.label, "Moving · left and right to move it · OK when done", app.color)
                },
                onHide = {
                    appFocus.remove(app.key)
                    menuApp = null
                    onHideApp(app)
                },
                onInfo = {
                    menuApp = null
                    onAppInfo(app)
                },
            )
        }
    }

    // Closing the details puts focus back on the card.
    var lastSeerrKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seerrKey) {
        val open = seerrKey
        if (open != null) {
            lastSeerrKey = open
            return@LaunchedEffect
        }
        val back = lastSeerrKey ?: return@LaunchedEffect
        lastSeerrKey = null
        delay(50)
        runCatching { seerrFocus[back]?.requestFocus() }
    }

    // When the menu closes (Back, Move, or Hide), focus goes back to the app, or the first card if it's hidden.
    LaunchedEffect(menuApp) {
        val key = menuReturnKey
        if (menuApp != null || key == null) return@LaunchedEffect
        menuReturnKey = null
        delay(50)
        val back = appFocus[key]
        if (back == null || runCatching { back.requestFocus() }.isFailure) runCatching { firstCard.requestFocus() }
    }
}

/** The menu for an app, from holding OK: a small panel on the right, like Google TV's. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppMenu(app: TvApp, onMove: () -> Unit, onHide: () -> Unit, onInfo: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(app.key) { runCatching { first.requestFocus() } }
    // The OK that opened the menu is still held; ignore it until it's let go, so it doesn't pick "Move".
    var armed by remember { mutableStateOf(false) }
    SidePanel(
        width = 340.dp,
        modifier = Modifier.onPreviewKeyEvent { e ->
            val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            when {
                armed || !isOk -> false
                e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> {
                    armed = true // a fresh press: the first one was already let go
                    false
                }
                else -> {
                    if (e.type == KeyEventType.KeyUp) armed = true
                    true
                }
            }
        },
    ) {
        Text(app.label, color = TvColors.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
        ListItem(selected = false, onClick = onMove, modifier = Modifier.focusRequester(first), headlineContent = { Text("Move") },
            supportingContent = { Text("Left and right to move it, OK when done") })
        ListItem(selected = false, onClick = onHide, headlineContent = { Text("Hide") },
            supportingContent = { Text("Show it again from the gear") })
        ListItem(selected = false, onClick = onInfo, headlineContent = { Text("App info") },
            supportingContent = { Text("Android's page for it: update, clear, uninstall") })
    }
}

/**
 * A flyout from the right over a dimmed screen, like Google TV's settings. Focus can't leave it, so the
 * rows behind stay put until it closes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidePanel(
    width: androidx.compose.ui.unit.Dp = 440.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))) {
        Column(
            modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(width)
                .background(TvColors.Panel)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

private fun Modifier.firstIf(condition: Boolean, requester: FocusRequester): Modifier =
    if (condition) focusRequester(requester) else this

@Composable
private fun TopBar(weather: String?, onOpenLauncherSettings: () -> Unit, gearFocus: FocusRequester) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 40.dp, top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        if (weather != null) {
            Text(text = weather, color = TvColors.Muted, fontSize = 18.sp)
            Spacer(Modifier.width(20.dp))
        }
        Clock()
        Spacer(Modifier.width(16.dp))
        IconButton(
            onClick = onOpenLauncherSettings,
            modifier = Modifier.size(36.dp).focusRequester(gearFocus),
        ) {
            Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = "TV Home settings", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Text(
        text = DateFormat.getTimeFormat(context).format(now),
        color = TvColors.Text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** "Open", "Closed" or "Open 40%" for a blind; "On" or "Off" for a light. */
private fun controlState(c: HaEntity): String {
    if (c.domain != "cover") return if (c.isOn) "On" else "Off"
    val pos = c.attributes.optInt("current_position", -1)
    return when {
        c.state == "opening" -> "Opening"
        c.state == "closing" -> "Closing"
        pos in 1..99 -> "Open $pos%"
        c.state == "open" -> "Open"
        else -> "Closed"
    }
}

private fun controlHint(c: HaEntity): String =
    if (c.domain == "cover") "Blind · ${controlState(c)} · OK to ${if (c.isOn) "close" else "open"}"
    else "Light · OK to turn ${if (c.isOn) "off" else "on"}"

/** A light or blind in the Home row. */
@Composable
private fun ControlCard(c: HaEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val look = controlStyle(c)
    GlyphCard(
        label = c.name,
        icon = look.icon,
        colors = look.colors,
        onClick = onClick,
        active = c.isOn,
        detail = controlState(c),
        modifier = modifier,
    )
}
