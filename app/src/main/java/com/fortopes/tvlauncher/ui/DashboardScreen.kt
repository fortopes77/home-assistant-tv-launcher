package com.fortopes.tvlauncher.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text

/**
 * The dashboard's TV screen (`?tv`) inside TV Home, for everything the Home row doesn't have. Back goes to
 * the dashboard first (to leave a full-screen camera or a tab) and closes it when the dashboard is done.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DashboardScreen(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    // What went wrong, shown over the page so it's not just a blank screen.
    var problem by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val path = remember(url) { android.net.Uri.parse(url).path ?: "" }
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                // The dashboard opens apps with intent: links; a WebView can't, so hand them to Android.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val link = request.url.toString()
                    if (!link.startsWith("intent:")) return false
                    runCatching {
                        context.startActivity(Intent.parseUri(link, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading = false
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) problem = "Couldn't open the dashboard: ${error.description}"
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (!request.isForMainFrame) return
                    problem = if (response.statusCode == 404) {
                        "Home Assistant has no dashboard at $path. Put the dashboard's index.html in config/www/dashboard/ " +
                            "(its README has the steps), or tell Claude where yours is."
                    } else {
                        "Home Assistant answered ${response.statusCode} for the dashboard."
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                // A script error that stops the dashboard drawing; the first one is the useful one.
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR && problem == null &&
                        message.message().startsWith("Uncaught")
                    ) {
                        problem = "The dashboard hit an error: ${message.message()} (line ${message.lineNumber()})"
                    }
                    return false
                }
            }
            loadUrl(url) // the caller already swapped a .local host for its IP
            requestFocus()
        }
    }

    BackHandler {
        // Offer Back to the dashboard as the key it listens for; if it doesn't use it, close.
        val js = "(function(){var e=new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true});" +
            "(document.activeElement||document.body).dispatchEvent(e);return e.defaultPrevented;})()"
        webView.evaluateJavascript(js) { used -> if (used != "true") onClose() }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF14161A))) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
        if (loading && problem == null) {
            Text("Loading the dashboard…", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, modifier = Modifier.align(Alignment.Center))
        }
        problem?.let {
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE6301018)).padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Text(it, color = Color.White, fontSize = 15.sp)
                Text("Press Back to go home.", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}
