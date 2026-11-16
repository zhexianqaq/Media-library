package com.zhexian.douyingallery.domain

import com.zhexian.douyingallery.data.db.MediaEntity

/** 文件排序：作品页默认序号升序，「全部」页时间倒序 */
object MediaSorter {

    fun sort(items: List<MediaEntity>, sort: FileSort): List<MediaEntity> = when (sort) {
        FileSort.SEQ -> items.sortedWith(
            compareBy<MediaEntity> { it.seq ?: Int.MAX_VALUE }
                .thenBy { it.isCaption }
                .thenBy { it.path }
        )

        FileSort.TIME_DESC -> items.sortedByDescending { it.mtime }
        FileSort.SIZE -> items.sortedByDescending { it.size }
    }
}
