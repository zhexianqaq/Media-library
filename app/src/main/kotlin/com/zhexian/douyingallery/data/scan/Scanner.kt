package com.zhexian.douyingallery.data.scan

import com.zhexian.douyingallery.data.db.MediaDao
import com.zhexian.douyingallery.data.db.MediaEntity
import com.zhexian.douyingallery.data.db.WorkEntity
import com.zhexian.douyingallery.data.db.WorkFts
import com.zhexian.douyingallery.data.storage.FileSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 目录扫描器：全量 / 增量 / 单文件。
 * 只遍历三层（UP主 → 作品 → 文件），不递归更深（见技术方案 §5.4）。
 */
class Scanner(private val dao: MediaDao) {

    enum class Phase { IDLE, SCANNING, AGGREGATING, DONE, ERROR }

    data class ScanProgress(
        val phase: Phase = Phase.IDLE,
        val dirs: Int = 0,
        val files: Int = 0,
        val message: String = ""
    ) {
        val isRunning: Boolean get() = phase == Phase.SCANNING || phase == Phase.AGGREGATING
    }

    data class Result(
        val ok: Boolean,
        val files: Int = 0,
        val works: Int = 0,
        val elapsedMs: Long = 0L,
        val error: String? = null
    )

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    private val mutex = Mutex()

    // ---------------------------------------------------------------- 全量

    suspend fun fullScan(root: String): Result = withContext(Dispatchers.IO) {
        mutex.withLock {
            val start = System.currentTimeMillis()
            val gen = start
            _progress.value = ScanProgress(Phase.SCANNING, 0, 0, "准备扫描")
            try {
                if (!FileSource.isRootAvailable(root)) {
                    _progress.value = ScanProgress(Phase.ERROR, 0, 0, "根目录不存在或无法访问")
                    return@withLock Result(false, error = "根目录不存在或无法访问：$root")
                }

                // 已入库记录：用于保留 id / 收藏 / 懒加载的宽高与时长
                val existing = dao.allMedia().associateBy { it.path }

                val batch = ArrayList<MediaEntity>(BATCH)
                val dirMtimes = HashMap<String, Long>()
                val metas = HashMap<String, MetaReader.MetaInfo>()
                var dirs = 0
                var files = 0

                for (creatorDir in FileSource.listDirs(root)) {
                    val creator = creatorDir.substringAfterLast('/')
                    for (workDir in FileSource.listDirs(creatorDir)) {
                        dirs++
                        dirMtimes[workDir] = FileSource.lastModified(workDir)
                        MetaReader.read(workDir)?.let { metas[workDir] = it }

                        for (entry in FileSource.listFiles(workDir)) {
                            val fresh = PathParser.buildMedia(
                                path = entry.path,
                                name = entry.name,
                                size = entry.size,
                                mtime = entry.mtime,
                                creator = creator,
                                workDirName = workDir.substringAfterLast('/'),
                                scanGen = gen
                            ) ?: continue
                            batch.add(fresh.mergeWith(existing[entry.path]))
                            files++
                            if (batch.size >= BATCH) {
                                dao.upsertMedia(batch)
                                batch.clear()
                                _progress.value = ScanProgress(Phase.SCANNING, dirs, files, "正在扫描")
                            }
                        }
                        _progress.value = ScanProgress(Phase.SCANNING, dirs, files, "正在扫描")
                    }
                }
                if (batch.isNotEmpty()) dao.upsertMedia(batch)

                // 清掉本轮没见到的文件（已被外部删除）
                dao.deleteStaleMedia(gen)

                _progress.value = ScanProgress(Phase.AGGREGATING, dirs, files, "正在汇总作品")
                val works = rebuildAllWorks(dirMtimes, metas)

                _progress.value = ScanProgress(Phase.DONE, dirs, files, "扫描完成")
                Result(true, files, works, System.currentTimeMillis() - start)
            } catch (t: Throwable) {
                _progress.value = ScanProgress(Phase.ERROR, 0, 0, t.message ?: "扫描失败")
                Result(false, error = t.message ?: t.javaClass.simpleName)
            }
        }
    }

    // ---------------------------------------------------------------- 增量

    /**
     * 每次回到前台 / 下拉刷新时调用。
     * 只需要 stat 每个作品目录，mtime 变了的才重新列文件。
     */
    suspend fun incrementalScan(root: String): Result = withContext(Dispatchers.IO) {
        mutex.withLock {
            val start = System.currentTimeMillis()
            val gen = start
            _progress.value = ScanProgress(Phase.SCANNING, 0, 0, "正在刷新")
            try {
                if (!FileSource.isRootAvailable(root)) {
                    _progress.value = ScanProgress(Phase.ERROR, 0, 0, "根目录不存在或无法访问")
                    return@withLock Result(false, error = "根目录不存在或无法访问：$root")
                }

                val worksByPath = dao.allWorks().associateBy { it.workPath }
                val existing = dao.allMedia().associateBy { it.path }
                val seen = HashSet<String>(worksByPath.size * 2)
                val affected = LinkedHashSet<String>()
                var dirs = 0
                var files = 0

                for (creatorDir in FileSource.listDirs(root)) {
                    val creator = creatorDir.substringAfterLast('/')
                    for (workDir in FileSource.listDirs(creatorDir)) {
                        seen.add(workDir)
                        val mtime = FileSource.lastModified(workDir)
                        val prev = worksByPath[workDir]
                        if (prev != null && prev.dirMtime == mtime) continue

                        dirs++
                        val items = ArrayList<MediaEntity>()
                        for (entry in FileSource.listFiles(workDir)) {
                            val fresh = PathParser.buildMedia(
                                path = entry.path,
                                name = entry.name,
                                size = entry.size,
                                mtime = entry.mtime,
                                creator = creator,
                                workDirName = workDir.substringAfterLast('/'),
                                scanGen = gen
                            ) ?: continue
                            items.add(fresh.mergeWith(existing[entry.path]))
                        }
                        dao.deleteMediaByWork(workDir)
                        if (items.isNotEmpty()) dao.upsertMedia(items)
                        affected.add(workDir)
                        files += items.size
                    }
                }

                // 目录被删掉的作品
                val removed = worksByPath.keys.filter { it !in seen }
                for (workPath in removed) {
                    dao.deleteMediaByWork(workPath)
                    dao.deleteWork(workPath)
                }

                // 重算受影响的 work
                for (workPath in affected) {
                    rebuildWorkRow(workPath)
                }
                dao.deleteOrphanWorks()
                if (affected.isNotEmpty() || removed.isNotEmpty()) rebuildFts(dao.allWorks())

                val workCount = dao.allWorks().size
                _progress.value = ScanProgress(Phase.DONE, dirs, files, "刷新完成")
                Result(true, files, workCount, System.currentTimeMillis() - start)
            } catch (t: Throwable) {
                _progress.value = ScanProgress(Phase.ERROR, 0, 0, t.message ?: "刷新失败")
                Result(false, error = t.message ?: t.javaClass.simpleName)
            }
        }
    }

    // ---------------------------------------------------------------- 单文件（下载完成广播 / 打开失败自愈）

    suspend fun indexFile(path: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val file = File(path)
                if (!file.isFile) return@runCatching false
                val workDir = file.parentFile ?: return@runCatching false
                val creatorDir = workDir.parentFile ?: return@runCatching false
                val item = PathParser.buildMedia(
                    path = file.absolutePath,
                    name = file.name,
                    size = file.length(),
                    mtime = file.lastModified(),
                    creator = creatorDir.name,
                    workDirName = workDir.name,
                    scanGen = System.currentTimeMillis()
                ) ?: return@runCatching false
                dao.upsertMedia(listOf(item.mergeWith(dao.mediaByPath(path))))
                rebuildWorkRow(workDir.absolutePath)
                rebuildFts(dao.allWorks())
                true
            }.getOrDefault(false)
        }
    }

    suspend fun removeMissing(path: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val existing = dao.mediaByPath(path) ?: return@runCatching false
                dao.deleteMediaByPath(path)
                rebuildWorkRow(existing.workPath)
                rebuildFts(dao.allWorks())
                true
            }.getOrDefault(false)
        }
    }

    // ---------------------------------------------------------------- 内部

    private fun MediaEntity.mergeWith(old: MediaEntity?): MediaEntity =
        if (old == null) this else copy(
            id = old.id,
            width = old.width,
            height = old.height,
            durationMs = old.durationMs,
            favorite = old.favorite
        )

    private suspend fun rebuildAllWorks(
        dirMtimes: Map<String, Long>,
        metas: Map<String, MetaReader.MetaInfo>
    ): Int {
        val aggregates = dao.aggregateWorks()
        val oldWorks = dao.allWorks().associateBy { it.workPath }
        val covers = pickCovers(dao.allMedia())

        val works = aggregates.map { a ->
            val prev = oldWorks[a.workPath]
            val meta = metas[a.workPath]
            WorkEntity(
                workPath = a.workPath,
                creator = a.creator,
                work = a.work,
                coverPath = covers[a.workPath] ?: prev?.coverPath,
                count = a.count,
                videoCount = a.videoCount,
                imageCount = a.imageCount,
                totalSize = a.totalSize,
                latestMtime = a.latestMtime,
                fullDesc = meta?.desc ?: prev?.fullDesc,
                tags = meta?.tags?.joinToString(" ") ?: prev?.tags,
                sourceUrl = meta?.url ?: prev?.sourceUrl,
                source = meta?.source ?: prev?.source,
                dirMtime = dirMtimes[a.workPath] ?: prev?.dirMtime ?: 0L
            )
        }
        dao.upsertWorks(works)
        dao.deleteOrphanWorks()
        rebuildFts(works)
        return works.size
    }

    private suspend fun rebuildWorkRow(workPath: String) {
        val agg = dao.aggregateWork(workPath).firstOrNull()
        if (agg == null) {
            dao.deleteWork(workPath)
            return
        }
        val prev = dao.workByPath(workPath)
        val meta = MetaReader.read(workPath)
        val cover = pickCovers(dao.filesOfWork(workPath))[workPath]
        dao.upsertWorks(
            listOf(
                WorkEntity(
                    workPath = agg.workPath,
                    creator = agg.creator,
                    work = agg.work,
                    coverPath = cover ?: prev?.coverPath,
                    count = agg.count,
                    videoCount = agg.videoCount,
                    imageCount = agg.imageCount,
                    totalSize = agg.totalSize,
                    latestMtime = agg.latestMtime,
                    fullDesc = meta?.desc ?: prev?.fullDesc,
                    tags = meta?.tags?.joinToString(" ") ?: prev?.tags,
                    sourceUrl = meta?.url ?: prev?.sourceUrl,
                    source = meta?.source ?: prev?.source,
                    dirMtime = FileSource.lastModified(workPath)
                )
            )
        )
    }

    private fun pickCovers(items: List<MediaEntity>): Map<String, String> {
        val best = HashMap<String, MediaEntity>()
        for (m in items) {
            if (m.isCaption) continue
            val cur = best[m.workPath]
            if (cur == null || coverOrder(m) < coverOrder(cur)) best[m.workPath] = m
        }
        return best.mapValues { it.value.path }
    }

    private fun coverOrder(m: MediaEntity): Long =
        ((m.seq ?: 1_000_000).toLong() shl 20) + (m.path.hashCode().toLong() and 0xFFFFF)

    private suspend fun rebuildFts(works: List<WorkEntity>) {
        dao.clearFts()
        val rows = works.map {
            WorkFts(
                workPath = it.workPath,
                creator = it.creator,
                work = it.work,
                fullDesc = it.fullDesc,
                tags = it.tags
            )
        }
        rows.chunked(BATCH).forEach { dao.insertFts(it) }
    }

    private companion object {
        const val BATCH = 500
    }
}
