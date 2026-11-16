package com.zhexian.douyingallery.data.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhexian.douyingallery.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接收「抖音解析」下载器发来的单文件通知：
 * ```
 * Intent("com.zhexian.douyingallery.ACTION_MEDIA_ADDED").putExtra("path", file.absolutePath)
 * ```
 * 只做一次单文件 upsert，不触发全量扫描。
 */
class MediaAddedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MEDIA_ADDED) return
        val path = intent.getStringExtra(EXTRA_PATH) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Graph.repository.indexFile(path)
            } catch (_: Throwable) {
                // 广播是尽力而为的通知，失败不影响下载器
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MEDIA_ADDED = "com.zhexian.douyingallery.ACTION_MEDIA_ADDED"
        const val EXTRA_PATH = "path"
    }
}
