package com.fortopes.tvlauncher.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The dashboard's TV screen (`?tv`) inside TV Home, for everything the Home row doesn't have. Back goes to
 * the dashboard first (to leave a full-screen camera or a tab) and closes it when the dashboard is done.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DashboardScreen(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
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

    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
}
