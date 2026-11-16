package com.zhexian.douyingallery.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhexian.douyingallery.Graph
import com.zhexian.douyingallery.data.prefs.SettingsStore
import com.zhexian.douyingallery.data.repo.MediaRepository
import com.zhexian.douyingallery.domain.AppSettings
import com.zhexian.douyingallery.util.FileOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    private val repo: MediaRepository,
    private val settings: SettingsStore
) : ViewModel() {

    data class Stats(
        val files: Int = 0,
        val works: Int = 0,
        val size: Long = 0L,
        val cacheBytes: Long = 0L,
        val byCreator: List<Pair<String, Long>> = emptyList()
    )

    val settingsState: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun loadStats() {
        viewModelScope.launch {
            val works = repo.allWorks()
            val cacheDir = File(Graph.context.cacheDir, "thumbs")
            val cacheBytes = withContext(Dispatchers.IO) { FileOps.dirSize(cacheDir) }
            _stats.value = Stats(
                files = works.sumOf { it.count },
                works = works.size,
                size = works.sumOf { it.totalSize },
                cacheBytes = cacheBytes,
                byCreator = works.groupBy { it.creator }
                    .map { (name, list) -> name to list.sumOf { it.totalSize } }
                    .sortedByDescending { it.second }
                    .take(10)
            )
        }
    }

    fun setRootPath(path: String) {
        viewModelScope.launch {
            settings.setRootPath(path.trim())
            _message.value = "根目录已修改，正在重建索引"
            _busy.value = true
            val result = repo.refresh(path.trim(), forceFull = true)
            _busy.value = false
            _message.value = if (result.ok) "索引完成：${result.files} 个文件" else result.error ?: "扫描失败"
            loadStats()
        }
    }

    fun setGridColumns(columns: Int) {
        viewModelScope.launch { settings.setGridColumns(columns) }
    }

    fun setLoopVideo(loop: Boolean) {
        viewModelScope.launch { settings.setLoopVideo(loop) }
    }

    fun setMuteVideo(mute: Boolean) {
        viewModelScope.launch { settings.setMuteVideo(mute) }
    }

    fun setLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLockEnabled(enabled) }
    }

    fun setLockTimeout(minutes: Int) {
        viewModelScope.launch { settings.setLockTimeout(minutes) }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            _busy.value = true
            val result = repo.refresh(settings.current().rootPath, forceFull = true)
            _busy.value = false
            _message.value = if (result.ok) {
                "索引完成：${result.files} 个文件 / ${result.works} 个作品（${result.elapsedMs} ms）"
            } else {
                result.error ?: "重建失败"
            }
            loadStats()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                FileOps.clearDir(File(Graph.context.cacheDir, "thumbs"))
            }
            _message.value = "缩略图缓存已清除"
            loadStats()
        }
    }
}
