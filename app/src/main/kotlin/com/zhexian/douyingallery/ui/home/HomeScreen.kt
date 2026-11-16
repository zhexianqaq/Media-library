package com.zhexian.douyingallery.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.zhexian.douyingallery.Graph
import com.zhexian.douyingallery.data.db.MediaEntity
import com.zhexian.douyingallery.domain.CreatorSort
import com.zhexian.douyingallery.domain.MediaFilter
import com.zhexian.douyingallery.ui.common.CreatorCard
import com.zhexian.douyingallery.ui.common.EmptyState
import com.zhexian.douyingallery.ui.common.MediaThumb
import com.zhexian.douyingallery.ui.vmFactory
import com.zhexian.douyingallery.util.Format
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCreator: (String) -> Unit,
    onOpenViewerOfTimeline: (MediaFilter, Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: HomeViewModel = viewModel(
        factory = vmFactory { HomeViewModel(Graph.repository, Graph.settings) }
    )
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val creators by vm.creators.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val creatorSort by vm.creatorSort.collectAsStateWithLifecycle()
    val progress by vm.scanProgress.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 回到前台时做增量扫描（技术方案 §5.4）
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) {
        vm.refresh(forceFull = false)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("抖音解析图库", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (progress.isRunning) progress.message.ifBlank { "正在扫描" }
                                else "共 ${creators.size} 位 UP主",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (tab == 0) {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                CreatorSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort.label) },
                                        onClick = {
                                            vm.setCreatorSort(sort)
                                            sortMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                )
                if (progress.isRunning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("UP主") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    label = { Text("全部") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                vm.refresh(forceFull = false) {
                    refreshing = false
                    scope.launch { snackbar.showSnackbar(it) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (tab == 0) {
                if (creators.isEmpty() && !progress.isRunning) {
                    EmptyState(
                        text = "还没有扫描到作品",
                        hint = "请确认 ${settings.rootPath} 目录存在，下拉刷新或到设置里重建索引"
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(creators, key = { it.creator }) { creator ->
                            CreatorCard(creator = creator, onClick = { onOpenCreator(creator.creator) })
                        }
                    }
                }
            } else {
                TimelineTab(
                    vm = vm,
                    filter = filter,
                    onFilterChange = { vm.setFilter(it) },
                    columns = settings.gridColumns,
                    onOpen = { index -> onOpenViewerOfTimeline(filter, index) }
                )
            }
        }
    }
}

private sealed interface TimelineEntry {
    data class Header(val day: String) : TimelineEntry
    data class Item(val item: MediaEntity, val index: Int) : TimelineEntry
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTab(
    vm: HomeViewModel,
    filter: MediaFilter,
    onFilterChange: (MediaFilter) -> Unit,
    columns: Int,
    onOpen: (Int) -> Unit
) {
    val pagingItems = remember(filter) { vm.timelineFor(filter) }.collectAsLazyPagingItems()

    val entries = remember(pagingItems.itemCount, filter) {
        val list = ArrayList<TimelineEntry>(pagingItems.itemCount + 8)
        var lastDay: String? = null
        for (i in 0 until pagingItems.itemCount) {
            val item = pagingItems.peek(i) ?: continue
            val day = Format.dayGroup(item.mtime)
            if (day != lastDay) {
                list.add(TimelineEntry.Header(day))
                lastDay = day
            }
            list.add(TimelineEntry.Item(item, i))
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilterChange(f) },
                    label = { Text(f.label) }
                )
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                EmptyState(
                    text = if (pagingItems.loadState.refresh is androidx.paging.LoadState.Loading) "正在加载"
                    else "这里还没有文件"
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns.coerceIn(2, 5)),
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is TimelineEntry.Header -> item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = entry.day,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp)
                            )
                        }

                        is TimelineEntry.Item -> item {
                            MediaThumb(
                                item = entry.item,
                                onClick = { onOpen(entry.index) },
                                onSizeKnown = { w, h -> vm.onThumbLoaded(entry.item, w, h) }
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
