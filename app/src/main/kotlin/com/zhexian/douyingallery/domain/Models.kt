package com.zhexian.douyingallery.domain

/** 应用设置（DataStore 持久化） */
data class AppSettings(
    val rootPath: String = DEFAULT_ROOT,
    val gridColumns: Int = 3,
    val loopVideo: Boolean = true,
    val muteVideo: Boolean = false,
    val lockEnabled: Boolean = false,
    val lockTimeoutMinutes: Int = 3
) {
    companion object {
        const val DEFAULT_ROOT = "/storage/emulated/0/抖音解析"
    }
}

/** 顶栏筛选：全部 / 图片 / 视频 / 收藏 */
enum class MediaFilter(val label: String) {
    ALL("全部"),
    IMAGE("图片"),
    VIDEO("视频"),
    FAVORITE("收藏");

    /** -1 表示不按类型过滤 */
    val typeArg: Int
        get() = when (this) {
            ALL, FAVORITE -> -1
            IMAGE -> 0
            VIDEO -> 1
        }

    val favOnly: Int
        get() = if (this == FAVORITE) 1 else 0
}

enum class WorkSort(val label: String) {
    LATEST("最近下载"),
    NAME("名称")
}

enum class FileSort(val label: String) {
    SEQ("序号"),
    TIME_DESC("时间倒序"),
    SIZE("大小")
}

enum class CreatorSort(val label: String) {
    LATEST("最近更新"),
    NAME("名称"),
    COUNT("文件数")
}

/** 查看器的数据范围：某个作品，或「全部」时间线 */
sealed interface ViewerScope {
    /** 查看某个作品里的文件；sort 与作品页保持一致，保证索引对得上 */
    data class OfWork(
        val workPath: String,
        val sort: FileSort = FileSort.SEQ,
        val focusId: Long? = null
    ) : ViewerScope

    /** 查看「全部」时间线（或其中的图片/视频/收藏） */
    data class OfTimeline(val type: Int, val favOnly: Int = 0) : ViewerScope
}
