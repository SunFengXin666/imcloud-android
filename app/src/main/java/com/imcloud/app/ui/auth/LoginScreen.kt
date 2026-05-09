package com.imcloud.app.ui.auth

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private val httpClient = okhttp3.OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .build()

private val mainHandler = Handler(Looper.getMainLooper())

@Composable
fun LoginScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Text("清云", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1D1F))
            Spacer(modifier = Modifier.height(4.dp))
            Text("智能 AI 助手", fontSize = 15.sp, color = Color(0xFF8E8E93))
            Spacer(modifier = Modifier.height(40.dp))

            // 用 BasicTextField 替代 OutlinedTextField
            BasicTextField(
                value = username,
                onValueChange = { username = it; status = "" },
                textStyle = LocalTextStyle.current.copy(color = Color(0xFF1D1D1F), fontSize = 16.sp),
                cursorBrush = SolidColor(Color(0xFF007AFF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD1D1D6), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (username.isEmpty()) {
                            Text("用户名", color = Color(0xFF8E8E93), fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            BasicTextField(
                value = password,
                onValueChange = { password = it; status = "" },
                textStyle = LocalTextStyle.current.copy(color = Color(0xFF1D1D1F), fontSize = 16.sp),
                cursorBrush = SolidColor(Color(0xFF007AFF)),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD1D1D6), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (password.isEmpty()) {
                            Text("密码", color = Color(0xFF8E8E93), fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        status = "请输入用户名和密码"
                        return@Button
                    }
                    isLoading = true
                    status = "登录中..."
                    val u = username.trim()
                    val p = password

                    Thread {
                        try {
                            val jsonBody = """{"username":"$u","password":"$p"}"""
                            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
                            val request = Request.Builder()
                                .url("http://49.232.224.90:8080/api/auth/login")
                                .post(requestBody).build()
                            val response = httpClient.newCall(request).execute()
                            val body = response.body?.string() ?: ""
                            val code = response.code
                            response.close()

                            mainHandler.post {
                                isLoading = false
                                if (code == 200 && body.contains("token")) {
                                    val tokenMatch = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)
                                    val token = tokenMatch?.groupValues?.get(1) ?: ""
                                    if (token.isNotEmpty()) {
                                        val prefs = context.getSharedPreferences("imcloud_prefs", android.content.Context.MODE_PRIVATE)
                                        prefs.edit().putString("auth_token", token).apply()
                                        status = "✅ 登录成功"
                                        onNavigateToChat()
                                    } else {
                                        status = "❌ token解析失败"
                                    }
                                } else {
                                    val err = Regex(""""error"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                                    status = err ?: "HTTP $code"
                                }
                            }
                        } catch (e: Exception) {
                            mainHandler.post {
                                isLoading = false
                                status = "❌ ${e.message}"
                            }
                        }
                    }.start()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("登录", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            if (status.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(status, fontSize = 13.sp, color = Color(0xFF34C759))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("还没有账号？", color = Color(0xFF8E8E93), fontSize = 14.sp)
                TextButton(onClick = onNavigateToRegister) {
                    Text("立即注册", color = Color(0xFF007AFF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
