package com.fortopes.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.fortopes.tvlauncher.data.HaClient
import com.fortopes.tvlauncher.data.HaStore
import com.fortopes.tvlauncher.home.PhoneSetupServer

/**
 * Connects to Home Assistant: type or paste the address and a long-lived token on the TV (the Google Home
 * app's remote keyboard can paste), or scan the QR code and fill them in on your phone.
 */
@Composable
fun HaSetupScreen(store: HaStore, client: HaClient, onDone: () -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf(store.url?.removePrefix("http://") ?: "") }
    var token by remember { mutableStateOf("") }
    var room by remember { mutableStateOf(store.room ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun connectFromTv() {
        if (checking) return
        checking = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { save(store, client, url, token, room) }
            checking = false
            result.onSuccess { done = it }.onFailure { error = it.message }
        }
    }

    DisposableEffect(Unit) {
        val server = PhoneSetupServer(
            title = "Connect TV Home to Home Assistant",
            fields = listOf(
                PhoneSetupServer.Field("url", "Home Assistant address", "http://homeassistant.local:8123"),
                PhoneSetupServer.Field(
                    "token",
                    "Long-lived access token (your profile → Security → Create token)",
                    "eyJ…",
                    secret = true,
                ),
                PhoneSetupServer.Field("room", "The room this TV is in (optional)", "Living Room"),
            ),
        ) { values ->
            save(store, client, values["url"].orEmpty(), values["token"].orEmpty(), values["room"].orEmpty())
                .onSuccess { done = it }
                .map { "TV Home is connected to $it." }
        }
        address = server.start(context)
        onDispose { server.stop() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = 96.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Home Assistant", color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        val name = done
        if (name != null) {
            Text("Connected to $name.", color = TvColors.Text, fontSize = 18.sp)
            val focus = remember { FocusRequester() }
            ListItem(selected = false, onClick = onDone, modifier = Modifier.widthIn(max = 720.dp).focusRequester(focus), headlineContent = { Text("Done") })
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        } else {
            Row {
                // Left: type or paste here. The Google Home app's remote keyboard can paste the token.
                Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val first = remember { FocusRequester() }
                    TvTextField(
                        label = "Home Assistant address",
                        value = url,
                        onValueChange = { url = it },
                        imeAction = ImeAction.Next,
                        modifier = Modifier.focusRequester(first),
                    )
                    TvTextField(
                        label = "Long-lived token (your profile → Security → Create token)",
                        value = token,
                        onValueChange = { token = it },
                        secret = true,
                        imeAction = ImeAction.Next,
                    )
                    TvTextField(label = "The room this TV is in (optional)", value = room, onValueChange = { room = it }, onSubmit = ::connectFromTv)
                    ListItem(
                        selected = false,
                        onClick = ::connectFromTv,
                        headlineContent = { Text(if (checking) "Checking…" else "Connect") },
                    )
                    error?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 13.sp) }
                    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                }
                // Right: or fill it in on your phone.
                val qr = address
                if (qr != null) {
                    Column(Modifier.padding(start = 48.dp).width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Or scan to fill it in on your phone", color = TvColors.Muted, fontSize = 14.sp)
                        QrCode(qr, Modifier.size(180.dp))
                        Text(qr, color = TvColors.Muted, fontSize = 11.sp)
                    }
                }
            }
            Text("Back to cancel.", color = TvColors.Muted, fontSize = 13.sp)
        }
    }
}

/** Checks the address and token, then saves them. Returns Home Assistant's name. */
private fun save(store: HaStore, client: HaClient, rawUrl: String, rawToken: String, rawRoom: String): Result<String> = runCatching {
    require(rawUrl.isNotBlank()) { "Type the Home Assistant address, like cunn-ha.local:8123." }
    val url = HaClient.normaliseUrl(rawUrl)
    val token = rawToken.trim()
    require(token.isNotEmpty()) { "Paste the token too." }
    val name = try {
        client.check(url, token)
    } catch (e: Exception) {
        throw IllegalStateException("Couldn't reach Home Assistant at $url with that token (${e.message}).")
    }
    store.url = url
    store.token = token
    store.room = rawRoom.trim().takeIf { it.isNotEmpty() }
    name
}
