package com.zhexian.douyingallery.ui.creator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhexian.douyingallery.Graph
import com.zhexian.douyingallery.domain.WorkSort
import com.zhexian.douyingallery.ui.common.EmptyState
import com.zhexian.douyingallery.ui.common.WorkRow
import com.zhexian.douyingallery.ui.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorScreen(
    creator: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit
) {
    val vm: CreatorViewModel = viewModel(
        key = "creator-$creator",
        factory = vmFactory { CreatorViewModel(Graph.repository, creator) }
    )
    val works by vm.works.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(creator, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        WorkSort.entries.forEach { option ->
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (works.isEmpty()) {
                EmptyState(text = "这个 UP主 还没有作品")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = "${works.size} 个作品 · 共 ${works.sumOf { it.count }} 个文件",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(works, key = { it.workPath }) { work ->
                        WorkRow(work = work, onClick = { onOpenWork(work.workPath) })
                    }
                }
            }
        }
    }
}
