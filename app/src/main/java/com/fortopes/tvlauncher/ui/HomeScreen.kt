package com.fortopes.tvlauncher.ui

import android.text.format.DateFormat
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

    /** Home Assistant: a camera, scenes and scripts, the TV room's lights, what's playing, the full dashboard. */
    data class Home(val state: HomeState?) : HomeRow {
        override val key = "home"
        override val title = "Home"
    }

    data class Inputs(val inputs: List<TvInput>) : HomeRow {
        override val key = "inputs"
        override val title = "Inputs & settings"
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    continueItems: List<ContinueItem>,
    continueNeedsSetup: Boolean,
    apps: List<TvApp>,
    inputs: List<TvInput>,
    home: HomeState?,
    loadCamera: suspend (String) -> ImageBitmap?,
    loaded: Boolean,
    resetSignal: Int,
    onOpenContinue: (ContinueItem) -> Unit,
    onOpenApp: (TvApp) -> Unit,
    onOpenInput: (TvInput) -> Unit,
    onOpenTvSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    onHaRun: (HaEntity) -> Unit,
    onOpenCamera: (Int) -> Unit,
    onOpenDashboard: () -> Unit,
    onSetUpHa: () -> Unit,
) {
    val rows = remember(continueItems, continueNeedsSetup, apps, inputs, home) {
        buildList {
            if (continueItems.isNotEmpty() || continueNeedsSetup) {
                add(HomeRow.ContinueWatching(continueItems, showSetup = continueItems.isEmpty()))
            }
            if (apps.isNotEmpty()) add(HomeRow.Apps(apps))
            add(HomeRow.Home(home))
            add(HomeRow.Inputs(inputs)) // always shown: it holds the TV settings card
        }
    }

    var spotlight by remember { mutableStateOf<Spotlight?>(null) }
    val accent by animateColorAsState(
        targetValue = spotlight?.color ?: TvColors.Purple,
        animationSpec = tween(durationMillis = 600),
        label = "accent",
    )

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
                // A soft glow in the focused app's colour, top right, fading into the dark.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.05f),
                        radius = size.width * 0.7f,
                    ),
                )
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, TvColors.Background.copy(alpha = 0.85f))))
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(home?.weather, onOpenLauncherSettings)

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
                                    AppCard(
                                        app = app,
                                        onClick = { onOpenApp(app) },
                                        modifier = Modifier
                                            .firstIf(isFirstRow && i == 0, firstCard)
                                            .onFocusChanged {
                                                if (it.isFocused) spotlight = Spotlight(app.label, "App", app.color)
                                            },
                                    )
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
                                        state.lights.forEach { light ->
                                            val i = n++
                                            item(key = light.id) {
                                                GlyphCard(
                                                    label = light.name,
                                                    icon = R.drawable.ic_bulb,
                                                    onClick = { onHaRun(light) },
                                                    active = light.isOn,
                                                    detail = if (light.isOn) "On" else "Off",
                                                    modifier = Modifier.spot(light.name, "Light · OK to turn ${if (light.isOn) "off" else "on"}", i),
                                                )
                                            }
                                        }
                                        state.actions.forEach { a ->
                                            val i = n++
                                            item(key = a.id) {
                                                GlyphCard(
                                                    label = a.name,
                                                    icon = R.drawable.ic_play,
                                                    onClick = { onHaRun(a) },
                                                    modifier = Modifier.spot(a.name, if (a.domain == "scene") "Scene · OK to set it" else "Script · OK to run it", i),
                                                )
                                            }
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
                                    item(key = "tv-settings") {
                                        GlyphCard(
                                            label = "TV settings",
                                            icon = R.drawable.ic_settings,
                                            onClick = onOpenTvSettings,
                                            modifier = Modifier
                                                .firstIf(isFirstRow && row.inputs.isEmpty(), firstCard)
                                                .onFocusChanged {
                                                    if (it.isFocused) spotlight = Spotlight("TV settings", "Google TV settings", TvColors.Cyan)
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
    }
}

private fun Modifier.firstIf(condition: Boolean, requester: FocusRequester): Modifier =
    if (condition) focusRequester(requester) else this

@Composable
private fun TopBar(weather: String?, onOpenLauncherSettings: () -> Unit) {
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
            modifier = Modifier.size(36.dp),
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
