package com.zhexian.douyingallery.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.zhexian.douyingallery.data.db.CreatorRow
import com.zhexian.douyingallery.data.db.MediaEntity
import com.zhexian.douyingallery.data.prefs.SettingsStore
import com.zhexian.douyingallery.data.repo.MediaRepository
import com.zhexian.douyingallery.domain.AppSettings
import com.zhexian.douyingallery.domain.CreatorSort
import com.zhexian.douyingallery.domain.MediaFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repo: MediaRepository,
    private val settings: SettingsStore
) : ViewModel() {

    val scanProgress = repo.scanProgress

    private val _filter = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()

    private val _creatorSort = MutableStateFlow(CreatorSort.LATEST)
    val creatorSort: StateFlow<CreatorSort> = _creatorSort.asStateFlow()

    val settingsState: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val creators: StateFlow<List<CreatorRow>> = combine(repo.creators(), _creatorSort) { list, sort ->
        when (sort) {
            CreatorSort.LATEST -> list
            CreatorSort.NAME -> list.sortedBy { it.creator.lowercase() }
            CreatorSort.COUNT -> list.sortedByDescending { it.fileCount }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 「全部」时间线：每个筛选条件一条分页流，由页面用 remember(filter) 持有 */
    fun timelineFor(filter: MediaFilter): Flow<PagingData<MediaEntity>> =
        repo.timeline(filter.typeArg, filter.favOnly).cachedIn(viewModelScope)

    fun setFilter(filter: MediaFilter) {
        _filter.value = filter
    }

    fun setCreatorSort(sort: CreatorSort) {
        _creatorSort.value = sort
    }

    fun refresh(forceFull: Boolean = false, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val root = settings.current().rootPath
            val result = repo.refresh(root, forceFull)
            onDone(
                when {
                    !result.ok -> result.error ?: "扫描失败"
                    else -> "已更新：${result.files} 个文件 / ${result.works} 个作品（${result.elapsedMs} ms）"
                }
            )
        }
    }

    fun onThumbLoaded(item: MediaEntity, width: Int, height: Int) {
        if (item.width != null && item.height != null) return
        if (width <= 0 || height <= 0) return
        viewModelScope.launch { repo.saveDimensions(item.id, width, height) }
    }
}
