package top.ipla.drama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import top.ipla.drama.data.ApiClient
import top.ipla.drama.data.DramaDetail
import top.ipla.drama.data.Episode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavHostController, dramaId: Int) {
    var detail by remember { mutableStateOf<DramaDetail?>(null) }
    var selectedEpisode by remember { mutableStateOf<Episode?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dramaId) {
        if (dramaId > 0) {
            try {
                val resp = ApiClient.service.getDramaDetail(dramaId)
                if (resp.status == "success") {
                    detail = resp.data
                    selectedEpisode = resp.data?.episodes?.firstOrNull()
                }
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

    val drama = detail?.drama ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(drama.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "返回")
                }
            }
        )

        Box(modifier = Modifier.fillMaxWidth().height(240.dp).background(Color.Black)) {
            selectedEpisode?.let { ep ->
                VideoPlayer(videoUrl = ep.videoUrl)
            } ?: Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("暂无剧集", color = Color.White)
            }
        }

        Text(
            "剧集列表",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(12.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(detail!!.episodes) { index, ep ->
                ListItem(
                    headlineContent = { Text("第${ep.order}集 ${ep.title}") },
                    supportingContent = { Text("${ep.duration}分钟") },
                    modifier = Modifier.clickable { selectedEpisode = ep },
                    leadingContent = {
                        if (selectedEpisode?.id == ep.id) {
                            Icon(Icons.Default.PlayArrow, "播放中", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = if (selectedEpisode?.id == ep.id)
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else ListItemDefaults.colors()
                )
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
            PlayerView(ctx).apply { player = exoPlayer }
        },
        modifier = Modifier.fillMaxSize()
    )
}
