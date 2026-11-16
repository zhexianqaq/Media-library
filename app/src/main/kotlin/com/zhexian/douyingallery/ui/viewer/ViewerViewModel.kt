package com.zhexian.douyingallery.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhexian.douyingallery.data.db.MediaEntity
import com.zhexian.douyingallery.data.prefs.SettingsStore
import com.zhexian.douyingallery.data.repo.MediaRepository
import com.zhexian.douyingallery.data.scan.PathParser
import com.zhexian.douyingallery.domain.AppSettings
import com.zhexian.douyingallery.domain.MediaSorter
import com.zhexian.douyingallery.domain.ViewerScope
import com.zhexian.douyingallery.util.MetadataEnricher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewerViewModel(
    private val repo: MediaRepository,
    private val settings: SettingsStore,
    private val scope: ViewerScope,
    startIndex: Int
) : ViewModel() {

    private val _items = MutableStateFlow<List<MediaEntity>>(emptyList())
    val items: StateFlow<List<MediaEntity>> = _items.asStateFlow()

    private val _index = MutableStateFlow(startIndex)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val settingsState: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val enriched = HashSet<Long>()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _loading.value = true
            val list = when (scope) {
                is ViewerScope.OfWork -> MediaSorter.sort(repo.filesOfWorkOnce(scope.workPath), scope.sort)
                is ViewerScope.OfTimeline -> repo.timelineList(scope.type, scope.favOnly)
            }
            _items.value = list
            val focus = (scope as? ViewerScope.OfWork)?.focusId
            val focusIndex = if (focus != null) list.indexOfFirst { it.id == focus } else -1
            _index.value = if (focusIndex >= 0) focusIndex
            else _index.value.coerceIn(0, (list.size - 1).coerceAtLeast(0))
            _loading.value = false
        }
    }

    fun setIndex(index: Int) {
        _index.value = index
    }

    fun current(): MediaEntity? = _items.value.getOrNull(_index.value)

    fun itemAt(index: Int): MediaEntity? = _items.value.getOrNull(index)

    fun toggleFavorite(item: MediaEntity) {
        viewModelScope.launch {
            repo.setFavorite(listOf(item.id), !item.favorite)
            _items.value = _items.value.map { if (it.id == item.id) it.copy(favorite = !item.favorite) else it }
        }
    }

    fun delete(item: MediaEntity, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val root = settings.current().rootPath
            val result = repo.delete(listOf(item), root)
            onDone(if (result.deleted > 0) "已删除" else "删除失败")
            reload()
        }
    }

    /** 视频时长懒补全，只在打开过这个视频时做一次 */
    fun ensureMetadata(item: MediaEntity) {
        synchronized(enriched) {
            if (!enriched.add(item.id)) return
        }
        viewModelScope.launch {
            if (item.type == PathParser.TYPE_VIDEO && item.durationMs == null) {
                val duration = MetadataEnricher.videoDuration(item.path) ?: return@launch
                repo.saveDuration(item.id, duration)
                _items.value = _items.value.map {
                    if (it.id == item.id) it.copy(durationMs = duration) else it
                }
            } else if (item.type != PathParser.TYPE_VIDEO && (item.width == null || item.height == null)) {
                val size = MetadataEnricher.imageSize(item.path) ?: return@launch
                repo.saveDimensions(item.id, size.first, size.second)
                _items.value = _items.value.map {
                    if (it.id == item.id) it.copy(width = size.first, height = size.second) else it
                }
            }
        }
    }

    suspend fun playbackPosition(path: String): Long = settings.playbackPosition(path)

    fun savePlaybackPosition(path: String, positionMs: Long) {
        viewModelScope.launch { settings.savePlaybackPosition(path, positionMs) }
    }

    fun clearPlaybackPosition(path: String) {
        viewModelScope.launch { settings.clearPlaybackPosition(path) }
    }

    /** 打开失败（文件被外部删掉）时，删掉这条记录并刷新 */
    fun onOpenFailed(item: MediaEntity, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            repo.removeMissingFile(item.path)
            onDone("文件不存在，已从索引移除")
            reload()
        }
    }
}
