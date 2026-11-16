package com.zhexian.douyingallery.ui.viewer

import android.net.Uri
import com.zhexian.douyingallery.domain.FileSort
import com.zhexian.douyingallery.domain.ViewerScope

/**
 * 查看器导航参数的编解码。
 *
 * Navigation-Compose 的路径参数不能直接放绝对路径（含 `/`），所以编码成一段字符串：
 * ```
 * w|<workPath>|<sortOrdinal>|<index>|<focusId>
 * t|<type>|<favOnly>|<index>
 * ```
 */
object ViewerArgs {

    fun encodeOfWork(workPath: String, sort: FileSort, index: Int, focusId: Long? = null): String =
        "w|${Uri.encode(workPath)}|${sort.ordinal}|$index|${focusId ?: -1L}"

    fun encodeOfTimeline(type: Int, favOnly: Int, index: Int): String =
        "t|$type|$favOnly|$index"

    data class Decoded(val scope: ViewerScope, val index: Int)

    fun decode(raw: String): Decoded {
        val parts = raw.split("|")
        return when (parts.firstOrNull()) {
            "w" -> {
                val path = Uri.decode(parts.getOrElse(1) { "" })
                val sort = FileSort.entries.getOrElse(parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0) { FileSort.SEQ }
                val index = parts.getOrElse(3) { "0" }.toIntOrNull() ?: 0
                val focus = parts.getOrElse(4) { "-1" }.toLongOrNull()?.takeIf { it > 0 }
                Decoded(ViewerScope.OfWork(path, sort, focus), index)
            }

            "t" -> {
                val type = parts.getOrElse(1) { "-1" }.toIntOrNull() ?: -1
                val fav = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0
                val index = parts.getOrElse(3) { "0" }.toIntOrNull() ?: 0
                Decoded(ViewerScope.OfTimeline(type, fav), index)
            }

            else -> Decoded(ViewerScope.OfTimeline(-1, 0), 0)
        }
    }
}
