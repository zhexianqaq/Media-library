package com.zhexian.douyingallery.ui.permission

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhexian.douyingallery.util.StoragePermission

/**
 * 权限引导页（技术方案 §4.2）。
 * 授权后 onResume 会自动重新检查，由 MainActivity 控制何时离开本页。
 */
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it } || StoragePermission.hasAccess(context)) onGranted()
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (StoragePermission.hasAccess(context)) onGranted()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(56.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "需要「所有文件访问」权限",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "「抖音解析」把文件保存在 ${com.zhexian.douyingallery.domain.AppSettings.DEFAULT_ROOT}，" +
                    "并且每一层都放了 .nomedia，系统相册不会收录这些目录。\n\n" +
                    "所以本图库不能通过系统媒体库读取，只能直接扫描文件系统，这需要「所有文件访问」权限。\n\n" +
                    "本 App 完全离线：不联网、不上传，不会删除 .nomedia，也不会改动原文件名。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    if (StoragePermission.needsRuntimeRequest()) {
                        runtimeLauncher.launch(StoragePermission.runtimePermissions())
                    } else if (activity != null) {
                        StoragePermission.openAllFilesAccessSettings(activity)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (StoragePermission.needsRuntimeRequest()) "授予读写权限" else "去开启「所有文件访问」")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    if (activity != null) StoragePermission.openAppDetails(activity)
                }
            ) {
                Text("找不到开关？打开应用详情页")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onGranted) {
                Text("已授权，重新检查")
            }
        }
    }
}
