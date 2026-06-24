package top.ipla.drama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import top.ipla.drama.data.ApiClient
import top.ipla.drama.data.DramaDetail
import top.ipla.drama.data.Episode

private const val API_KEY = "mWqvYloCyXJjhm3kdif9VgZYak"
private const val API_BD = "https://www.52api.cn/api/bd_duanju"
private const val API_HG = "https://www.52api.cn/api/hg_new"

suspend fun fetchVideoUrlDirect(videoId: String): String = withContext(Dispatchers.IO) {
    var url = tryGetVideoUrl(API_BD, videoId)
    if (url.isEmpty()) url = tryGetVideoUrl(API_HG, videoId)
    url
}

private fun tryGetVideoUrl(apiUrl: String, videoId: String): String = try {
    val url = URL("$apiUrl?key=$API_KEY&type=video&video_id=$videoId")
    val conn = url.openConnection() as HttpURLConnection
    conn.connectTimeout = 10000; conn.readTimeout = 15000
    val text = conn.inputStream.bufferedReader().readText(); conn.disconnect()
    val json = JSONObject(text)
    if (json.getInt("code") == 200) {
        val data = json.getJSONObject("data")
        if (data.has("qualities")) data.getJSONArray("qualities").getJSONObject(0).getString("download_url")
        else if (data.has("video_lists")) data.getJSONArray("video_lists").getJSONObject(0).getString("url")
        else ""
    } else ""
} catch (e: Exception) { "" }

@Composable
fun PlayerScreen(navController: NavHostController, dramaId: String) {
    var detail by remember { mutableStateOf<DramaDetail?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentVideoUrl by remember { mutableStateOf("") }
    var showEpisodeList by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // 预加载缓存: videoId -> videoUrl
    val preloadedUrls = remember { mutableStateMapOf<String, String>() }

    // 切换剧集
    fun switchEpisode(index: Int) {
        if (index < 0 || index >= (detail?.episodes?.size ?: 0)) return
        currentIndex = index
        val ep = detail?.episodes?.getOrNull(index) ?: return
        if (ep.videoId.isEmpty()) return
        // 优先用预加载
        val cached = preloadedUrls[ep.videoId]
        if (cached != null) {
            currentVideoUrl = cached
        } else {
            scope.launch {
                currentVideoUrl = fetchVideoUrlDirect(ep.videoId)
            }
        }
        // 触发预加载后续剧集
        scope.launch { preloadNextEpisodes(index, detail, preloadedUrls) }
    }

    LaunchedEffect(dramaId) {
        if (dramaId.isNotEmpty()) {
            try {
                val resp = ApiClient.service.getDramaDetail(dramaId)
                if (resp.status == "success") {
                    detail = resp.data
                    val ep = resp.data?.episodes?.firstOrNull()
                    if (ep != null && ep.videoId.isNotEmpty()) {
                        currentVideoUrl = fetchVideoUrlDirect(ep.videoId)
                    }
                    // 首次预加载
                    preloadNextEpisodes(0, resp.data, preloadedUrls)
                }
            } catch (_: Exception) { }
        }
        isLoading = false
    }

    if (dramaId.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Text("请从首页选择一部短剧观看", color = Color.Gray)
            }
        }
        return
    }
    if (isLoading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }

    val episodes = detail?.episodes ?: emptyList()
    val currentEp = episodes.getOrNull(currentIndex)

    // 播放器状态
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var fastForwarding by remember { mutableStateOf(false) }
    var rewinding by remember { mutableStateOf(false) }

    // 进度轮询
    LaunchedEffect(exoPlayer) {
        exoPlayer?.let { player ->
            while (true) {
                val dur = player.duration
                val pos = player.currentPosition
                if (dur > 0) {
                    progress = pos.toFloat() / dur
                    duration = dur
                    // 预加载检测
                    val pct = progress * 100
                    val idx = currentIndex
                    if (pct >= 30 && idx + 1 < episodes.size) preloadNextEpisodes(idx, detail, preloadedUrls)
                    if (pct >= 60 && idx + 2 < episodes.size) preloadNextEpisodes(idx, detail, preloadedUrls)
                    if (pct >= 90 && idx + 3 < episodes.size) preloadNextEpisodes(idx, detail, preloadedUrls)
                }
                delay(500)
            }
        }
    }

    // 快进/快退效果
    LaunchedEffect(fastForwarding) {
        if (fastForwarding) {
            while (fastForwarding) { exoPlayer?.seekTo((exoPlayer?.currentPosition ?: 0) + 2000); delay(200) }
        }
    }
    LaunchedEffect(rewinding) {
        if (rewinding) {
            while (rewinding) { exoPlayer?.seekTo(((exoPlayer?.currentPosition ?: 2000) - 2000).coerceAtLeast(0)); delay(200) }
        }
    }

    var dragStartY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(currentIndex) {
            detectVerticalDragGestures(
                onVerticalDrag = { _, amount -> dragStartY += amount },
                onDragEnd = {
                    if (dragStartY < -100 && currentIndex > 0) {
                        switchEpisode(currentIndex - 1)
                    } else if (dragStartY > 100 && currentIndex < episodes.lastIndex) {
                        switchEpisode(currentIndex + 1)
                    }
                    dragStartY = 0f
                }
            )
        }
    ) {
        // 全屏视频
        if (currentVideoUrl.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    ExoPlayer.Builder(ctx).build().also { player ->
                        exoPlayer = player
                        player.setMediaItem(MediaItem.fromUri(currentVideoUrl))
                        player.prepare()
                        player.playWhenReady = true
                    }.let { p ->
                        PlayerView(ctx).apply { player = p; useController = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view -> view.player = exoPlayer }
            )
        }

        // 左右长按区域
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧：长按倒退
            Box(modifier = Modifier.weight(1f).fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { rewinding = true },
                        onPress = { tryAwaitRelease(); rewinding = false }
                    )
                }
            )
            // 中间：点击暂停/播放
            Box(modifier = Modifier.weight(1f).fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { isPlaying = !isPlaying; exoPlayer?.playWhenReady = isPlaying })
                }
            )
            // 右侧：长按快进
            Box(modifier = Modifier.weight(1f).fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { fastForwarding = true },
                        onPress = { tryAwaitRelease(); fastForwarding = false }
                    )
                }
            )
        }

        // 顶部按钮
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White, modifier = Modifier.size(28.dp)) }
            IconButton(onClick = { showEpisodeList = !showEpisodeList }) { Icon(Icons.Default.List, "剧集", tint = Color.White, modifier = Modifier.size(28.dp)) }
        }

        // 底部：进度条 + 剧集信息
        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding()
            .pointerInput(currentIndex) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, _ -> },
                    onDragEnd = { }
                )
            }
        ) {
            // 进度条
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime((progress * duration).toLong()), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Slider(
                    value = progress, onValueChange = { v -> progress = v; exoPlayer?.seekTo((v * duration).toLong()) },
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFFF6B35), inactiveTrackColor = Color.White.copy(alpha = 0.3f))
                )
                Text(formatTime(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            // 剧集信息
            if (currentEp != null && !showEpisodeList) {
                Text("${detail?.drama?.title}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("第${currentEp.order}集 ${currentEp.title} ｜ ${currentIndex + 1}/${episodes.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        // 快进提示
        if (fastForwarding) {
            Box(Modifier.align(Alignment.Center).background(Color.Black.copy(0.5f), MaterialTheme.shapes.medium).padding(16.dp)) { Text("⏩ 快进中", color = Color.White, fontSize = 18.sp) }
        }
        if (rewinding) {
            Box(Modifier.align(Alignment.Center).background(Color.Black.copy(0.5f), MaterialTheme.shapes.medium).padding(16.dp)) { Text("⏪ 快退中", color = Color.White, fontSize = 18.sp) }
        }
    }

    // 剧集列表
    if (showEpisodeList) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
            IconButton(onClick = { showEpisodeList = false }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Default.List, "关闭", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 70.dp, bottom = 40.dp).navigationBarsPadding().padding(horizontal = 16.dp)) {
                item { Text("${detail?.drama?.title} — 剧集列表", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)) }
                itemsIndexed(episodes) { index, ep ->
                    Surface(color = if (index == currentIndex) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                        onClick = { showEpisodeList = false; switchEpisode(index) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (index == currentIndex) { Icon(Icons.Default.PlayArrow, "播放中", tint = Color(0xFFFF6B35)); Spacer(Modifier.width(8.dp)) }
                            else { Text("${index + 1}", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.width(32.dp)) }
                            Column { Text("第${ep.order}集 ${ep.title}", color = Color.White, fontSize = 16.sp); Text("${ep.duration}分钟", color = Color.Gray, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}

/** 预加载后续3集 */
suspend fun preloadNextEpisodes(currentIdx: Int, detail: DramaDetail?, cache: MutableMap<String, String>) {
    val eps = detail?.episodes ?: return
    for (i in 1..3) {
        val ep = eps.getOrNull(currentIdx + i) ?: continue
        if (ep.videoId.isEmpty() || cache.containsKey(ep.videoId)) continue
        val url = fetchVideoUrlDirect(ep.videoId)
        if (url.isNotEmpty()) cache[ep.videoId] = url
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${String.format("%02d", s % 60)}"
}
