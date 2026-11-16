package com.zhexian.douyingallery.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * 全局唯一的 ExoPlayer 实例（技术方案 §5.7）：
 * Pager 翻页时只 setMediaItem 复用，不为每页新建播放器。
 */
@OptIn(UnstableApi::class)
class PlayerHolder(private val context: Context) {

    private var instance: ExoPlayer? = null

    fun get(): ExoPlayer {
        instance?.let { return it }
        val player = ExoPlayer.Builder(context).build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        instance = player
        return player
    }

    fun pause() {
        instance?.pause()
    }

    fun release() {
        instance?.release()
        instance = null
    }
}
