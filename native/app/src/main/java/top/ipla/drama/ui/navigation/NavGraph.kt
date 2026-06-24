package top.ipla.drama.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import top.ipla.drama.ui.screens.*

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun DramaNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val hideBottomBar = currentRoute == "player" || currentRoute?.startsWith("player/") == true

    val items = listOf(
        BottomNavItem("首页", Icons.Default.Home, "home"),
        BottomNavItem("播放", Icons.Default.PlayArrow, "player"),
        BottomNavItem("我的", Icons.Default.Person, "profile"),
    )

    Scaffold(
        bottomBar = {
            if (!hideBottomBar) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen(navController) }
            composable("player/{dramaId}") { backStackEntry ->
                val dramaId = backStackEntry.arguments?.getString("dramaId") ?: ""
                PlayerScreen(navController, dramaId)
            }
            composable("player") { PlayerScreen(navController, "") }
            composable("profile") { ProfileScreen(navController) }
            composable("login") { LoginScreen(navController) }
            composable("history") { HistoryScreen(navController) }
        }
    }
}
