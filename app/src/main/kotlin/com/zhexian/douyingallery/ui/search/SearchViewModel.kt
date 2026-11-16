package com.zhexian.douyingallery.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhexian.douyingallery.data.repo.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repo: MediaRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<MediaRepository.SearchResult> = _query
        .debounce(220)
        .distinctUntilChanged()
        .flatMapLatest { keyword ->
            flow {
                emit(if (keyword.isBlank()) MediaRepository.SearchResult() else repo.search(keyword))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaRepository.SearchResult())

    fun setQuery(text: String) {
        _query.value = text
    }
}
