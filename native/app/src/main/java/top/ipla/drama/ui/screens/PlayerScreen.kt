package top.ipla.drama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import top.ipla.drama.data.ApiClient
import top.ipla.drama.data.DramaDetail
import top.ipla.drama.data.Episode

@Composable
fun PlayerScreen(navController: NavHostController, dramaId: String) {
    var detail by remember { mutableStateOf<DramaDetail?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentVideoUrl by remember { mutableStateOf("") }
    var showEpisodeList by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dramaId) {
        if (dramaId.isNotEmpty()) {
            try {
                val resp = ApiClient.service.getDramaDetail(dramaId)
                if (resp.status == "success") {
                    detail = resp.data
                    // 自动加载第一集视频
                    val ep = resp.data?.episodes?.firstOrNull()
                    if (ep != null && ep.videoId.isNotEmpty()) {
                        val vr = ApiClient.service.getVideoUrl(ep.videoId)
                        if (vr.status == "success") currentVideoUrl = vr.data?.videoUrl ?: ""
                    }
                }
            } catch (_: Exception) { }
        }
        isLoading = false
    }

    if (dramaId.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Text("请从首页选择一部短剧观看", color = Color.Gray)
            }
        }
        return
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val episodes = detail?.episodes ?: emptyList()
    val currentEp = episodes.getOrNull(currentIndex)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 全屏视频
        if (currentVideoUrl.isNotEmpty()) {
            VideoPlayer(videoUrl = currentVideoUrl)
        }

        // 顶部：返回按钮 + 剧集列表按钮
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { showEpisodeList = !showEpisodeList }) {
                Icon(Icons.Default.List, "剧集列表", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        // 底部剧集信息 + 滑动手势切换
        if (currentEp != null && !showEpisodeList) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp).navigationBarsPadding()
                    .pointerInput(currentIndex) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, _ -> },
                            onDragEnd = {
                                if (currentIndex < episodes.lastIndex) {
                                    currentIndex++
                                    val ep = episodes.getOrNull(currentIndex)
                                    if (ep != null && ep.videoId.isNotEmpty()) {
                                        scope.launch {
                                            val vr = ApiClient.service.getVideoUrl(ep.videoId)
                                            if (vr.status == "success") currentVideoUrl = vr.data?.videoUrl ?: ""
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                Text(
                    "${detail?.drama?.title}",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Text(
                    "第${currentEp.order}集 ${currentEp.title} ｜ ${currentIndex + 1}/${episodes.size}",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "⬆ 上滑切下一集",
                    color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 剧集列表面板（右上角按钮打开）
    if (showEpisodeList) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
            // 关闭按钮
            IconButton(
                onClick = { showEpisodeList = false },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            ) {
                Icon(Icons.Default.List, "关闭列表", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .padding(top = 70.dp, bottom = 40.dp).navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Text(
                        "${detail?.drama?.title} — 剧集列表",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                itemsIndexed(episodes) { index, ep ->
                    Surface(
                        color = if (index == currentIndex) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                        onClick = {
                            currentIndex = index; showEpisodeList = false
                            val ep = episodes.getOrNull(index)
                            if (ep != null && ep.videoId.isNotEmpty()) {
                                scope.launch {
                                    val vr = ApiClient.service.getVideoUrl(ep.videoId)
                                    if (vr.status == "success") currentVideoUrl = vr.data?.videoUrl ?: ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (index == currentIndex) {
                                Icon(Icons.Default.PlayArrow, "播放中", tint = Color(0xFFFF6B35))
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Text(
                                    "${index + 1}", color = Color.Gray, fontSize = 16.sp,
                                    modifier = Modifier.width(32.dp)
                                )
                            }
                            Column {
                                Text("第${ep.order}集 ${ep.title}", color = Color.White, fontSize = 16.sp)
                                Text("${ep.duration}分钟", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
