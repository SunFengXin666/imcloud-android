package com.imcloud.app.ui.disk

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.gson.Gson
import com.imcloud.app.data.remote.ApiClient
import com.imcloud.app.data.remote.DiskConnectResponse
import com.imcloud.app.data.remote.DiskListResponse
import com.imcloud.app.data.remote.FileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DiskScreen(
    onNavigateToLogin: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── State ──────────────────────────────────────────────
    var host     by remember { mutableStateOf("81.70.229.222") }
    var port     by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("ubuntu") }
    var password by remember { mutableStateOf("SunFengXin521?") }
    var showConnectDialog by remember { mutableStateOf(false) }

    var connId   by remember { mutableStateOf<String?>(null) }
    var rootPath by remember { mutableStateOf("/") }
    var curPath  by remember { mutableStateOf("/") }
    var files    by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }
    var toast by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // ── Toast ─────────────────────────────────────────────
    if (toast != null) {
        LaunchedEffect(toast) {
            delay(2000)
            toast = null
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier.padding(bottom = 100.dp, start = 32.dp, end = 32.dp),
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(
                    containerColor = if (toast!!.second) Color(0xFF34C759) else Color(0xFFFF3B30)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (toast!!.second) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(toast!!.first, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }

    // ── Connect dialog ────────────────────────────────────
    if (showConnectDialog) {
        Dialog(onDismissRequest = { }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("连接服务器", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("主机") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("端口") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                connId = null
                                files = emptyList()
                                curPath = "/"
                                showConnectDialog = false
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    isLoading = true
                                    errorMsg = null
                                    try {
                                        val body = Gson().toJson(mapOf(
                                            "host" to host,
                                            "port" to (port.toIntOrNull() ?: 22),
                                            "username" to username,
                                            "password" to password
                                        ))
                                        val json = ApiClient.diskConnect(body)
                                        val resp = Gson().fromJson(json, DiskConnectResponse::class.java)
                                        withContext(Dispatchers.Main) {
                                            if (resp.ok == true && resp.connId != null) {
                                                val cid = resp.connId
                                                val root = resp.rootPath ?: "/"
                                                connId = cid
                                                rootPath = root
                                                curPath = root
                                                showConnectDialog = false
                                                loadDir(cid, root) { files = it }
                                            } else {
                                                errorMsg = resp.error ?: "连接失败"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { errorMsg = e.message ?: "网络错误" }
                                    } finally {
                                        withContext(Dispatchers.Main) { isLoading = false }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("连接") }
                    }
                }
            }
        }
    }

    // ── Delete dialog ──────────────────────────────────────
    if (deleteTarget != null) {
        Dialog(onDismissRequest = { deleteTarget = null }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(72.dp).background(Color(0xFFFFEBEB), shape = RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("确认删除", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("确定要删除「${deleteTarget!!.name}」吗？", fontSize = 15.sp, color = Color(0xFF636366))
                    Text("此操作不可恢复", fontSize = 13.sp, color = Color(0xFFBDBDBD))
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { deleteTarget = null },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                val target = deleteTarget!!
                                deleteTarget = null
                                scope.launch(Dispatchers.IO) {
                                    val cid = connId ?: return@launch
                                    val fullPath = if (curPath == "/") "/${target.name}" else "$curPath/${target.name}"
                                    try {
                                        val json = ApiClient.diskDelete(cid, fullPath)
                                        val resp = Gson().fromJson(json, com.imcloud.app.data.remote.DiskResponse::class.java)
                                        withContext(Dispatchers.Main) {
                                            if (resp.ok == true) {
                                                toast = "删除成功" to true
                                                loadDir(cid, curPath) { files = it }
                                            } else {
                                                toast = "删除失败" to false
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { toast = "删除失败: ${e.message}" to false }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("删除") }
                    }
                }
            }
        }
    }

    // ── File picker ────────────────────────────────────────
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null || connId == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isUploading = true }
            try {
                val inputStream = ctx.contentResolver.openInputStream(uri) ?: throw Exception("无法读取文件")
                val fileName = uri.lastPathSegment ?: "file"
                val ok = ApiClient.diskUpload(connId!!, curPath, inputStream, fileName)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        toast = "上传成功" to true
                        loadDir(connId!!, curPath) { files = it }
                    } else {
                        toast = "上传失败" to false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast = "上传失败: ${e.message}" to false }
            } finally {
                withContext(Dispatchers.Main) { isUploading = false }
            }
        }
    }

    // ── Main UI ───────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val canBack = curPath != rootPath
                        if (canBack) {
                            IconButton(
                                onClick = {
                                    val parent = curPath.substringBeforeLast('/', curPath).ifEmpty { rootPath }
                                    curPath = parent
                                    connId?.let { loadDir(it, parent) { files = it } }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF5B8DEF), modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(if (canBack) 0.dp else 8.dp))
                        Column {
                            Text("云盘", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(curPath, fontSize = 11.sp, color = Color(0xFF5B8DEF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                actions = {
                    // IconButton(onClick = { filePickerLauncher.launch("*/*") }, enabled = connId != null && !isUploading) {
                    if (connId != null) {
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                connId?.let { ApiClient.diskDisconnect(it) }
                                withContext(Dispatchers.Main) {
                                    connId = null
                                    files = emptyList()
                                    curPath = "/"
                                    toast = "已断开" to false
                                }
                            }
                        }) {
                            Icon(Icons.Default.Close, "断开", tint = Color(0xFFFF3B30), modifier = Modifier.size(22.dp))
                        }
                    }
                    IconButton(onClick = { showConnectDialog = true }) {
                        Icon(Icons.Default.Settings, "连接设置", tint = Color(0xFF5B8DEF), modifier = Modifier.size(22.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFE8EBF5)
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 2.dp
            ) {
                when {
                    connId == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Cloud, null, tint = Color(0xFFBDBDBD), modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("未连接服务器", fontSize = 15.sp, color = Color(0xFF8E8E93))
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { showConnectDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("连接服务器") }
                            }
                        }
                    }
                    isLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF5B8DEF))
                                Spacer(Modifier.height(12.dp))
                                Text("加载中...", fontSize = 15.sp, color = Color(0xFF8E8E93))
                            }
                        }
                    }
                    errorMsg != null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Report, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(errorMsg!!, fontSize = 15.sp, color = Color(0xFFFF3B30))
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { showConnectDialog = true }, shape = RoundedCornerShape(12.dp)) { Text("重新连接") }
                            }
                        }
                    }
                    files.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Folder, null, tint = Color(0xFFBDBDBD), modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("空文件夹", fontSize = 15.sp, color = Color(0xFF8E8E93))
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("上传文件")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(files, key = { it.name }) { file ->
                                FileItem(
                                    file = file,
                                    onClick = {
                                        if (file.is_dir) {
                                            curPath = if (curPath == "/") "/${file.name}" else "$curPath/${file.name}"
                                            connId?.let { loadDir(it, curPath) { files = it } }
                                        }
                                    },
                                    onDelete = { deleteTarget = file },
                                    onDownload = {
                                        val cid = connId ?: return@FileItem
                                        val fullPath = if (curPath == "/") "/${file.name}" else "$curPath/${file.name}"
                                        val encoded = java.net.URLEncoder.encode(fullPath, "UTF-8")
                                        val url = "http://49.232.224.90:8080/api/disk/$cid/download/stream?path=$encoded"
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                                            ctx.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                Divider(color = Color(0xFFF2F2F7), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    file: FileEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit
) {
    val iconType = when {
        file.is_dir -> Icons.Default.Folder
        file.name.endsWith(".jpg") || file.name.endsWith(".jpeg")
            || file.name.endsWith(".png") || file.name.endsWith(".gif")
            || file.name.endsWith(".webp") -> Icons.Default.Photo
        file.name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
        file.name.endsWith(".doc") || file.name.endsWith(".docx") -> Icons.Default.Article
        file.name.endsWith(".mp3") || file.name.endsWith(".wav")
            || file.name.endsWith(".aac") || file.name.endsWith(".flac") -> Icons.Default.Audiotrack
        file.name.endsWith(".mp4") || file.name.endsWith(".avi")
            || file.name.endsWith(".mkv") || file.name.endsWith(".mov") -> Icons.Default.Movie
        file.name.endsWith(".zip") || file.name.endsWith(".tar")
            || file.name.endsWith(".gz") || file.name.endsWith(".rar") -> Icons.Default.FolderZip
        file.name.endsWith(".apk") -> Icons.Default.PhoneAndroid
        else -> Icons.Default.FileCopy
    }
    val iconColor = if (file.is_dir) Color(0xFFFFB300) else Color(0xFF5B8DEF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(iconType, null, tint = iconColor, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, fontSize = 15.sp, color = Color(0xFF1D1D1F), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!file.isDir) {
                Spacer(Modifier.height(2.dp))
                Text("${formatSize(file.size)} · ${formatDate(file.mtime)}", fontSize = 12.sp, color = Color(0xFFBDBDBD))
            }
        }
        if (!file.is_dir) {
            Icon(Icons.Default.Download, "下载", tint = Color(0xFF5B8DEF), modifier = Modifier.size(18.dp).clickable(onClick = onDownload))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.Delete, "删除", tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp).clickable(onClick = onDelete))
        }
    }
}

private fun loadDir(connId: String, path: String, onResult: (List<FileEntry>) -> Unit) {
    try {
        val json = ApiClient.diskList(connId, path)
        val resp = Gson().fromJson(json, DiskListResponse::class.java)
        onResult(resp.entries ?: emptyList())
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(emptyList())
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

private fun formatDate(ts: Long): String {
    if (ts <= 0) return ""
    return try {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts * 1000))
    } catch (_: Exception) { "" }
}
