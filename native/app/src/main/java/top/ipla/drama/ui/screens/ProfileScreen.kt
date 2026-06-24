package top.ipla.drama.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import top.ipla.drama.data.ApiClient
import top.ipla.drama.data.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf(0) }
    var signMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoggedIn = prefs.isLoggedIn()
        if (isLoggedIn) {
            username = prefs.getUsername()
            try {
                val token = prefs.getToken()
                val resp = ApiClient.service.getUserInfo("Bearer $token")
                if (resp.status == "success") {
                    resp.user?.let { points = it.points }
                }
            } catch (_: Exception) { }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("我的") })

        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                if (isLoggedIn) {
                    Text(username, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("积分: $points", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("未登录", fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { navController.navigate("login") }) {
                        Text("登录 / 注册")
                    }
                }
            }
        }

        if (isLoggedIn) {
            ListItem(
                headlineContent = { Text("签到领积分") },
                leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                modifier = Modifier.clickable {
                    scope.launch {
                        try {
                            val token = prefs.getToken()
                            val resp = ApiClient.service.checkIn("Bearer $token")
                            signMessage = if (resp.status == "success")
                                "签到成功! +${resp.pointsEarned} 积分, 总积分: ${resp.totalPoints}"
                            else resp.message
                            points = resp.totalPoints
                        } catch (_: Exception) {
                            signMessage = "签到失败, 请重试"
                        }
                    }
                }
            )
            if (signMessage.isNotEmpty()) {
                Text(signMessage, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
            }
        }

        ListItem(
            headlineContent = { Text("观看历史") },
            leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
            modifier = Modifier.clickable { navController.navigate("history") }
        )

        if (isLoggedIn) {
            ListItem(
                headlineContent = { Text("退出登录") },
                leadingContent = { Icon(Icons.Default.Logout, contentDescription = null) },
                modifier = Modifier.clickable {
                    scope.launch {
                        prefs.logout()
                        isLoggedIn = false
                        username = ""
                        points = 0
                    }
                }
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            "短剧大全 v1.0.0",
            modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            fontSize = 12.sp
        )
    }
}
