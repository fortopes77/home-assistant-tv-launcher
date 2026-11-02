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
 * Signs in to Home Assistant from your phone: the TV shows a QR code for a small form, and you paste the
 * address and a long-lived token there instead of typing them with the remote.
 */
@Composable
fun HaSetupScreen(store: HaStore, client: HaClient, onDone: () -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

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
            runCatching {
                val url = HaClient.normaliseUrl(values["url"].orEmpty())
                val token = values["token"].orEmpty().trim()
                require(token.isNotEmpty()) { "Paste the token too." }
                val name = try {
                    client.check(url, token)
                } catch (e: Exception) {
                    throw IllegalStateException("The TV couldn't reach Home Assistant at $url with that token (${e.message}).")
                }
                store.url = url
                store.token = token
                store.room = values["room"]?.trim()?.takeIf { it.isNotEmpty() }
                done = name
                "TV Home is connected to $name."
            }
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
            val url = address
            if (url == null) {
                Text("The TV doesn't seem to be on a network.", color = TvColors.Text, fontSize = 18.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(url, Modifier.size(220.dp))
                    Column(Modifier.padding(start = 32.dp).width(460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Scan this with your phone (on the same Wi-Fi).", color = TvColors.Text, fontSize = 18.sp)
                        Text("Paste your Home Assistant address and a long-lived token, then press Send.", color = TvColors.Muted, fontSize = 15.sp)
                        Text("Or open $url", color = TvColors.Muted, fontSize = 13.sp)
                    }
                }
            }
            Text("Back to cancel.", color = TvColors.Muted, fontSize = 13.sp)
        }
    }
}
