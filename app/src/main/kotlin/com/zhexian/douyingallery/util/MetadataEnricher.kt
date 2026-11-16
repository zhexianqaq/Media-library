package com.zhexian.douyingallery.util

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.zhexian.douyingallery.data.db.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 懒补全元数据（技术方案 §5.4）：
 * 宽高 / 时长不在扫描时读，进详情或后台空闲时补，视频并发上限 2。
 */
object MetadataEnricher {

    private val videoSemaphore = Semaphore(2)

    /** 只读图片头，不 decode 像素，很快 */
    suspend fun imageSize(path: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        }.getOrNull()
    }

    suspend fun videoDuration(path: String): Long? = withContext(Dispatchers.IO) {
        videoSemaphore.withPermit {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } catch (t: Throwable) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    fun isValidMedia(path: String): Boolean {
        val f = File(path)
        return f.isFile && f.length() > 0
    }
}
