package top.ipla.drama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import top.ipla.drama.ui.navigation.DramaNavHost
import top.ipla.drama.ui.theme.DramaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DramaTheme {
                DramaNavHost()
            }
        }
    }
}
