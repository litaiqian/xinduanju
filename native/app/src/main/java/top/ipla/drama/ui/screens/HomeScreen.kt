package top.ipla.drama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.ipla.drama.data.ApiClient
import top.ipla.drama.data.Drama

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val categories = listOf("推荐", "最新", "热门", "都市", "古装", "甜宠")
    var selectedCategory by remember { mutableStateOf("推荐") }
    var dramas by remember { mutableStateOf<List<Drama>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var loaded by remember { mutableStateOf(false) }
    var lastLoadTime by remember { mutableLongStateOf(0L) }

    // 分类缓存: {category: (dramas, page, hasMore)}
    val categoryCache = remember { mutableStateMapOf<String, Triple<List<Drama>, Int, Boolean>>() }

    // 加载数据
    fun loadPage(cat: String, page: Int, pageSize: Int, append: Boolean) {
        scope.launch {
            if (page == 1) isLoading = true else loadingMore = true
            try {
                val kw = if (cat == "推荐") "" else cat
                val resp = ApiClient.service.getDramaList(kw, page, pageSize)
                if (resp.status == "success") {
                    val newData = if (append) dramas + resp.data else resp.data
                    dramas = newData
                    currentPage = page
                    hasMore = resp.hasMore
                    // 缓存分类数据
                    if (resp.data.isNotEmpty()) {
                        categoryCache[cat] = Triple(newData, page, resp.hasMore)
                    }
                }
            } catch (_: Exception) { }
            isLoading = false
            loadingMore = false
            lastLoadTime = System.currentTimeMillis()
            loaded = true
        }
    }

    // 首次加载
    LaunchedEffect(Unit) {
        if (!loaded) loadPage(selectedCategory, 1, 6, false)
    }

    // 分类切换
    LaunchedEffect(selectedCategory) {
        if (!loaded) return@LaunchedEffect
        val cached = categoryCache[selectedCategory]
        if (cached != null) {
            // 命中缓存：瞬间切换
            dramas = cached.first
            currentPage = cached.second
            hasMore = cached.third
        } else {
            // 无缓存：加载新分类
            dramas = emptyList()
            currentPage = 1
            hasMore = true
            loadPage(selectedCategory, 1, 6, false)
        }
    }

    // 刷新
    LaunchedEffect(refreshTrigger) {
        categoryCache.clear()
        dramas = emptyList()
        currentPage = 1
        hasMore = true
        loadPage(selectedCategory, 1, 6, false)
    }

    // 滚动检测加载更多（带防抖）
    val shouldLoadMore = remember { derivedStateOf {
        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = gridState.layoutInfo.totalItemsCount
        // 滑到倒数第2个触发加载
        lastVisible >= totalItems - 2 && hasMore && !loadingMore && !isLoading && totalItems > 0
    }}

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            // 防抖：1秒内不重复加载
            val now = System.currentTimeMillis()
            if (now - lastLoadTime < 1000) return@LaunchedEffect
            val nextPage = currentPage + 1
            val pageSize = if (nextPage % 3 == 1) 6 else 2
            loadPage(selectedCategory, nextPage, pageSize, true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("短剧大全", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            actions = {
                IconButton(onClick = { refreshTrigger++ }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, "刷新", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        if (isLoading && !loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dramas) { drama ->
                    DramaCard(drama = drama) {
                        navController.navigate("player/${drama.id}")
                    }
                }
                if (loadingMore) {
                    item(span = { GridItemSpan(2) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DramaCard(drama: Drama, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium
    ) {
        Column {
            AsyncImage(
                model = drama.cover,
                contentDescription = drama.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    drama.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⭐ ${drama.score}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${drama.episodeCount}集",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
