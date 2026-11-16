package com.zhexian.douyingallery.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条媒体文件记录。`id` 由 Room 自增，`path` 唯一。
 */
@Entity(
    tableName = "media",
    indices = [
        Index("creator"),
        Index("workPath"),
        Index("mtime"),
        Index(value = ["path"], unique = true)
    ]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val creator: String,
    val work: String,
    val workPath: String,
    val role: String?,
    val seq: Int?,
    val isCaption: Boolean,
    val type: Int,
    val ext: String,
    val size: Long,
    val mtime: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val favorite: Boolean = false,
    val scanGen: Long
) {
    val isVideo: Boolean get() = type == 1
    val displayName: String get() = path.substringAfterLast('/')
}

/**
 * 一个作品（= 数据源里的二级目录）。
 */
@Entity(tableName = "work")
data class WorkEntity(
    @PrimaryKey val workPath: String,
    val creator: String,
    val work: String,
    val coverPath: String?,
    val count: Int,
    val videoCount: Int,
    val imageCount: Int,
    val totalSize: Long,
    val latestMtime: Long,
    val fullDesc: String? = null,
    val tags: String? = null,
    val sourceUrl: String? = null,
    val source: String? = null,
    val dirMtime: Long = 0L
)

/**
 * 独立 FTS4 表（不使用 contentEntity，避免 work 表用字符串主键时 rowid 映射的限制）。
 * 每次重算 work 汇总后整体重建，行数在千级，代价可忽略。
 */
@Fts4
@Entity(tableName = "work_fts")
data class WorkFts(
    val workPath: String,
    val creator: String,
    val work: String,
    val fullDesc: String?,
    val tags: String?
)

/** 聚合查询的临时结果 */
data class WorkAggRow(
    val workPath: String,
    val creator: String,
    val work: String,
    val count: Int,
    val videoCount: Int,
    val imageCount: Int,
    val totalSize: Long,
    val latestMtime: Long
)

/** UP主 聚合 */
data class CreatorRow(
    val creator: String,
    val workCount: Int,
    val fileCount: Int,
    val latestMtime: Long,
    val totalSize: Long,
    val coverPath: String? = null
)

/** 路径与 id 的映射，用于扫描时保留收藏标记 */
data class PathId(val id: Long, val path: String)
