package com.zhexian.douyingallery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhexian.douyingallery.Graph
import com.zhexian.douyingallery.data.db.MediaEntity
import com.zhexian.douyingallery.ui.common.EmptyState
import com.zhexian.douyingallery.ui.common.MediaThumb
import com.zhexian.douyingallery.ui.common.WorkRow
import com.zhexian.douyingallery.ui.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenFile: (MediaEntity) -> Unit
) {
    val vm: SearchViewModel = viewModel(factory = vmFactory { SearchViewModel(Graph.repository) })
    val query by vm.query.collectAsStateWithLifecycle()
    val result by vm.results.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text("搜 UP主 / 作品 / 角色") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "清空")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            when {
                query.isBlank() -> EmptyState(
                    text = "输入关键词开始搜索",
                    hint = "同时搜索 UP主、作品名、角色名；有 meta.json 时还会搜完整简介和话题"
                )

                result.isEmpty -> EmptyState(text = "没有匹配的内容", hint = "试试更短的关键词")

                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (result.creators.isNotEmpty()) {
                        item { SectionTitle("UP主") }
                        items(result.creators, key = { "c-${it.creator}" }) { creator ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(creator.creator, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${creator.workCount} 个作品 · ${creator.fileCount} 个文件",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                )
                            }
                            androidx.compose.material3.Divider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                    }
                    if (result.works.isNotEmpty()) {
                        item { SectionTitle("作品") }
                        items(result.works, key = { "w-${it.workPath}" }) { work ->
                            WorkRow(work = work, onClick = { onOpenWork(work.workPath) })
                        }
                    }
                    if (result.files.isNotEmpty()) {
                        item { SectionTitle("文件（${result.files.size}）") }
                        result.files.withIndex().chunked(4).forEach { row ->
                            item(key = "frow-${row.first().index}") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    row.forEach { indexed ->
                                        MediaThumb(
                                            item = indexed.value,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onOpenFile(indexed.value) }
                                        )
                                    }
                                    repeat(4 - row.size) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp)
    )
}
