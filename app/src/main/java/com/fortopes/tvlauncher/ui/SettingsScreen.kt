package com.fortopes.tvlauncher.ui

import com.fortopes.tvlauncher.data.HaEntity
import com.fortopes.tvlauncher.data.HomeRows
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
    tvListingsAllowed: Boolean,
    onAllowTvListings: () -> Unit,
    haStatus: String?,
    seerrStatus: String?,
    onSetUpSeerr: () -> Unit,
    onSignOutSeerr: () -> Unit,
    lightMode: Boolean,
    hiddenApps: List<Pair<String, String>>,
    onToggleLightMode: (Boolean) -> Unit,
    onUnhideApp: (String) -> Unit,
    rowOrder: List<String>,
    hiddenRows: Set<String>,
    onToggleRow: (String) -> Unit,
    onMoveRowUp: (String) -> Unit,
    doorbellSensors: List<HaEntity>,
    doorbellCameras: List<HaEntity>,
    doorbellSensor: String?,
    doorbellCamera: String?,
    onPickDoorbellSensor: (String?) -> Unit,
    onPickDoorbellCamera: (String?) -> Unit,
    onTestDoorbell: () -> Unit,
    doorbellPause: Boolean,
    onToggleDoorbellPause: (Boolean) -> Unit,
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
            onClick = onAllowTvListings,
            modifier = rowModifier,
            headlineContent = { Text("Google TV's continue watching") },
            supportingContent = {
                Text(
                    if (tvListingsAllowed) "On. Shows what apps share with Google TV's home screen."
                    else "Off. Press OK to allow \"TV listings\", so apps' continue watching shows here like on Google TV.",
                )
            },
            trailingContent = { Text(if (tvListingsAllowed) "On" else "Off", color = if (tvListingsAllowed) TvColors.Cyan else TvColors.Muted) },
        )

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

            // OK steps through the choices, so nothing needs typing.
            val sensorName = doorbellSensor?.let { id -> doorbellSensors.firstOrNull { it.id == id }?.name ?: id }
            ListItem(
                selected = false,
                onClick = {
                    val ids = listOf<String?>(null) + doorbellSensors.map { it.id }
                    onPickDoorbellSensor(ids[(ids.indexOf(doorbellSensor) + 1) % ids.size])
                },
                modifier = rowModifier,
                headlineContent = { Text("Doorbell pop-up") },
                supportingContent = {
                    Text(
                        when {
                            sensorName != null -> "On: when $sensorName goes off, the camera pops up over whatever's playing. OK for the next sensor."
                            doorbellSensors.isEmpty() -> "Off. No doorbell or person sensors found in Home Assistant."
                            else -> "Off. Press OK to pick the sensor that rings it (doorbell buttons first, then person detection)."
                        },
                    )
                },
                trailingContent = { Text(if (sensorName != null) "On" else "Off", color = if (sensorName != null) TvColors.Cyan else TvColors.Muted) },
            )
            if (doorbellSensor != null) {
                val auto = sensorName?.let { com.fortopes.tvlauncher.home.DoorbellPopup.matchCamera(it, doorbellCameras)?.name }
                ListItem(
                    selected = false,
                    onClick = {
                        val ids = listOf<String?>(null) + doorbellCameras.map { it.id }
                        onPickDoorbellCamera(ids[(ids.indexOf(doorbellCamera) + 1) % ids.size])
                    },
                    modifier = rowModifier,
                    headlineContent = { Text("Doorbell camera") },
                    supportingContent = {
                        val name = doorbellCamera?.let { id -> doorbellCameras.firstOrNull { it.id == id }?.name ?: id }
                        Text((name ?: "Automatic" + (auto?.let { ": $it" } ?: "")) + ". OK for the next camera.")
                    },
                )
                ListItem(
                    selected = false,
                    onClick = { onToggleDoorbellPause(!doorbellPause) },
                    modifier = rowModifier,
                    headlineContent = { Text("Pause what's playing") },
                    supportingContent = {
                        Text(
                            if (doorbellPause) "On: the doorbell pauses the show. Back on the pop-up carries on, OK leaves it paused."
                            else "Off: the show keeps playing under the pop-up.",
                        )
                    },
                    trailingContent = { Switch(checked = doorbellPause, onCheckedChange = null) },
                )
                ListItem(
                    selected = false,
                    onClick = onTestDoorbell,
                    modifier = rowModifier,
                    headlineContent = { Text("Try the doorbell pop-up") },
                    supportingContent = { Text("Shows it now for 30 seconds, pausing what is playing. Back closes it, OK opens the camera.") },
                )
            }
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

        Text("Rows", color = TvColors.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        Text(
            "OK shows or hides a row. Hold OK to move it up.",
            color = TvColors.Muted,
            fontSize = 13.sp,
            modifier = rowModifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        )
        rowOrder.forEachIndexed { i, key ->
            val shown = key !in hiddenRows
            val note: (@Composable () -> Unit)? =
                if (key == "favourites") ({ Text("Hold OK on an app and choose Add to favourites.") }) else null
            ListItem(
                selected = false,
                onClick = { onToggleRow(key) },
                onLongClick = { onMoveRowUp(key) },
                modifier = rowModifier,
                headlineContent = { Text("${i + 1}. ${HomeRows.title(key)}") },
                supportingContent = note,
                trailingContent = { Switch(checked = shown, onCheckedChange = null) },
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
