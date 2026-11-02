package com.fortopes.tvlauncher.ui

import androidx.compose.foundation.background
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
    version: String,
    onToggleOverride: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenStockHome: () -> Unit,
    onOpenTvSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onClearError: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = 96.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("TV Home settings", color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Back returns to the home screen.",
            color = TvColors.Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        val rowModifier = Modifier.widthIn(max = 720.dp)

        ListItem(
            selected = false,
            onClick = { onToggleOverride(!overrideEnabled) },
            modifier = rowModifier.focusRequester(first),
            headlineContent = { Text("Open TV Home with the Home button") },
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
                    else "Turned off. Open Accessibility and turn on \"TV Home: Home button\"." +
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
                        "Android blocks accessibility for apps that didn't come from the Play Store. Press OK here, " +
                            "scroll to the bottom and choose \"Allow restricted settings\", then turn the switch on again.",
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

        ListItem(
            selected = false,
            onClick = onOpenStockHome,
            modifier = rowModifier,
            headlineContent = { Text("Open the Google TV home") },
            supportingContent = { Text("Once, without turning the toggle off. The next time you leave an app you come back here.") },
        )

        ListItem(
            selected = false,
            onClick = onOpenTvSettings,
            modifier = rowModifier,
            headlineContent = { Text("TV settings") },
        )

        Text("Version $version", color = TvColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
    }
}
