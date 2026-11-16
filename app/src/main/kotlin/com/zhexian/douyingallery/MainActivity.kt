package com.zhexian.douyingallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.zhexian.douyingallery.ui.GalleryNavHost
import com.zhexian.douyingallery.ui.theme.GalleryTheme
import com.zhexian.douyingallery.util.BiometricLock
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var lockEnabled by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var timeoutMinutes by mutableStateOf(3)
    private var lastStoppedAt = 0L

    private var deepLinkWorkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkWorkPath = parseDeepLink(intent)

        lifecycleScope.launch {
            val current = Graph.settings.current()
            lockEnabled = current.lockEnabled
            timeoutMinutes = current.lockTimeoutMinutes
            locked = current.lockEnabled
            applySecureFlag(current.lockEnabled)
            ready = true

            Graph.settings.settings.collect { settings ->
                val wasEnabled = lockEnabled
                lockEnabled = settings.lockEnabled
                timeoutMinutes = settings.lockTimeoutMinutes
                if (settings.lockEnabled && !wasEnabled) locked = true
                if (!settings.lockEnabled) locked = false
                applySecureFlag(settings.lockEnabled)
            }
        }

        setContent {
            GalleryTheme {
                when {
                    !ready -> Box(modifier = Modifier.fillMaxSize())
                    locked -> LockScreen(onUnlocked = { locked = false })
                    else -> GalleryNavHost(deepLinkWorkPath = deepLinkWorkPath)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseDeepLink(intent)?.let { path ->
            deepLinkWorkPath = path
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        if (lockEnabled && lastStoppedAt > 0L &&
            System.currentTimeMillis() - lastStoppedAt > timeoutMinutes * 60_000L
        ) {
            locked = true
        }
    }

    override fun onStop() {
        super.onStop()
        lastStoppedAt = System.currentTimeMillis()
        Graph.player.pause()
    }

    private fun applySecureFlag(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** douyingallery://work?path=/storage/emulated/0/抖音解析/UP/作品 */
    private fun parseDeepLink(intent: Intent?): String? {
        val data: Uri = intent?.data ?: return null
        if (data.scheme != "douyingallery") return null
        return data.getQueryParameter("path")
    }
}

@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("图库已锁定", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "开启应用锁后，最近任务列表里显示为空白，也无法截屏。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (activity == null) {
                        onUnlocked()
                        return@Button
                    }
                    if (!BiometricLock.isAvailable(activity)) {
                        scope.launch { snackbar.showSnackbar("此设备未设置指纹或锁屏密码，请先在系统设置里添加") }
                        return@Button
                    }
                    BiometricLock.authenticate(
                        activity,
                        onSuccess = { onUnlocked() },
                        onFailure = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                    )
                }
            ) { Text("解锁") }
        }
    }
}
