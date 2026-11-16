package com.zhexian.douyingallery.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhexian.douyingallery.BuildConfig
import com.zhexian.douyingallery.Graph
import com.zhexian.douyingallery.ui.vmFactory
import com.zhexian.douyingallery.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(
        factory = vmFactory { SettingsViewModel(Graph.repository, Graph.settings) }
    )
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var editRoot by remember { mutableStateOf(false) }
    var rootInput by remember { mutableStateOf(settings.rootPath) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.loadStats() }
    LaunchedEffect(settings.rootPath) { rootInput = settings.rootPath }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (busy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("  正在处理…", style = MaterialTheme.typography.bodySmall)
                }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SectionHeader("数据源") }
                item {
                    SettingRow(
                        title = "根目录",
                        subtitle = settings.rootPath,
                        onClick = { editRoot = true }
                    )
                }
                item { SectionHeader("显示") }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("网格列数", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (2..5).forEach { columns ->
                                FilterChip(
                                    selected = settings.gridColumns == columns,
                                    onClick = { vm.setGridColumns(columns) },
                                    label = { Text("$columns 列") }
                                )
                            }
                        }
                    }
                }
                item { SectionHeader("播放") }
                item {
                    SettingSwitch(
                        title = "默认循环播放",
                        subtitle = "短视频习惯：播完自动重播",
                        checked = settings.loopVideo,
                        onCheckedChange = vm::setLoopVideo
                    )
                }
                item {
                    SettingSwitch(
                        title = "默认静音",
                        subtitle = "进入视频时默认不发出声音",
                        checked = settings.muteVideo,
                        onCheckedChange = vm::setMuteVideo
                    )
                }
                item { SectionHeader("索引与缓存") }
                item {
                    SettingRow(
                        title = "重建索引",
                        subtitle = "全量重扫 ${settings.rootPath}，扫描期间可继续浏览",
                        onClick = { vm.rebuildIndex() }
                    )
                }
                item {
                    SettingRow(
                        title = "清除缩略图缓存",
                        subtitle = "当前缓存 ${Format.size(stats.cacheBytes)}（位于 App 私有目录，相册不可见）",
                        onClick = { vm.clearCache() }
                    )
                }
                item { SectionHeader("存储统计") }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "共 ${stats.files} 个文件 · ${stats.works} 个作品 · ${Format.size(stats.size)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        stats.byCreator.forEach { (name, size) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = Format.size(size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                item { SectionHeader("安全") }
                item {
                    SettingSwitch(
                        title = "应用锁",
                        subtitle = "开启后回到前台超过 ${settings.lockTimeoutMinutes} 分钟需要指纹解锁；同时禁止截屏",
                        checked = settings.lockEnabled,
                        onCheckedChange = vm::setLockEnabled
                    )
                }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("锁定时长", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 3, 10).forEach { minutes ->
                                FilterChip(
                                    selected = settings.lockTimeoutMinutes == minutes,
                                    onClick = { vm.setLockTimeout(minutes) },
                                    label = { Text("$minutes 分钟") }
                                )
                            }
                        }
                    }
                }
                item { SectionHeader("关于") }
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("抖音解析图库 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "完全离线：不联网、不上传、不改文件名、不删 .nomedia。\n" +
                                "缩略图缓存在 App 私有目录，系统相册看不到。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (editRoot) {
        AlertDialog(
            onDismissRequest = { editRoot = false },
            title = { Text("根目录") },
            text = {
                Column {
                    OutlinedTextField(
                        value = rootInput,
                        onValueChange = { rootInput = it },
                        singleLine = true,
                        label = { Text("绝对路径") }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "修改后会清空旧路径的索引并重新全量扫描。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editRoot = false
                    vm.setRootPath(rootInput)
                }) { Text("保存并重建") }
            },
            dismissButton = {
                TextButton(onClick = { editRoot = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp)
    )
    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
}
