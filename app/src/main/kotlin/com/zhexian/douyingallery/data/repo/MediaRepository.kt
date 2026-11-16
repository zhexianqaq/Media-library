package com.zhexian.douyingallery.data.repo

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.zhexian.douyingallery.data.db.CreatorRow
import com.zhexian.douyingallery.data.db.MediaDao
import com.zhexian.douyingallery.data.db.MediaEntity
import com.zhexian.douyingallery.data.db.WorkEntity
import com.zhexian.douyingallery.data.prefs.SettingsStore
import com.zhexian.douyingallery.data.scan.Scanner
import com.zhexian.douyingallery.util.FileOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 唯一的数据入口。UI 只依赖它，不直接碰 DAO / File。
 */
class MediaRepository(
    private val dao: MediaDao,
    private val scanner: Scanner,
    private val settings: SettingsStore
) {

    val scanProgress = scanner.progress

    // ------------------------------------------------------------ 读

    fun creators(): Flow<List<CreatorRow>> = dao.creatorsFlow().distinctUntilChanged()

    fun worksOfCreator(creator: String): Flow<List<WorkEntity>> = dao.worksOfCreatorFlow(creator)

    fun filesOfWork(workPath: String): Flow<List<MediaEntity>> = dao.filesOfWorkFlow(workPath)

    fun creatorCover(creator: String): Flow<String?> = dao.latestCoverOfCreator(creator)

    fun favorites(): Flow<List<MediaEntity>> = dao.favorites()

    fun mediaCount(): Flow<Int> = dao.mediaCountFlow()

    fun workCount(): Flow<Int> = dao.workCountFlow()

    fun totalSize(): Flow<Long> = dao.totalSizeFlow()

    suspend fun work(workPath: String): WorkEntity? = dao.workByPath(workPath)

    suspend fun filesOfWorkOnce(workPath: String): List<MediaEntity> = dao.filesOfWork(workPath)

    suspend fun allWorks(): List<WorkEntity> = dao.allWorks()

    suspend fun allMedia(): List<MediaEntity> = dao.allMedia()

    /** 「全部」时间线的分页源 */
    fun timeline(type: Int, favOnly: Int): Flow<PagingData<MediaEntity>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = PAGE_SIZE / 2,
            enablePlaceholders = false,
            initialLoadSize = PAGE_SIZE * 2
        ),
        pagingSourceFactory = { dao.timeline(type, favOnly) }
    ).flow

    /** 查看器需要一个完整的列表来做 HorizontalPager */
    suspend fun timelineList(type: Int, favOnly: Int, limit: Int = 3000): List<MediaEntity> =
        dao.timelineOnce(type, favOnly, limit)

    // ------------------------------------------------------------ 搜索

    data class SearchResult(
        val creators: List<CreatorRow> = emptyList(),
        val works: List<WorkEntity> = emptyList(),
        val files: List<MediaEntity> = emptyList()
    ) {
        val isEmpty: Boolean get() = creators.isEmpty() && works.isEmpty() && files.isEmpty()
    }

    suspend fun search(keyword: String): SearchResult {
        val kw = keyword.trim()
        if (kw.isEmpty()) return SearchResult()
        val works = ftsSearch(kw) ?: dao.searchWorksLike(kw)
        val files = dao.searchFiles(kw)
        val creators = works.groupBy { it.creator }.map { (name, ws) ->
            CreatorRow(
                creator = name,
                workCount = ws.size,
                fileCount = ws.sumOf { it.count },
                latestMtime = ws.maxOf { it.latestMtime },
                totalSize = ws.sumOf { it.totalSize }
            )
        }.sortedByDescending { it.latestMtime }
        return SearchResult(creators, works, files)
    }

    private suspend fun ftsSearch(keyword: String): List<WorkEntity>? {
        // 中文分词不理想，FTS 只做前缀尝试；结果为空时上层会退到 LIKE
        val query = "\"" + keyword.replace("\"", "\"\"") + "\"*"
        val result = runCatching { dao.searchWorksFts(query) }.getOrNull() ?: return null
        return result.ifEmpty { null }
    }

    // ------------------------------------------------------------ 扫描

    suspend fun refresh(root: String, forceFull: Boolean = false): Scanner.Result {
        val isFirstRun = dao.allWorks().isEmpty()
        return if (forceFull || isFirstRun) scanner.fullScan(root) else scanner.incrementalScan(root)
    }

    suspend fun indexFile(path: String): Boolean = scanner.indexFile(path)

    suspend fun removeMissingFile(path: String): Boolean = scanner.removeMissing(path)

    suspend fun scanRunning(): Boolean = scanProgress.value.isRunning

    // ------------------------------------------------------------ 写

    suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = dao.setFavorite(ids, favorite)

    suspend fun delete(items: List<MediaEntity>, root: String): FileOps.DeleteResult {
        val result = FileOps.deleteFiles(items, root)
        // 数据库同步：逐条删（数量级是几十条以内）
        items.forEach { dao.deleteMediaByPath(it.path) }
        dao.deleteOrphanWorks()
        val affected = items.map { it.workPath }.distinct()
        if (affected.isNotEmpty()) {
            // 用一次增量扫描把 work 汇总和 FTS 刷新到正确状态
            val current = settings.current()
            scanner.incrementalScan(current.rootPath)
        }
        return result
    }

    suspend fun exportToGallery(context: Context, items: List<MediaEntity>): FileOps.ExportResult =
        FileOps.exportToGallery(context, items)

    suspend fun saveDimensions(id: Long, width: Int, height: Int) = dao.setDimensions(id, width, height)

    suspend fun saveDuration(id: Long, durationMs: Long) = dao.setDuration(id, durationMs)

    companion object {
        const val PAGE_SIZE = 120
    }
}
