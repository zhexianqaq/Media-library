package com.zhexian.douyingallery

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.zhexian.douyingallery.data.db.AppDatabase
import com.zhexian.douyingallery.data.db.MediaDao
import com.zhexian.douyingallery.data.prefs.SettingsStore
import com.zhexian.douyingallery.data.repo.MediaRepository
import com.zhexian.douyingallery.data.scan.Scanner
import com.zhexian.douyingallery.util.PlayerHolder
import okio.Path.Companion.toOkioPath
import java.io.File

class App : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Graph.attach(this)
    }

    /**
     * 全局 ImageLoader：
     * - 内存缓存 = 可用内存的 20%
     * - 磁盘缓存 <cacheDir>/thumbs，上限 512 MB（App 私有目录，系统相册看不到）
     * - 注册视频抽帧 / 动图解码器
     */
    override fun newImageLoader(context: Context): ImageLoader {
        val thumbDir = File(cacheDir, "thumbs").apply { mkdirs() }
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.2)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(thumbDir.toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .components {
                // 视频缩略图：coil3 3.0.x 只能指定帧序号，这里用关键帧（默认第 0 帧）
                add(VideoFrameDecoder.Factory())
                // 动图：API 28+ 用 AnimatedImageDecoder，低版本用 GifDecoder
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(120)
            .build()
    }
}

/**
 * 极简手写 DI（技术方案 §5.1 允许「也可以手写 DI」，避免引入 Hilt 的编译开销）。
 */
object Graph {

    private lateinit var appContext: Context

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context get() = appContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val dao: MediaDao by lazy { database.mediaDao() }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val scanner: Scanner by lazy { Scanner(dao) }

    val repository: MediaRepository by lazy { MediaRepository(dao, scanner, settings) }

    val player: PlayerHolder by lazy { PlayerHolder(appContext) }
}
