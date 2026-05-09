package com.imcloud.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.JsonObject
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

    val currentVersion = BuildConfig.VERSION_NAME

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

                // Download complete — install
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1D1F),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

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

                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = Color(0xFFE5E5EA))
                Spacer(modifier = Modifier.height(20.dp))

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
