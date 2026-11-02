package com.fortopes.tvlauncher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.fortopes.tvlauncher.data.JellyfinClient
import com.fortopes.tvlauncher.data.JellyfinServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SetupState {
    data object Searching : SetupState
    data class Pick(val servers: List<JellyfinServer>) : SetupState
    data class Code(val server: JellyfinServer, val code: String) : SetupState
    data object Done : SetupState
    data class Failed(val message: String) : SetupState
}

/**
 * Signs in to Jellyfin without typing: finds the server on the network, then shows a Quick Connect code
 * to enter in the Jellyfin app on your phone (Settings → Quick Connect).
 */
@Composable
fun JellyfinSetupScreen(client: JellyfinClient, onDone: () -> Unit) {
    var state by remember { mutableStateOf<SetupState>(SetupState.Searching) }
    var typed by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun connect(server: JellyfinServer) {
        scope.launch {
            try {
                val checked = withContext(Dispatchers.IO) { client.checkServer(server.url) }
                val qc = withContext(Dispatchers.IO) { client.startQuickConnect(checked.url) }
                state = SetupState.Code(checked, qc.code)
                while (true) {
                    delay(3000)
                    if (withContext(Dispatchers.IO) { client.isQuickConnectApproved(checked.url, qc.secret) }) break
                }
                withContext(Dispatchers.IO) { client.finishQuickConnect(checked, qc.secret) }
                state = SetupState.Done
            } catch (e: Exception) {
                state = SetupState.Failed(e.message ?: "Couldn't connect to ${server.url}")
            }
        }
    }

    LaunchedEffect(Unit) {
        val servers = withContext(Dispatchers.IO) { client.discover() }
        state = SetupState.Pick(servers)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = 96.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Jellyfin", color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        val rowModifier = Modifier.widthIn(max = 720.dp)

        when (val s = state) {
            SetupState.Searching -> Text("Looking for your Jellyfin server…", color = TvColors.Muted, fontSize = 16.sp)

            is SetupState.Pick -> {
                Text(
                    if (s.servers.isEmpty()) "No server answered on this network. Type its address below."
                    else "Pick your server.",
                    color = TvColors.Muted,
                    fontSize = 16.sp,
                )
                val first = remember { FocusRequester() }
                s.servers.forEachIndexed { i, server ->
                    ListItem(
                        selected = false,
                        onClick = { connect(server) },
                        modifier = if (i == 0) rowModifier.focusRequester(first) else rowModifier,
                        headlineContent = { Text(server.name) },
                        supportingContent = { Text(server.url) },
                    )
                }
                TvTextField(
                    label = "Or type the address, like 192.168.1.20 or jellyfin.local:8096",
                    value = typed,
                    onValueChange = { typed = it },
                    onSubmit = { if (typed.isNotBlank()) connect(JellyfinServer(typed, JellyfinClient.normaliseUrl(typed))) },
                    modifier = if (s.servers.isEmpty()) rowModifier.focusRequester(first) else rowModifier,
                )
                LaunchedEffect(s) { runCatching { first.requestFocus() } }
            }

            is SetupState.Code -> {
                Text("On your phone, open Jellyfin and go to Settings → Quick Connect. Enter this code:", color = TvColors.Text, fontSize = 18.sp)
                Text(s.code, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
                Text(
                    "Server: ${s.server.name}. This page finishes by itself once you've entered the code.",
                    color = TvColors.Muted,
                    fontSize = 14.sp,
                )
            }

            SetupState.Done -> {
                Text("Signed in. Your Continue watching row will show what you're part-way through in Jellyfin.", color = TvColors.Text, fontSize = 18.sp)
                val done = remember { FocusRequester() }
                ListItem(selected = false, onClick = onDone, modifier = rowModifier.focusRequester(done), headlineContent = { Text("Done") })
                LaunchedEffect(Unit) { runCatching { done.requestFocus() } }
            }

            is SetupState.Failed -> {
                Text(s.message, color = TvColors.Text, fontSize = 16.sp)
                if (s.message.contains("Quick Connect")) {
                    Text(
                        "Turn it on in Jellyfin: Dashboard → General → Enable Quick Connect.",
                        color = TvColors.Muted,
                        fontSize = 14.sp,
                    )
                }
                val retry = remember { FocusRequester() }
                ListItem(
                    selected = false,
                    onClick = {
                        state = SetupState.Searching
                        scope.launch { state = SetupState.Pick(withContext(Dispatchers.IO) { client.discover() }) }
                    },
                    modifier = rowModifier.focusRequester(retry),
                    headlineContent = { Text("Try again") },
                )
                LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
            }
        }
    }
}

