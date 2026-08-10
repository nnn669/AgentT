package com.agentt.app.ui.browser

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(initialUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
        }
    }
    var input by rememberSaveable { mutableStateOf(initialUrl.ifBlank { "https://www.bing.com" }) }

    fun navigate(raw: String) {
        val u = raw.trim().let {
            when {
                it.startsWith("http://") || it.startsWith("https://") -> it
                it.contains(".") && !it.contains(" ") -> "https://$it"
                else -> "https://www.bing.com/search?q=${URLEncoder.encode(it, "UTF-8")}"
            }
        }
        input = u
        webView.loadUrl(u)
    }

    LaunchedEffect(Unit) {
        if (initialUrl.isNotBlank()) navigate(initialUrl)
    }

    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("内置浏览器", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { if (webView.canGoBack()) webView.goBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "后退", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { if (webView.canGoForward()) webView.goForward() }) {
                        Icon(Icons.Outlined.ArrowForward, contentDescription = "前进", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("输入网址或搜索内容") }
                    )
                    IconButton(onClick = { navigate(input) }) {
                        Icon(Icons.Outlined.Navigation, contentDescription = "前往", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { padding ->
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}