package com.imcloud.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.imcloud.app.BuildConfig
import com.imcloud.app.data.remote.ApiClient
import com.imcloud.app.util.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Composable
fun SettingsScreen(
    onNavigateToLogin: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var pendingUpdateUrl by remember { mutableStateOf("") }
    var pendingUpdateVersion by remember { mutableStateOf("") }
    var hasUpdate by remember { mutableStateOf(false) }

    // AI Config state
    var aiProvider by remember { mutableStateOf("") }
    var aiApiBase by remember { mutableStateOf("") }
    var aiApiKey by remember { mutableStateOf("") }
    var aiModel by remember { mutableStateOf("") }
    var isAiConfigVisible by remember { mutableStateOf(false) }
    var isSavingAi by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    val currentVersion = BuildConfig.VERSION_NAME

    // Load AI config on first composition
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val config = ApiClient.getAiConfig()
                aiProvider = config.provider_name
                aiApiBase = config.api_base
                aiApiKey = config.api_key
                aiModel = config.model
            } catch (e: Exception) {
                // ignore, keep empty
            }
        }
    }

    fun checkUpdate() {
        if (isCheckingUpdate || isDownloading) return
        isCheckingUpdate = true
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) { ApiClient.checkUpdate() }
                val obj = JsonParser.parseString(json).asJsonObject
                val serverVersion = obj.get("latest_version")?.asString ?: return@launch
                val apkUrl = obj.get("apk_url")?.asString ?: return@launch

                fun parseVersion(v: String): List<Int> = v.split(".").map { it.toIntOrNull() ?: 0 }
                val serverParts = parseVersion(serverVersion)
                val currentParts = parseVersion(currentVersion)
                val serverNum = serverParts.getOrElse(0) { 0 } * 10000 + serverParts.getOrElse(1) { 0 } * 100 + serverParts.getOrElse(2) { 0 }
                val currentNum = currentParts.getOrElse(0) { 0 } * 10000 + currentParts.getOrElse(1) { 0 } * 100 + currentParts.getOrElse(2) { 0 }

                if (serverNum > currentNum) {
                    pendingUpdateUrl = apkUrl
                    pendingUpdateVersion = serverVersion
                    updateMessage = "发现新版本: $serverVersion\n当前版本: $currentVersion"
                    hasUpdate = true
                } else {
                    updateMessage = "已是最新版本 ($currentVersion)"
                    hasUpdate = false
                }
            } catch (e: Exception) {
                updateMessage = "检查更新失败: ${e.message}"
                hasUpdate = false
            }
            isCheckingUpdate = false
            showUpdateDialog = true
        }
    }

    fun startDownload() {
        if (pendingUpdateUrl.isEmpty()) return
        isDownloading = true
        downloadProgress = 0
        showUpdateDialog = false

        val baseUrl = "http://49.232.224.90:8080"
        val fullUrl = if (pendingUpdateUrl.startsWith("http")) pendingUpdateUrl else baseUrl + pendingUpdateUrl
        val version = pendingUpdateVersion

        scope.launch {
            try {
                val client = OkHttpClient.Builder().build()
                val request = Request.Builder().url(fullUrl).build()
                val cacheFile = File(context.cacheDir, "imcloud-v$version.apk")

                withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("下载失败: ${response.code}")
                        }

                        val body = response.body ?: throw Exception("空响应")
                        val totalBytes = body.contentLength()

                        body.byteStream().use { input ->
                            cacheFile.outputStream().use { output ->
                                val buffer = ByteArray(8192)
                                var downloaded = 0L
                                var read: Int
                                while (input.read(buffer).also { read = it } != -1) {
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (totalBytes > 0) {
                                        val progress = ((downloaded * 100) / totalBytes).toInt()
                                        withContext(Dispatchers.Main) {
                                            downloadProgress = progress
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ApkInstaller.installApk(context, cacheFile.absolutePath)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isDownloading = false
            }
        }
    }

    fun cancelDownload() {
        isDownloading = false
    }

    fun saveAiConfig() {
        isSavingAi = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ApiClient.saveAiConfig(aiProvider, aiApiBase, aiApiKey, aiModel)
            }
            isSavingAi = false
            Toast.makeText(
                context,
                if (ok) "保存成功" else "保存失败",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "设置",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1D1F),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        // ── AI Config Section ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI 配置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1D1F)
                    )
                    TextButton(onClick = { isAiConfigVisible = !isAiConfigVisible }) {
                        Text(
                            text = if (isAiConfigVisible) "收起" else "展开",
                            color = Color(0xFF007AFF)
                        )
                    }
                }

                if (isAiConfigVisible) {
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = aiProvider,
                        onValueChange = { aiProvider = it },
                        label = { Text("Provider") },
                        placeholder = { Text("e.g. OpenAI, Anthropic") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF007AFF),
                            unfocusedBorderColor = Color(0xFFE5E5EA)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = aiApiBase,
                        onValueChange = { aiApiBase = it },
                        label = { Text("API Base URL") },
                        placeholder = { Text("https://api.openai.com/v1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF007AFF),
                            unfocusedBorderColor = Color(0xFFE5E5EA)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = aiApiKey,
                        onValueChange = { aiApiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(
                                    text = if (showPassword) "隐藏" else "显示",
                                    color = Color(0xFF007AFF),
                                    fontSize = 12.sp
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF007AFF),
                            unfocusedBorderColor = Color(0xFFE5E5EA)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = aiModel,
                        onValueChange = { aiModel = it },
                        label = { Text("Model") },
                        placeholder = { Text("e.g. gpt-4o, claude-3-opus") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF007AFF),
                            unfocusedBorderColor = Color(0xFFE5E5EA)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { saveAiConfig() },
                        enabled = !isSavingAi,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                    ) {
                        if (isSavingAi) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("保存 AI 配置", fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── App Update Section ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "当前版本", fontSize = 16.sp, color = Color(0xFF1D1D1F))
                    Text(text = "v$currentVersion", fontSize = 16.sp, color = Color(0xFF8E8E93))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { checkUpdate() },
                    enabled = !isCheckingUpdate && !isDownloading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else if (isDownloading) {
                        Text("下载中 $downloadProgress%", fontSize = 16.sp, color = Color.White)
                    } else {
                        Text("检查更新", fontSize = 16.sp, color = Color.White)
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = downloadProgress / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF007AFF),
                        trackColor = Color(0xFFE5E5EA),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { cancelDownload() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("取消", color = Color(0xFFFF3B30))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Logout Section ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Button(
                    onClick = {
                        runCatching { ApiClient.logout() }
                        onNavigateToLogin()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                ) {
                    Text("退出登录", fontSize = 16.sp, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) showUpdateDialog = false
            },
            title = {
                Text(
                    text = if (hasUpdate) "发现新版本" else "当前版本",
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(text = updateMessage) },
            confirmButton = {
                if (pendingUpdateUrl.isNotEmpty()) {
                    TextButton(
                        onClick = { startDownload() },
                        enabled = !isDownloading
                    ) {
                        Text("更新")
                    }
                }
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("取消")
                }
            },
            dismissButton = {}
        )
    }
}
