package com.zhexian.douyingallery.ui.creator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhexian.douyingallery.data.db.WorkEntity
import com.zhexian.douyingallery.data.repo.MediaRepository
import com.zhexian.douyingallery.domain.WorkSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class CreatorViewModel(
    private val repo: MediaRepository,
    val creator: String
) : ViewModel() {

    private val _sort = MutableStateFlow(WorkSort.LATEST)
    val sort: StateFlow<WorkSort> = _sort.asStateFlow()

    val works: StateFlow<List<WorkEntity>> =
        combine(repo.worksOfCreator(creator), _sort) { list, sort ->
            when (sort) {
                WorkSort.LATEST -> list.sortedByDescending { it.latestMtime }
                WorkSort.NAME -> list.sortedBy { it.work.lowercase() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSort(sort: WorkSort) {
        _sort.value = sort
    }
}
