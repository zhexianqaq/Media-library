package com.zhexian.douyingallery.ui.work

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhexian.douyingallery.Graph
import com.zhexian.douyingallery.domain.FileSort
import com.zhexian.douyingallery.ui.common.EmptyState
import com.zhexian.douyingallery.ui.common.MediaThumb
import com.zhexian.douyingallery.ui.vmFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(
    workPath: String,
    onBack: () -> Unit,
    onOpenViewer: (FileSort, Int) -> Unit
) {
    val context = LocalContext.current
    val vm: WorkViewModel = viewModel(
        key = "work-$workPath",
        factory = vmFactory { WorkViewModel(Graph.repository, Graph.settings, workPath) }
    )
    val files by vm.files.collectAsStateWithLifecycle()
    val work by vm.work.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()

    var sortMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        scope.launch { snackbar.showSnackbar(text) }
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("已选 ${selected.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll() }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "全选")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = work?.work ?: workPath.substringAfterLast('/'),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(work?.creator ?: "")
                                    work?.let { append(" · ${it.count} 个文件 · ${it.imageCount} 图") }
                                    work?.let { if (it.videoCount > 0) append(" / ${it.videoCount} 视频") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            FileSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        vm.setSort(option)
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (selectionMode) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ActionItem("分享", Icons.Filled.Share) {
                            val items = vm.selectedItems()
                            val intent = vm.shareIntent(context, items)
                            if (intent == null) toast("分享失败：无法生成 URI") else runCatching {
                                context.startActivity(android.content.Intent.createChooser(intent, "分享到"))
                            }
                        }
                        ActionItem("导出", Icons.Filled.Download) {
                            vm.exportItems(context, vm.selectedItems()) { toast(it) }
                        }
                        ActionItem("收藏", Icons.Filled.Favorite) {
                            vm.favoriteSelected(true) { count -> toast("已收藏 $count 项") }
                        }
                        ActionItem("删除", Icons.Filled.Delete) { confirmDelete = true }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (files.isEmpty()) {
                EmptyState(text = "这个作品里没有文件")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.gridColumns.coerceIn(2, 5)),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(files, key = { _, item -> item.id }) { index, item ->
                        MediaThumb(
                            item = item,
                            selectionMode = selectionMode,
                            selected = item.id in selected,
                            onClick = {
                                if (selectionMode) vm.toggleSelection(item.id)
                                else if (!com.zhexian.douyingallery.util.MetadataEnricher.isValidMedia(item.path)) {
                                    vm.onOpenFailed(item)
                                    toast("文件已不存在，已移出索引")
                                } else {
                                    onOpenViewer(sort, index)
                                }
                            },
                            onLongClick = { vm.toggleSelection(item.id) },
                            onSizeKnown = { w, h -> vm.onThumbLoaded(item, w, h) }
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除 ${selected.size} 个文件？") },
            text = {
                Text(
                    "将从磁盘上直接删除，不进回收站。\n" +
                        "如果作品目录里只剩 .nomedia，会连目录一起清理（根目录永远不删）。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteItems(vm.selectedItems()) { toast(it) }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ActionItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
