package com.vibevault.app.ui.screens.home

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SpotifyConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (spDc: String, spKey: String) -> Unit
) {
    var captured by remember { mutableStateOf(false) }
    val handler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
            try {
                webViewRef?.apply {
                    stopLoading()
                    clearCache(true)
                    clearFormData()
                    clearHistory()
                    destroy()
                }
            } catch (_: Exception) {}
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.80f)
                .clip(RoundedCornerShape(24.dp)),
            color = Color(0xFF1C1B20),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Spotify Login",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Log in below to connect your Spotify account:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Spacer(Modifier.height(12.dp))

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    factory = { context ->
                        WebView(context).apply {
                            webViewRef = this
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            val cookieManager = CookieManager.getInstance()
                            try {
                                android.webkit.WebStorage.getInstance().deleteAllData()
                            } catch (_: Exception) {}

                            clearCache(true)
                            clearFormData()
                            clearHistory()

                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                saveFormData = false
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
                            }

                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN,
                                    android.view.MotionEvent.ACTION_MOVE -> {
                                        v.parent.requestDisallowInterceptTouchEvent(true)
                                    }
                                    android.view.MotionEvent.ACTION_UP,
                                    android.view.MotionEvent.ACTION_CANCEL -> {
                                        v.parent.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                false
                            }

                            fun checkCookies(): Boolean {
                                if (captured) return true
                                cookieManager.flush()
                                val c1 = cookieManager.getCookie("https://open.spotify.com") ?: ""
                                val c2 = cookieManager.getCookie("https://accounts.spotify.com") ?: ""
                                val c3 = cookieManager.getCookie("https://spotify.com") ?: ""
                                val combined = "$c1;$c2;$c3"
                                val cookies = combined.split(";").associate {
                                    val parts = it.split("=")
                                    val key = parts.firstOrNull()?.trim().orEmpty()
                                    val valStr = parts.drop(1).joinToString("=").trim()
                                    key to valStr
                                }
                                val spDc = cookies["sp_dc"].orEmpty()
                                if (spDc.isNotBlank()) {
                                    captured = true
                                    handler.removeCallbacksAndMessages(null)
                                    onConnect(spDc, cookies["sp_key"].orEmpty())
                                    onDismiss()
                                    return true
                                }
                                return false
                            }

                            val checkRunnable = object : Runnable {
                                override fun run() {
                                    if (!captured && checkCookies()) {
                                        return
                                    }
                                    if (!captured) {
                                        handler.postDelayed(this, 1000)
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    return checkCookies()
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                    checkCookies()
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    checkCookies()
                                }

                                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                    checkCookies()
                                }
                            }
                            webChromeClient = android.webkit.WebChromeClient()
                            cookieManager.removeAllCookies {
                                cookieManager.removeSessionCookies {
                                    cookieManager.flush()
                                    if (!captured) {
                                        handler.post(checkRunnable)
                                        loadUrl(com.music.spotify.SpotifyAuth.LOGIN_URL)
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

