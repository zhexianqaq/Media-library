package com.zhexian.douyingallery.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.zhexian.douyingallery.data.db.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件级操作：分享 / 导出到相册 / 删除（含空目录清理）。
 *
 * 三条红线（见技术方案 §1、§5.8）：
 * 1. 永远不删 `.nomedia`，除非整个目录要删（删目录时连它一起删，这是唯一例外）；
 * 2. 根目录永远不删；
 * 3. 导出到相册是「复制」，原文件保持隐藏。
 */
object FileOps {

    const val EXPORT_FOLDER = "抖音解析导出"
    private const val NOMEDIA = ".nomedia"

    // ------------------------------------------------------------ 分享

    fun mimeOf(item: MediaEntity): String {
        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.ext)
        if (fromMap != null) return fromMap
        return when (item.type) {
            com.zhexian.douyingallery.data.scan.PathParser.TYPE_VIDEO -> "video/*"
            com.zhexian.douyingallery.data.scan.PathParser.TYPE_GIF -> "image/gif"
            else -> "image/*"
        }
    }

    fun uriFor(context: Context, path: String): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
    }.getOrNull()

    fun buildShareIntent(context: Context, items: List<MediaEntity>): Intent? {
        if (items.isEmpty()) return null
        val uris = items.mapNotNull { uriFor(context, it.path) }
        if (uris.isEmpty()) return null
        val mime = if (items.size == 1) mimeOf(items[0]) else "*/*"
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun openIntent(context: Context, item: MediaEntity): Intent? {
        val uri = uriFor(context, item.path) ?: return null
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(item))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    // ------------------------------------------------------------ 导出到相册

    data class ExportResult(val success: Int, val failed: Int)

    /**
     * 显式复制到系统相册目录（Pictures/抖音解析导出 或 Movies/抖音解析导出）。
     * 不是移动，原文件保持原样。
     */
    suspend fun exportToGallery(context: Context, items: List<MediaEntity>): ExportResult =
        withContext(Dispatchers.IO) {
            var success = 0
            var failed = 0
            for (item in items) {
                val ok = runCatching { if (Build.VERSION.SDK_INT >= 29) exportViaMediaStore(context, item) else exportLegacy(context, item) }
                    .getOrDefault(false)
                if (ok) success++ else failed++
            }
            ExportResult(success, failed)
        }

    private fun exportName(item: MediaEntity): String {
        val rawStem = item.path.substringAfterLast('/').substringBeforeLast('.')
        val rolePart = item.role ?: rawStem
        val sb = StringBuilder()
            .append(item.creator)
            .append('_')
            .append(rolePart)
        if (item.seq != null) sb.append('-').append(item.seq.toString().padStart(3, '0'))
        if (item.isCaption) sb.append("-正文")
        sb.append('.').append(item.ext)
        return sanitizeFileName(sb.toString())
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_").trim().ifEmpty { "media_${System.currentTimeMillis()}" }

    private fun isVideo(item: MediaEntity) = item.type == com.zhexian.douyingallery.data.scan.PathParser.TYPE_VIDEO

    private fun exportViaMediaStore(context: Context, item: MediaEntity): Boolean {
        val collection = if (isVideo(item)) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativeDir = if (isVideo(item)) {
            "${Environment.DIRECTORY_MOVIES}/$EXPORT_FOLDER"
        } else {
            "${Environment.DIRECTORY_PICTURES}/$EXPORT_FOLDER"
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, exportName(item))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(item))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        var ok = false
        try {
            File(item.path).inputStream().use { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output, DEFAULT_BUFFER) }
                    ?: return false
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            ok = true
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
        }
        return ok
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(context: Context, item: MediaEntity): Boolean {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                if (isVideo(item)) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            ),
            EXPORT_FOLDER
        )
        if (!dir.exists() && !dir.mkdirs()) return false
        val target = File(dir, exportName(item))
        File(item.path).inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER) }
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return target.length() > 0
    }

    // ------------------------------------------------------------ 删除

    data class DeleteResult(val deleted: Int, val failed: Int)

    /**
     * 删除文件，并在作品目录 / UP主 目录只剩 `.nomedia` 时把它们一起删掉。
     * 根目录永远不删。
     */
    fun deleteFiles(items: List<MediaEntity>, root: String): DeleteResult {
        var deleted = 0
        var failed = 0
        val touchedWorks = LinkedHashSet<String>()
        for (item in items) {
            val f = File(item.path)
            val ok = runCatching { !f.exists() || f.delete() }.getOrDefault(false)
            if (ok) {
                deleted++
                touchedWorks.add(item.workPath)
            } else {
                failed++
            }
        }
        val normalizedRoot = File(root).absolutePath
        for (workPath in touchedWorks) cleanupWorkDir(workPath, normalizedRoot)
        return DeleteResult(deleted, failed)
    }

    private fun cleanupWorkDir(workPath: String, root: String) {
        val dir = File(workPath)
        if (!dir.isDirectory) return
        if (dir.absolutePath == root) return
        val children = dir.listFiles() ?: return
        if (children.all { it.name == NOMEDIA }) {
            children.forEach { runCatching { it.delete() } }
            if (dir.delete()) {
                dir.parentFile?.let { cleanupCreatorDir(it, root) }
            }
        }
    }

    private fun cleanupCreatorDir(dir: File, root: String) {
        if (!dir.isDirectory) return
        if (dir.absolutePath == root) return
        val children = dir.listFiles() ?: return
        if (children.all { it.name == NOMEDIA }) {
            children.forEach { runCatching { it.delete() } }
            dir.delete()
        }
    }

    // ------------------------------------------------------------ 缓存

    fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    fun clearDir(dir: File): Boolean {
        if (!dir.exists()) return true
        dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        return true
    }

    private const val DEFAULT_BUFFER = 64 * 1024
}
