package com.fortopes.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.fortopes.tvlauncher.data.SeerrClient
import com.fortopes.tvlauncher.data.SeerrStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connects to Seerr: its address and API key, typed or pasted with the Google Home app's keyboard. */
@Composable
fun SeerrSetupScreen(store: SeerrStore, client: SeerrClient, onDone: () -> Unit) {
    var url by remember { mutableStateOf(store.url?.removePrefix("http://") ?: "") }
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        if (checking) return
        checking = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    require(url.isNotBlank()) { "Type Seerr's address, like cunn-ha.local:5055." }
                    require(key.isNotBlank()) { "Paste the API key too." }
                    val clean = SeerrClient.normaliseUrl(url)
                    val name = try {
                        client.check(clean, key.trim())
                    } catch (e: Exception) {
                        throw IllegalStateException("Couldn't reach Seerr at $clean with that key (${e.message}).")
                    }
                    store.url = clean
                    store.apiKey = key.trim()
                    name
                }
            }
            checking = false
            result.onSuccess { done = it }.onFailure { error = it.message }
        }
    }

    Column(
        Modifier.fillMaxSize().background(TvColors.Background).padding(horizontal = 96.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Seerr", color = TvColors.Text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        val name = done
        if (name != null) {
            Text("Connected as $name. Recommended shows on the home screen.", color = TvColors.Text, fontSize = 18.sp)
            val focus = remember { FocusRequester() }
            ListItem(selected = false, onClick = onDone, modifier = Modifier.width(480.dp).focusRequester(focus), headlineContent = { Text("Done") })
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        } else {
            Text(
                "For the Recommended row: trending films and series, trailers, and requests. The Google Home app's TV remote keyboard can paste into these boxes.",
                color = TvColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.width(640.dp),
            )
            Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val first = remember { FocusRequester() }
                TvTextField(label = "Seerr address", value = url, onValueChange = { url = it }, imeAction = ImeAction.Next, modifier = Modifier.focusRequester(first))
                TvTextField(label = "API key (Seerr → Settings → General)", value = key, onValueChange = { key = it }, secret = true, onSubmit = ::connect)
                ListItem(selected = false, onClick = ::connect, headlineContent = { Text(if (checking) "Checking…" else "Connect") })
                error?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 13.sp) }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            }
            Text("Back to cancel.", color = TvColors.Muted, fontSize = 13.sp)
        }
    }
}
