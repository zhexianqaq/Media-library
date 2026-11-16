package com.zhexian.douyingallery.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    // ---------- 扫描写入 ----------

    @Upsert
    suspend fun upsertMedia(items: List<MediaEntity>)

    @Query("SELECT id, path FROM media")
    suspend fun pathIds(): List<PathId>

    @Query("DELETE FROM media WHERE scanGen < :gen")
    suspend fun deleteStaleMedia(gen: Long)

    @Query("DELETE FROM media WHERE workPath = :workPath")
    suspend fun deleteMediaByWork(workPath: String)

    @Query("SELECT * FROM media")
    suspend fun allMedia(): List<MediaEntity>

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteMediaByIds(ids: List<Long>)

    @Query("DELETE FROM media WHERE path = :path")
    suspend fun deleteMediaByPath(path: String)

    @Query("UPDATE media SET favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    @Query("UPDATE media SET width = :width, height = :height WHERE id = :id")
    suspend fun setDimensions(id: Long, width: Int, height: Int)

    @Query("UPDATE media SET durationMs = :durationMs WHERE id = :id")
    suspend fun setDuration(id: Long, durationMs: Long)

    // ---------- 查询 ----------

    @Query("SELECT * FROM media WHERE workPath = :workPath ORDER BY mtime DESC")
    fun filesOfWorkFlow(workPath: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE workPath = :workPath ORDER BY mtime DESC")
    suspend fun filesOfWork(workPath: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE path = :path LIMIT 1")
    suspend fun mediaByPath(path: String): MediaEntity?

    @Query("SELECT * FROM media WHERE workPath = :workPath AND size = :size AND mtime = :mtime LIMIT 1")
    suspend fun findByPathSizeMtime(workPath: String, size: Long, mtime: Long): MediaEntity?

    /** 「全部」时间线：type = -1 表示不过滤；favOnly = 1 只看收藏 */
    @Query(
        "SELECT * FROM media WHERE (:type = -1 OR type = :type) AND (:favOnly = 0 OR favorite = 1) " +
            "ORDER BY mtime DESC"
    )
    fun timeline(type: Int, favOnly: Int): PagingSource<Int, MediaEntity>

    @Query(
        "SELECT * FROM media WHERE (:type = -1 OR type = :type) AND (:favOnly = 0 OR favorite = 1) " +
            "ORDER BY mtime DESC LIMIT :limit"
    )
    suspend fun timelineOnce(type: Int, favOnly: Int, limit: Int): List<MediaEntity>

    @Query("SELECT * FROM media WHERE favorite = 1 ORDER BY mtime DESC")
    fun favorites(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE creator = :creator AND (:type = -1 OR type = :type) ORDER BY mtime DESC LIMIT 500")
    fun filesOfCreator(creator: String, type: Int): Flow<List<MediaEntity>>

    @Query(
        "SELECT * FROM media WHERE role LIKE '%' || :kw || '%' OR path LIKE '%' || :kw || '%' " +
            "ORDER BY mtime DESC LIMIT 300"
    )
    suspend fun searchFiles(kw: String): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media")
    fun mediaCountFlow(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size), 0) FROM media")
    fun totalSizeFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun mediaCount(): Int

    // ---------- work 汇总 ----------

    @Upsert
    suspend fun upsertWorks(items: List<WorkEntity>)

    @Query("SELECT * FROM work")
    suspend fun allWorks(): List<WorkEntity>

    @Query("SELECT * FROM work WHERE workPath = :workPath LIMIT 1")
    suspend fun workByPath(workPath: String): WorkEntity?

    @Query("SELECT * FROM work WHERE creator = :creator ORDER BY latestMtime DESC")
    fun worksOfCreatorFlow(creator: String): Flow<List<WorkEntity>>

    @Query("SELECT * FROM work WHERE creator = :creator ORDER BY latestMtime DESC")
    suspend fun worksOfCreator(creator: String): List<WorkEntity>

    @Query(
        "SELECT workPath, creator, work, COUNT(*) AS count, " +
            "SUM(CASE WHEN type = 1 THEN 1 ELSE 0 END) AS videoCount, " +
            "SUM(CASE WHEN type IN (0, 2) THEN 1 ELSE 0 END) AS imageCount, " +
            "SUM(size) AS totalSize, MAX(mtime) AS latestMtime " +
            "FROM media GROUP BY workPath"
    )
    suspend fun aggregateWorks(): List<WorkAggRow>

    @Query(
        "SELECT workPath, creator, work, COUNT(*) AS count, " +
            "SUM(CASE WHEN type = 1 THEN 1 ELSE 0 END) AS videoCount, " +
            "SUM(CASE WHEN type IN (0, 2) THEN 1 ELSE 0 END) AS imageCount, " +
            "SUM(size) AS totalSize, MAX(mtime) AS latestMtime " +
            "FROM media WHERE workPath = :workPath GROUP BY workPath"
    )
    suspend fun aggregateWork(workPath: String): List<WorkAggRow>

    @Query("DELETE FROM work WHERE workPath = :workPath")
    suspend fun deleteWork(workPath: String)

    @Query("DELETE FROM work WHERE workPath NOT IN (SELECT DISTINCT workPath FROM media)")
    suspend fun deleteOrphanWorks()

    @Query(
        "SELECT creator, COUNT(*) AS workCount, SUM(w.count) AS fileCount, " +
            "MAX(latestMtime) AS latestMtime, SUM(totalSize) AS totalSize, " +
            "(SELECT w2.coverPath FROM work w2 WHERE w2.creator = w.creator AND w2.coverPath IS NOT NULL " +
            "ORDER BY w2.latestMtime DESC LIMIT 1) AS coverPath " +
            "FROM work w GROUP BY creator ORDER BY MAX(latestMtime) DESC"
    )
    fun creatorsFlow(): Flow<List<CreatorRow>>

    @Query(
        "SELECT creator, COUNT(*) AS workCount, SUM(w.count) AS fileCount, " +
            "MAX(latestMtime) AS latestMtime, SUM(totalSize) AS totalSize, " +
            "(SELECT w2.coverPath FROM work w2 WHERE w2.creator = w.creator AND w2.coverPath IS NOT NULL " +
            "ORDER BY w2.latestMtime DESC LIMIT 1) AS coverPath " +
            "FROM work w GROUP BY creator ORDER BY MAX(latestMtime) DESC"
    )
    suspend fun creators(): List<CreatorRow>

    @Query("SELECT coverPath FROM work WHERE creator = :creator AND coverPath IS NOT NULL ORDER BY latestMtime DESC LIMIT 1")
    fun latestCoverOfCreator(creator: String): Flow<String?>

    @Query("SELECT COUNT(*) FROM work")
    fun workCountFlow(): Flow<Int>

    // ---------- FTS ----------

    @Query("DELETE FROM work_fts")
    suspend fun clearFts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(items: List<WorkFts>)

    @Query("SELECT * FROM work WHERE workPath IN (SELECT workPath FROM work_fts WHERE work_fts MATCH :query) LIMIT 200")
    suspend fun searchWorksFts(query: String): List<WorkEntity>

    @Query(
        "SELECT * FROM work WHERE creator LIKE '%' || :kw || '%' OR work LIKE '%' || :kw || '%' " +
            "OR fullDesc LIKE '%' || :kw || '%' OR tags LIKE '%' || :kw || '%' " +
            "ORDER BY latestMtime DESC LIMIT 200"
    )
    suspend fun searchWorksLike(kw: String): List<WorkEntity>
}
