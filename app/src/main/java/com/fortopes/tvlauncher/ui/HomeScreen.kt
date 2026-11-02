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
import com.fortopes.tvlauncher.data.TvApp
import com.fortopes.tvlauncher.data.TvInput
import kotlinx.coroutines.delay
import java.util.Date

/** What the big title at the top shows: whatever the remote is on. */
private data class Spotlight(val title: String, val subtitle: String, val color: Color)

/**
 * The rows on the home screen, top to bottom. Step 3 adds the Home Assistant panel based on the
 * dashboard's `tv` view.
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
    loaded: Boolean,
    resetSignal: Int,
    onOpenContinue: (ContinueItem) -> Unit,
    onOpenApp: (TvApp) -> Unit,
    onOpenInput: (TvInput) -> Unit,
    onOpenTvSettings: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
) {
    val rows = remember(continueItems, continueNeedsSetup, apps, inputs) {
        buildList {
            if (continueItems.isNotEmpty() || continueNeedsSetup) {
                add(HomeRow.ContinueWatching(continueItems, showSetup = continueItems.isEmpty()))
            }
            if (apps.isNotEmpty()) add(HomeRow.Apps(apps))
            add(HomeRow.Inputs(inputs)) // always shown: it holds the TV settings card
            // Step 3: add the Home Assistant panel here.
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
            TopBar(onOpenLauncherSettings)

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
private fun TopBar(onOpenLauncherSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 40.dp, top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
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
