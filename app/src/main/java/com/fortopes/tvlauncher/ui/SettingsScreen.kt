package com.fortopes.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import java.text.DateFormat
import java.util.Date

private fun formatTime(millis: Long): String =
    if (millis <= 0) "never" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@Composable
fun SettingsScreen(
    overrideEnabled: Boolean,
    serviceEnabled: Boolean,
    serviceStartedAt: Long,
    lastError: String?,
    jellyfinStatus: String?,
    nowPlayingAccess: Boolean,
    haStatus: String?,
    seerrStatus: String?,
    onSetUpSeerr: () -> Unit,
    onSignOutSeerr: () -> Unit,
    lightMode: Boolean,
    hiddenApps: List<Pair<String, String>>,
    onToggleLightMode: (Boolean) -> Unit,
    onUnhideApp: (String) -> Unit,
    version: String,
    onToggleOverride: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenStockHome: () -> Unit,
    onOpenTvSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onClearError: () -> Unit,
    onSetUpJellyfin: () -> Unit,
    onSignOutJellyfin: () -> Unit,
    onAllowNowPlaying: () -> Unit,
    onSetUpHa: () -> Unit,
    onSignOutHa: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    SidePanel {
        Text("Settings", color = TvColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Back closes this.",
            color = TvColors.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val rowModifier = Modifier

        ListItem(
            selected = false,
            onClick = onOpenTvSettings,
            modifier = rowModifier.focusRequester(first),
            headlineContent = { Text("TV settings") },
            supportingContent = { Text("Google TV's own settings: network, picture, sound, apps") },
        )

        Text("Home button", color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))

        ListItem(
            selected = false,
            onClick = { onToggleOverride(!overrideEnabled) },
            modifier = rowModifier,
            headlineContent = { Text("Open Fortopes Home with the Home button") },
            supportingContent = {
                Text(
                    if (overrideEnabled) "On: Home and Back out of apps come here."
                    else "Off: Home goes to the Google TV home screen as normal.",
                )
            },
            trailingContent = { Switch(checked = overrideEnabled, onCheckedChange = null) },
        )

        ListItem(
            selected = false,
            onClick = onOpenAccessibilitySettings,
            modifier = rowModifier,
            headlineContent = { Text("Home button service") },
            supportingContent = {
                Text(
                    if (serviceEnabled) "Turned on. The toggle above works. Last started ${formatTime(serviceStartedAt)}."
                    else "Turned off. Open Accessibility and turn on \"Fortopes Home: Home button\"." +
                        if (serviceStartedAt > 0) " It last ran ${formatTime(serviceStartedAt)}." else "",
                )
            },
            trailingContent = { Text(if (serviceEnabled) "On" else "Off", color = if (serviceEnabled) TvColors.Cyan else TvColors.Muted) },
        )

        if (!serviceEnabled) {
            ListItem(
                selected = false,
                onClick = onOpenAppInfo,
                modifier = rowModifier,
                headlineContent = { Text("Switch keeps turning itself off?") },
                supportingContent = {
                    Text(
                        if (serviceStartedAt > 0) {
                            "It started, then stopped. If there's an error below, tell Claude what it says. " +
                                "Press OK here to open Fortopes Home's app info."
                        } else {
                            "It has never started, so Android is blocking it. Android does this for apps that didn't " +
                                "come from the Play Store. Press OK here, and if the page has \"Allow restricted " +
                                "settings\" at the bottom, choose it. If not, see the README for the ADB command."
                        },
                    )
                },
            )
        }

        if (lastError != null) {
            ListItem(
                selected = false,
                onClick = onClearError,
                modifier = rowModifier,
                headlineContent = { Text("Last error (press OK to clear)") },
                supportingContent = { Text(lastError.lines().take(6).joinToString("\n"), fontSize = 11.sp) },
            )
        }

        Text("Continue watching", color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))

        ListItem(
            selected = false,
            onClick = onSetUpJellyfin,
            modifier = rowModifier,
            headlineContent = { Text("Jellyfin") },
            supportingContent = {
                Text(jellyfinStatus ?: "Not signed in. Press OK to sign in with a code from the Jellyfin app on your phone.")
            },
        )
        if (jellyfinStatus != null) {
            ListItem(
                selected = false,
                onClick = onSignOutJellyfin,
                modifier = rowModifier,
                headlineContent = { Text("Sign out of Jellyfin") },
            )
        }

        ListItem(
            selected = false,
            onClick = onAllowNowPlaying,
            modifier = rowModifier,
            headlineContent = { Text("Disney+, Binge and other apps") },
            supportingContent = {
                Text(
                    if (nowPlayingAccess) "On. Fortopes Home remembers the last show each app played, and OK opens that app."
                    else "Off. Fortopes Home needs notification access to see what's playing. Android blocks it for sideloaded " +
                        "apps, so turn it on with the ADB command in the README.",
                )
            },
            trailingContent = { Text(if (nowPlayingAccess) "On" else "Off", color = if (nowPlayingAccess) TvColors.Cyan else TvColors.Muted) },
        )

        ListItem(
            selected = false,
            onClick = onSetUpSeerr,
            modifier = rowModifier,
            headlineContent = { Text("Seerr") },
            supportingContent = { Text(seerrStatus ?: "Not connected. Press OK to connect, for the Recommended row with trailers and requests.") },
        )
        if (seerrStatus != null) {
            ListItem(selected = false, onClick = onSignOutSeerr, modifier = rowModifier, headlineContent = { Text("Disconnect Seerr") })
        }

        Text("Home Assistant", color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))

        ListItem(
            selected = false,
            onClick = onSetUpHa,
            modifier = rowModifier,
            headlineContent = { Text("Home Assistant") },
            supportingContent = {
                Text(haStatus ?: "Not connected. Press OK to connect, typing on the TV with the Google Home app's keyboard or on your phone with the QR code.")
            },
        )
        if (haStatus != null) {
            ListItem(
                selected = false,
                onClick = onSignOutHa,
                modifier = rowModifier,
                headlineContent = { Text("Disconnect Home Assistant") },
            )
        }

        Text("Look", color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))

        ListItem(
            selected = false,
            onClick = { onToggleLightMode(!lightMode) },
            modifier = rowModifier,
            headlineContent = { Text("Light mode for slow TVs") },
            supportingContent = {
                Text(
                    if (lightMode) "On: no colour glow or fades, so moving around stays quick."
                    else "Off: the background takes on the colour of what you're on.",
                )
            },
            trailingContent = { Switch(checked = lightMode, onCheckedChange = null) },
        )

        Text(
            "To move or hide an app, hold OK on it in the Apps row.",
            color = TvColors.Muted,
            fontSize = 13.sp,
            modifier = rowModifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        )
        hiddenApps.forEach { (key, label) ->
            ListItem(
                selected = false,
                onClick = { onUnhideApp(key) },
                modifier = rowModifier,
                headlineContent = { Text(label) },
                supportingContent = { Text("Hidden from Fortopes Home. Press OK to show it again.") },
            )
        }

        Text("Other", color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))

        ListItem(
            selected = false,
            onClick = onOpenStockHome,
            modifier = rowModifier,
            headlineContent = { Text("Open the Google TV home") },
            supportingContent = { Text("Once, without turning the toggle off. The next time you leave an app you come back here.") },
        )

        Text("Version $version", color = TvColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
    }
}
