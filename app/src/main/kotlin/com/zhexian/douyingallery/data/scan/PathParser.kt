package com.zhexian.douyingallery.data.scan

import com.zhexian.douyingallery.data.db.MediaEntity

/**
 * 文件名 / 目录名解析。
 *
 * 纯 Kotlin（只依赖 java.lang.String 与基本类型），可以直接跑 JVM 单元测试。
 * 规则见技术方案 §3.4：
 * ```
 * ^(?<role>.+?)-(?<seq>\d{3,})(?:\.(?<ext>[A-Za-z0-9]{1,8}))$        // 编号文件
 * ^(?<role>.+?)-(?<suffix>正文)(?:-(?<dup>\d+))?\.(?<ext>[A-Za-z0-9]{1,8})$  // 简介长图
 * ```
 */
object PathParser {

    const val TYPE_IMAGE = 0
    const val TYPE_VIDEO = 1
    const val TYPE_GIF = 2

    const val META_FILE = "meta.json"
    const val LEGACY_WORK_NAME = "旧版下载"
    const val UNKNOWN_CREATOR = "未知UP主"
    const val NO_DESC_WORK = "无简介"

    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif")
    val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "webm", "mkv", "3gp")

    /** 下载器写盘过程中的临时文件，扫描时忽略 */
    private val IGNORED_EXTENSIONS = setOf("nomedia", "tmp", "part", "download")

    /** 旧版目录：<作者>/视频/、<作者>/图片/ */
    private val LEGACY_DIRS = setOf("视频", "图片")

    private val NUMBERED = Regex("""^(.+)-(\d{3,})\.([A-Za-z0-9]{1,8})$""")
    private val CAPTION = Regex("""^(.+)-正文(?:-(\d+))?\.([A-Za-z0-9]{1,8})$""")

    /** 小写扩展名（不含点）；无扩展名返回空串 */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase()
    }

    /** 是否应该被扫描器跳过 */
    fun isIgnored(name: String, size: Long): Boolean {
        if (name.startsWith(".")) return true          // .nomedia / .trash / 隐藏文件
        if (name == META_FILE) return true             // 元数据，不是媒体
        if (size <= 0L) return true                    // 0 字节（下载中的占位文件）
        val ext = extensionOf(name)
        if (ext.isEmpty()) return true
        if (ext in IGNORED_EXTENSIONS) return true
        return typeOf(ext) == null
    }

    /** 扩展名 → 媒体类型；不支持的格式返回 null */
    fun typeOf(ext: String): Int? = when {
        ext == "gif" -> TYPE_GIF
        ext in IMAGE_EXTENSIONS -> TYPE_IMAGE
        ext in VIDEO_EXTENSIONS -> TYPE_VIDEO
        else -> null
    }

    fun isMediaName(name: String): Boolean = typeOf(extensionOf(name)) != null

    /** 二级目录名 → 作品名（旧版 视频/图片 目录统一记为「旧版下载」） */
    fun workNameOf(dirName: String): String =
        if (dirName in LEGACY_DIRS) LEGACY_WORK_NAME else dirName

    data class NameParts(
        val role: String?,
        val seq: Int?,
        val isCaption: Boolean,
        val ext: String
    )

    /**
     * 解析文件名。不匹配任何规则时 role / seq 为 null，仍然返回（这类文件也要显示）。
     */
    fun parseName(fileName: String): NameParts {
        val ext = extensionOf(fileName)

        CAPTION.matchEntire(fileName)?.let { m ->
            val role = m.groupValues[1].trim().ifEmpty { null }
            return NameParts(role, null, true, ext)
        }
        NUMBERED.matchEntire(fileName)?.let { m ->
            val role = m.groupValues[1].trim().ifEmpty { null }
            val seq = m.groupValues[2].toIntOrNull()
            return NameParts(role, seq, false, ext)
        }
        return NameParts(null, null, false, ext)
    }

    /**
     * 把一个文件描述成数据库实体；不是媒体文件（或应忽略）时返回 null。
     * 参数全部是基本类型，方便单元测试。
     */
    fun buildMedia(
        path: String,
        name: String,
        size: Long,
        mtime: Long,
        creator: String,
        workDirName: String,
        scanGen: Long
    ): MediaEntity? {
        if (isIgnored(name, size)) return null
        val ext = extensionOf(name)
        val type = typeOf(ext) ?: return null
        val parts = parseName(name)
        return MediaEntity(
            path = path,
            creator = creator,
            work = workNameOf(workDirName),
            workPath = path.substringBeforeLast('/', missingDelimiterValue = ""),
            role = parts.role,
            seq = parts.seq,
            isCaption = parts.isCaption,
            type = type,
            ext = ext,
            size = size,
            mtime = mtime,
            scanGen = scanGen
        )
    }
}
