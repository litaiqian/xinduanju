package top.ipla.drama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import top.ipla.drama.data.ApiClient
import top.ipla.drama.data.DramaDetail
import top.ipla.drama.data.Episode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavHostController, dramaId: Int) {
    var detail by remember { mutableStateOf<DramaDetail?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showEpisodeList by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(dramaId) {
        if (dramaId > 0) {
            try {
                val resp = ApiClient.service.getDramaDetail(dramaId)
                if (resp.status == "success") detail = resp.data
            } catch (_: Exception) { }
        }
        isLoading = false
    }

    if (dramaId == 0) {
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
        // 视频播放区
        currentEp?.let { ep ->
            VideoPlayer(
                videoUrl = ep.videoUrl,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(currentIndex) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, _ -> },
                            onDragEnd = {
                                if (currentIndex < episodes.lastIndex) currentIndex++
                                else if (currentIndex > 0) currentIndex--
                            }
                        )
                    }
            )
        }

        // 顶部返回按钮
        IconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).statusBarsPadding()
        ) {
            Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
        }

        // 剧集信息覆盖层
        if (currentEp != null) {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp).navigationBarsPadding()
            ) {
                Text(
                    "${detail?.drama?.title}",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Text(
                    "第${currentEp.order}集 ${currentEp.title}",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { if (currentIndex > 0) currentIndex-- }) {
                        Text("⬆ 上一集", color = Color.White.copy(alpha = 0.7f))
                    }
                    TextButton(onClick = { showEpisodeList = !showEpisodeList }) {
                        Text(if (showEpisodeList) "收起列表" else "📋 剧集列表", color = Color.White.copy(alpha = 0.7f))
                    }
                    TextButton(onClick = { if (currentIndex < episodes.lastIndex) currentIndex++ }) {
                        Text("下一集 ⬇", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }

    // 剧集列表面板
    if (showEpisodeList) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))
                .padding(top = 80.dp, bottom = 120.dp)
                .navigationBarsPadding()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                itemsIndexed(episodes) { index, ep ->
                    Surface(
                        color = if (index == currentIndex) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                        onClick = { currentIndex = index; showEpisodeList = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (index == currentIndex) {
                                Icon(Icons.Default.PlayArrow, "播放中", tint = Color(0xFFFF6B35))
                                Spacer(Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    "第${ep.order}集 ${ep.title}",
                                    color = Color.White, fontSize = 16.sp
                                )
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
fun VideoPlayer(videoUrl: String, modifier: Modifier = Modifier) {
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
        modifier = modifier
    )
}
