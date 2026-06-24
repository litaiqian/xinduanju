package top.ipla.drama.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import top.ipla.drama.data.ApiClient
import top.ipla.drama.data.LoginRequest
import top.ipla.drama.data.PreferencesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text(
            if (isRegister) "注册" else "登录",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    message = ""
                    try {
                        val req = LoginRequest(username, password)
                        val resp = if (isRegister) ApiClient.service.register(req)
                                   else ApiClient.service.login(req)
                        if (resp.status == "success" && resp.token.isNotEmpty()) {
                            prefs.saveLogin(resp.token, username)
                            navController.popBackStack()
                        } else {
                            message = resp.message.ifEmpty { "操作失败" }
                        }
                    } catch (e: Exception) {
                        message = "网络错误: ${e.message}"
                    }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
            else Text(if (isRegister) "注册" else "登录")
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { isRegister = !isRegister; message = "" }) {
            Text(if (isRegister) "已有账号? 登录" else "没有账号? 注册")
        }

        if (message.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}
