package com.zhexian.douyingallery.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 权限判定与跳转（技术方案 §4.2）。
 *
 * Android 11+ 用「所有文件访问」；Android 10 及以下用运行时读写权限 + requestLegacyExternalStorage。
 */
object StoragePermission {

    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** API ≤ 29 才需要走运行时权限弹窗 */
    fun needsRuntimeRequest(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q

    fun runtimePermissions(): Array<String> = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * 打开「所有文件访问」设置页。
     * vivo 等机型可能不支持带包名的 Intent，逐级回退（技术方案 §10）。
     */
    fun openAllFilesAccessSettings(activity: Activity) {
        val attempts = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:${activity.packageName}")),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${activity.packageName}"))
        )
        for (intent in attempts) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { activity.startActivity(intent) }.isSuccess
            if (ok) return
        }
    }

    fun openAppDetails(activity: Activity) {
        runCatching {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${activity.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
