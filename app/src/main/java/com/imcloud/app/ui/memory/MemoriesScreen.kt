package com.imcloud.app.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imcloud.app.data.remote.ApiClient
import com.imcloud.app.data.remote.MemoryItem as RemoteMemoryItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ACCENT = Color(0xFF5B8DEF)
private val PAGE_BG = Color(0xFFE8EBF5)
private val CARD_BG = Color.White
private val TEXT_SECONDARY = Color(0xFF8B8D9B)

data class MemoryItem(val id: String, val content: String, val date: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(onNavigateBack: () -> Unit = {}) {
    var memories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newContent by remember { mutableStateOf("") }
    var deletingId by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun loadMemories() {
        isLoading = true
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) { ApiClient.getMemories() }
                memories = items.map { MemoryItem(id = it.date, content = it.title, date = it.date) }
            } catch (e: Exception) { e.printStackTrace() }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadMemories() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CARD_BG)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ACCENT
            ) { Icon(Icons.Default.Add, "添加记忆", tint = Color.White) }
        },
        containerColor = PAGE_BG
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = ACCENT)
                memories.isEmpty() -> Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(64.dp), tint = TEXT_SECONDARY)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("暂无记忆", color = TEXT_SECONDARY)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(memories, key = { it.id }) { mem ->
                        MemoryCard(
                            memory = mem,
                            onDelete = {
                                deletingId = mem.id
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) { ApiClient.deleteMemory(mem.id) }
                                    if (ok) memories = memories.filter { it.id != mem.id }
                                    deletingId = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加记忆") },
            text = {
                OutlinedTextField(
                    value = newContent,
                    onValueChange = { newContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("记忆内容...") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newContent.isNotBlank()) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { ApiClient.addMemory(newContent) }
                            if (ok) { newContent = ""; loadMemories() }
                        }
                        showAddDialog = false
                    }
                }) { Text("保存", color = ACCENT) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MemoryCard(memory: MemoryItem, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = CARD_BG,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(memory.content, fontSize = 14.sp, color = Color(0xFF1A1A2E))
                Spacer(modifier = Modifier.height(6.dp))
                Text(memory.date, fontSize = 11.sp, color = TEXT_SECONDARY)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "删除", tint = Color(0xFFE5544E), modifier = Modifier.size(18.dp))
            }
        }
    }
}
