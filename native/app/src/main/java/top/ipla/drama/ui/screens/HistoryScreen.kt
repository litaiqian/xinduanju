package top.ipla.drama.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import top.ipla.drama.data.ApiClient
import top.ipla.drama.data.Drama
import top.ipla.drama.data.PreferencesManager
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var history by remember { mutableStateOf<List<Drama>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val token = prefs.getToken()
            val auth = if (token.isNotEmpty()) "Bearer $token" else ""
            val resp = ApiClient.service.getHistory(auth)
            if (resp.status == "success") {
                history = resp.data
            }
        } catch (_: Exception) { }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("观看历史") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "返回")
                }
            }
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无观看记录", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn {
                items(history) { item ->
                    ListItem(
                        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${item.episodeCount}集") }
                    )
                }
            }
        }
    }
}
