package com.zhexian.douyingallery.ui.work

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhexian.douyingallery.data.db.MediaEntity
import com.zhexian.douyingallery.data.db.WorkEntity
import com.zhexian.douyingallery.data.prefs.SettingsStore
import com.zhexian.douyingallery.data.repo.MediaRepository
import com.zhexian.douyingallery.domain.AppSettings
import com.zhexian.douyingallery.domain.FileSort
import com.zhexian.douyingallery.domain.MediaSorter
import com.zhexian.douyingallery.util.FileOps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkViewModel(
    private val repo: MediaRepository,
    private val settings: SettingsStore,
    val workPath: String
) : ViewModel() {

    private val _sort = MutableStateFlow(FileSort.SEQ)
    val sort: StateFlow<FileSort> = _sort.asStateFlow()

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    val selectionMode: StateFlow<Boolean> = _selected
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val work: StateFlow<WorkEntity?> = flow { emit(repo.work(workPath)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val rawFiles: Flow<List<MediaEntity>> = repo.filesOfWork(workPath)

    val files: StateFlow<List<MediaEntity>> = combine(rawFiles, _sort) { list, sort ->
        MediaSorter.sort(list, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settingsState: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setSort(sort: FileSort) {
        _sort.value = sort
    }

    fun toggleSelection(id: Long) {
        val current = _selected.value
        _selected.value = if (id in current) current - id else current + id
    }

    fun selectAll() {
        _selected.value = files.value.filter { it.type != -1 }.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun selectedItems(): List<MediaEntity> = files.value.filter { it.id in _selected.value }

    fun toggleFavorite(item: MediaEntity, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repo.setFavorite(listOf(item.id), !item.favorite)
            onDone(!item.favorite)
        }
    }

    fun favoriteSelected(favorite: Boolean, onDone: (Int) -> Unit = {}) {
        val ids = _selected.value.toList()
        viewModelScope.launch {
            repo.setFavorite(ids, favorite)
            _selected.value = emptySet()
            onDone(ids.size)
        }
    }

    fun deleteItems(items: List<MediaEntity>, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val root = settings.current().rootPath
            val result = repo.delete(items, root)
            _selected.value = emptySet()
            onDone(
                if (result.failed == 0) "已删除 ${result.deleted} 个文件"
                else "已删除 ${result.deleted} 个，失败 ${result.failed} 个"
            )
        }
    }

    fun exportItems(context: Context, items: List<MediaEntity>, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = repo.exportToGallery(context, items)
            _selected.value = emptySet()
            onDone(
                if (result.failed == 0) "已导出 ${result.success} 个到相册（Pictures/抖音解析导出）"
                else "导出成功 ${result.success} 个，失败 ${result.failed} 个"
            )
        }
    }

    fun shareIntent(context: Context, items: List<MediaEntity>): Intent? =
        FileOps.buildShareIntent(context, items)

    fun onThumbLoaded(item: MediaEntity, width: Int, height: Int) {
        if (item.width != null && item.height != null) return
        if (width <= 0 || height <= 0) return
        viewModelScope.launch { repo.saveDimensions(item.id, width, height) }
    }

    fun onOpenFailed(item: MediaEntity) {
        viewModelScope.launch { repo.removeMissingFile(item.path) }
    }

    fun rebuildIndex(onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val root = settings.current().rootPath
            val result = repo.refresh(root, forceFull = true)
            onDone(if (result.ok) "索引已重建：${result.files} 个文件" else result.error ?: "重建失败")
        }
    }
}
