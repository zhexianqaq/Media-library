package com.zhexian.douyingallery.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.zhexian.douyingallery.Graph
import com.zhexian.douyingallery.data.db.MediaEntity
import com.zhexian.douyingallery.data.scan.PathParser
import com.zhexian.douyingallery.domain.ViewerScope
import com.zhexian.douyingallery.ui.vmFactory
import com.zhexian.douyingallery.util.FileOps
import com.zhexian.douyingallery.util.Format
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import java.io.File

@Composable
fun ViewerScreen(
    scope: ViewerScope,
    startIndex: Int,
    onBack: () -> Unit
) {
    val vm: ViewerViewModel = viewModel(
        key = "viewer-$scope",
        factory = vmFactory { ViewerViewModel(Graph.repository, Graph.settings, scope, startIndex) }
    )
    val items by vm.items.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val settings by vm.settingsState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? Activity
    val scopeCoroutine = rememberCoroutineScope()

    var chromeVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (loading) "正在加载" else "没有可显示的内容",
                color = Color.White
            )
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = index.coerceIn(0, items.lastIndex)) { items.size }

    // 页码同步到 ViewModel，供顶栏信息与文件操作使用
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { vm.setIndex(it) }
    }

    val current = items.getOrNull(pagerState.currentPage)
    if (current != null) {
        LaunchedEffect(current.id) { vm.ensureMetadata(current) }
    }

    // 沉浸式：隐藏系统栏
    val view = LocalView.current
    LaunchedEffect(chromeVisible) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (chromeVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)) {

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            val isCurrent = page == pagerState.currentPage
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { chromeVisible = !chromeVisible }
            ) {
                if (item.type == PathParser.TYPE_VIDEO) {
                    if (isCurrent) {
                        VideoPane(
                            item = item,
                            vm = vm,
                            loop = settings.loopVideo,
                            mute = settings.muteVideo,
                            chromeVisible = chromeVisible,
                            onToggleChrome = { chromeVisible = !chromeVisible }
                        )
                    } else {
                        VideoPoster(item)
                    }
                } else {
                    // 超长简介图（宽高比 > 3）默认适配宽度、竖向滚动；普通图整体适配。
                    // Telephoto 会按可见区域做子采样解码，20000px 高的长图也不会 OOM。
                    val aspect = if (!item.width.isNullOrZero() && !item.height.isNullOrZero()) {
                        item.height!!.toFloat() / item.width!!.toFloat()
                    } else {
                        1f
                    }
                    ZoomableAsyncImage(
                        model = File(item.path),
                        contentDescription = item.displayName,
                        contentScale = if (aspect > 3f) ContentScale.FillWidth else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (chromeVisible) {
            // 顶栏
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(top = 28.dp, bottom = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current?.work ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                current?.role?.let { append(it) }
                                current?.seq?.let { append("-").append(it.toString().padStart(3, '0')) }
                                if (current?.isCaption == true) append(" · 正文长图")
                                append("  ")
                                append("${pagerState.currentPage + 1} / ${items.size}")
                            },
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }

            // 底栏
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(top = 24.dp, bottom = 18.dp)
            ) {
                if (current != null && current.type == PathParser.TYPE_VIDEO && current.durationMs != null) {
                    Text(
                        text = "时长 ${Format.duration(current.durationMs)}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ViewerAction("分享", Icons.Filled.Share) {
                        val item = current ?: return@ViewerAction
                        val intent = FileOps.buildShareIntent(context, listOf(item))
                        if (intent == null) toast(context, "分享失败") else runCatching {
                            context.startActivity(Intent.createChooser(intent, "分享到"))
                        }
                    }
                    ViewerAction("导出", Icons.Filled.Download) {
                        val item = current ?: return@ViewerAction
                        scopeCoroutine.launch {
                            val result = vm.let { Graph.repository.exportToGallery(context, listOf(item)) }
                            toast(context, if (result.success > 0) "已导出到相册" else "导出失败")
                        }
                    }
                    val favorite = current?.favorite == true
                    ViewerAction(
                        label = if (favorite) "已收藏" else "收藏",
                        icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
                    ) {
                        current?.let { vm.toggleFavorite(it) }
                    }
                    ViewerAction("信息", Icons.Filled.Info) { showInfo = true }
                    ViewerAction("删除", Icons.Filled.Delete) { confirmDelete = true }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Graph.player.pause()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (showInfo && current != null) {
        val item = current
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("文件信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoLine("作品", item.work)
                    InfoLine("UP主", item.creator)
                    InfoLine("文件", item.displayName)
                    InfoLine("路径", item.path)
                    InfoLine("大小", Format.size(item.size))
                    InfoLine("尺寸", if (item.width != null) "${item.width} × ${item.height}" else "读取中")
                    InfoLine("修改时间", Format.dateTime(item.mtime))
                    if (item.durationMs != null) InfoLine("时长", Format.duration(item.durationMs))
                    InfoLine("格式", item.ext.uppercase())
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("关闭") } }
        )
    }

    if (confirmDelete && current != null) {
        val deleteTarget = current
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个文件？") },
            text = { Text("将从磁盘上直接删除：\n${deleteTarget.displayName}") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(deleteTarget) { msg -> toast(context, msg) }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ViewerAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VideoPoster(item: MediaEntity) {
    val context = LocalPlatformContext.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(File(item.path)).build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPane(
    item: MediaEntity,
    vm: ViewerViewModel,
    loop: Boolean,
    mute: Boolean,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player: ExoPlayer = remember { Graph.player.get() }
    var speed by remember { mutableStateOf(1f) }
    var loopEnabled by remember { mutableStateOf(loop) }
    var muted by remember { mutableStateOf(mute) }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(item.path) {
        player.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(item.path))))
        player.prepare()
        player.playWhenReady = true
        player.volume = if (muted) 0f else 1f
        onDispose {
            // 记住播放位置（超过 3 秒且离结尾超过 3 秒才记）
            val position = player.currentPosition
            val duration = runCatching { player.duration }.getOrDefault(0L)
            if (position > 3_000 && duration > 0 && position < duration - 3_000) {
                vm.savePlaybackPosition(item.path, position)
            } else if (duration > 0 && position >= duration - 3_000) {
                vm.clearPlaybackPosition(item.path)
            }
            player.pause()
        }
    }

    LaunchedEffect(item.path) {
        val saved = vm.playbackPosition(item.path)
        if (saved > 3_000) player.seekTo(saved)
    }

    LaunchedEffect(loopEnabled) {
        player.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setControllerAutoShow(true)
                    this.setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 视频附加控件：倍速 / 循环 / 静音 / 全屏
        if (chromeVisible) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 104.dp)
                    .background(Color.Black.copy(alpha = 0.45f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    speed = when (speed) {
                        0.5f -> 1f
                        1f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2f
                        else -> 0.5f
                    }
                }) { Text("${speed}×", color = Color.White, fontSize = 12.sp) }

                IconButton(onClick = { loopEnabled = !loopEnabled }) {
                    Icon(
                        Icons.Filled.Loop,
                        contentDescription = "循环",
                        tint = if (loopEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { muted = !muted }) {
                    Icon(
                        if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                        contentDescription = "静音",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {
                    val landscape = activity?.resources?.configuration?.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    activity?.requestedOrientation = if (landscape) {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = "全屏",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun Int?.isNullOrZero(): Boolean = this == null || this <= 0

private fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
