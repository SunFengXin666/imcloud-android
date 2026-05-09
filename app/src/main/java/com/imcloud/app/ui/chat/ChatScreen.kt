package com.imcloud.app.ui.chat

import android.text.TextUtils
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.imcloud.app.data.remote.ApiClient
import com.imcloud.app.data.remote.ApiMessage
import com.imcloud.app.data.remote.ChatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

// ── Color palette (mirrors MainScreen) ──────────────────────────

private val PAGE_BG           = Color(0xFFE8EBF5)
private val CARD_BG           = Color.White
private val ACCENT            = Color(0xFF5B8DEF)
private val ACCENT_LIGHT      = Color(0xFFEEF2FF)
private val TEXT_PRIMARY      = Color(0xFF1A1A2E)
private val TEXT_SECONDARY    = Color(0xFF8B8D9B)
private val USER_BUBBLE       = ACCENT
private val ASSISTANT_BUBBLE  = Color(0xFFF0F2F8)
private val INPUT_BG          = Color(0xFFF5F5F7)
private val DIVIDER           = Color(0xFFE8E8F0)
private val DANGER            = Color(0xFFE5544E)
private val SUCCESS           = Color(0xFF34C759)

// ── Message types ──────────────────────────────────────────────

sealed class ChatMessage {
    abstract val id: String
    abstract val role: String
    abstract val content: String
    abstract val timestamp: Long

    data class User(
        override val id: String = UUID.randomUUID().toString(),
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage() {
        override val role: String = "user"
    }

    data class Assistant(
        override val id: String = UUID.randomUUID().toString(),
        override val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val isStreaming: Boolean = false
    ) : ChatMessage() {
        override val role: String = "assistant"
    }
}

// ── ViewModel ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentModel by remember { mutableStateOf("") }
    var showModelMenu by remember { mutableStateOf(false) }
    var showHistoryMenu by remember { mutableStateOf(false) }
    var chatHistory by remember { mutableStateOf<List<ChatEntry>>(emptyList()) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var streamingContent by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showError by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Load chat history on first composition
    LaunchedEffect(Unit) {
        loadChatHistory().let { chatHistory = it }
    }

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Load saved model preference
    val prefs = context.getSharedPreferences("imcloud_prefs", android.content.Context.MODE_PRIVATE)
    LaunchedEffect(Unit) {
        currentModel = prefs?.getString("selected_model", "gpt-3.5-turbo") ?: "gpt-3.5-turbo"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ──────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CARD_BG,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "新对话",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TEXT_PRIMARY,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Model selector
                    Box {
                        TextButton(
                            onClick = { showModelMenu = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentModel.ifEmpty { "选择模型" },
                                fontSize = 12.sp,
                                color = ACCENT
                            )
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = null,
                                tint = ACCENT,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            val models = listOf(
                                "gpt-3.5-turbo", "gpt-4", "gpt-4-turbo",
                                "claude-3-opus", "claude-3-sonnet", "claude-3-haiku",
                                "deepseek-chat", "Qwen", "glm-4"
                            )
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model, fontSize = 14.sp) },
                                    onClick = {
                                        currentModel = model
                                        showModelMenu = false
                                        // Save preference
                                        context.getSharedPreferences("imcloud_prefs", android.content.Context.MODE_PRIVATE)
                                            .edit()?.putString("selected_model", model)?.apply()
                                    },
                                    leadingIcon = {
                                        if (model == currentModel) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(ACCENT)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // History button
                    IconButton(
                        onClick = { showHistorySheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "历史记录",
                            tint = TEXT_SECONDARY,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Divider(color = DIVIDER, thickness = 0.5.dp)

            // ── Error banner ─────────────────────────────────────────
            AnimatedVisibility(visible = showError && errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    color = DANGER.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            fontSize = 13.sp,
                            color = DANGER,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showError = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "关闭",
                                tint = DANGER,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ── Message list ─────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyChatHint()
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    MessageBubble(message = msg)
                }

                // Streaming indicator
                if (streamingContent.isNotEmpty()) {
                    item {
                        MessageBubble(
                            message = ChatMessage.Assistant(
                                id = "streaming",
                                content = streamingContent + "▌",
                                isStreaming = true
                            )
                        )
                    }
                }

                // Loading indicator
                if (isLoading && streamingContent.isEmpty()) {
                    item {
                        LoadingIndicator()
                    }
                }
            }

            Divider(color = DIVIDER, thickness = 0.5.dp)

            // ── Input bar ─────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CARD_BG
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp),
                            placeholder = {
                                Text(
                                    "输入消息...",
                                    fontSize = 15.sp,
                                    color = TEXT_SECONDARY
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = INPUT_BG,
                                unfocusedContainerColor = INPUT_BG,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = TEXT_PRIMARY,
                                unfocusedTextColor = TEXT_PRIMARY
                            ),
                            shape = RoundedCornerShape(22.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank() && !isLoading) {
                                        val text = inputText.trim()
                                        inputText = ""
                                        sendMessage(text, currentModel) { newMsg ->
                                            messages = messages + newMsg
                                        }
                                    }
                                }
                            ),
                            singleLine = false,
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send button
                        val sendEnabled = inputText.isNotBlank() && !isLoading
                        val bgColor by animateColorAsState(
                            targetValue = if (sendEnabled) ACCENT else ACCENT.copy(alpha = 0.4f),
                            label = "sendBg"
                        )
                        val rotation by animateFloatAsState(
                            targetValue = if (isLoading) 360f else 0f,
                            label = "sendRotation"
                        )

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .then(
                                    if (sendEnabled) {
                                        Modifier.padding(0.dp)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank() && !isLoading) {
                                        val text = inputText.trim()
                                        inputText = ""
                                        sendMessage(text, currentModel) { newMsg ->
                                            messages = messages + newMsg
                                        }
                                    }
                                },
                                enabled = sendEnabled,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(rotation)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── History sheet ─────────────────────────────────────────────
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = CARD_BG
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "清空对话",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TEXT_PRIMARY
                    )
                    TextButton(
                        onClick = {
                            messages = emptyList()
                            showHistorySheet = false
                        }
                    ) {
                        Text("清空", color = DANGER, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (chatHistory.isEmpty()) {
                    Text(
                        "暂无历史记录",
                        fontSize = 14.sp,
                        color = TEXT_SECONDARY,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    chatHistory.forEach { entry ->
                        HistoryItem(entry = entry, onLoad = { loadedMessages ->
                            messages = loadedMessages
                            showHistorySheet = false
                        }, onDelete = {
                            chatHistory = chatHistory.filter { it.id != entry.id }
                            scope.launch { saveChatHistory(chatHistory) }
                        })
                        Divider(color = DIVIDER, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ── Message bubble ─────────────────────────────────────────────

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message is ChatMessage.User
    val bubbleColor by animateColorAsState(
        targetValue = if (isUser) USER_BUBBLE else ASSISTANT_BUBBLE,
        label = "bubbleColor"
    )
    val textColor = if (isUser) Color.White else TEXT_PRIMARY
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleShape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                fontSize = 15.sp,
                color = textColor,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = formatTime(message.timestamp),
            fontSize = 10.sp,
            color = TEXT_SECONDARY
        )
    }
}

@Composable
private fun EmptyChatHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(ACCENT, Color(0xFF8BA5F5))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "清",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "清云智能助手",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TEXT_PRIMARY
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "发送消息开始对话",
            fontSize = 13.sp,
            color = TEXT_SECONDARY
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = ASSISTANT_BUBBLE
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { i ->
                    val alpha by animateFloatAsState(
                        targetValue = if (true) 0.3f + (i * 0.3f) else 0.3f,
                        label = "dot$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ACCENT.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: ChatEntry,
    onLoad: (List<ChatMessage>) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title ?: "未命名对话",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TEXT_PRIMARY,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.time != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.time,
                    fontSize = 12.sp,
                    color = TEXT_SECONDARY
                )
            }
        }

        TextButton(
            onClick = {
                val loadedMessages = entry.messages?.mapIndexed { index, apiMsg ->
                    if (apiMsg.role == "user") {
                        ChatMessage.User(
                            id = "$index-user",
                            content = apiMsg.content.toString()
                        )
                    } else {
                        ChatMessage.Assistant(
                            id = "$index-assistant",
                            content = apiMsg.content.toString()
                        )
                    }
                } ?: emptyList()
                onLoad(loadedMessages)
            },
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text("加载", fontSize = 13.sp, color = ACCENT)
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = DANGER,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Chat logic ─────────────────────────────────────────────────

private fun sendMessage(
    text: String,
    model: String,
    onMessage: (ChatMessage) -> Unit
) {
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    val userMsg = ChatMessage.User(content = text)
    mainHandler.post { onMessage(userMsg) }

    thread {
        var response: Response? = null
        try {
            val messages = listOf(
                ApiMessage(role = "user", content = text)
            )
            val resp = ApiClient.chat(model, messages)
            response = resp
            val body = resp.body
            if (body == null) {
                mainHandler.post { onMessage(ChatMessage.Assistant(content = "错误：无响应")) }
                return@thread
            }

            val buffer = java.io.BufferedReader(java.io.InputStreamReader(body.byteStream(), Charsets.UTF_8))
            val gson = Gson()
            var accumulated = ""

            try {
                var line: String? = buffer.readLine()
                while (line != null) {
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data.isEmpty() || data == "[DONE]") {
                            line = buffer.readLine()
                            continue
                        }

                        try {
                            val chunk = gson.fromJson(data, Map::class.java)
                            val delta = (chunk["choices"] as? List<*>)?.firstOrNull()
                                ?.let { it as? Map<*, *> }?.get("delta") as? Map<*, *>
                            val content = delta?.get("content") as? String
                            if (!content.isNullOrEmpty()) {
                                accumulated += content
                            }
                        } catch (_: Exception) { }
                    }
                    line = buffer.readLine()
                }
            } catch (_: Exception) { }

            buffer.close()

            mainHandler.post { onMessage(ChatMessage.Assistant(content = accumulated.ifEmpty { "（无内容）" })) }
        } catch (e: Exception) {
            Log.e("ChatScreen", "Stream error", e)
            mainHandler.post { onMessage(ChatMessage.Assistant(content = "错误：${e.message ?: "未知错误"}")) }
        } finally {
            response?.close()
        }
    }
}

// ── Persistence ─────────────────────────────────────────────────

private suspend fun loadChatHistory(): List<ChatEntry> = withContext(Dispatchers.IO) {
    try {
        val raw = ApiClient.getChats()
        val arr = Gson().fromJson(raw, Array<ChatEntry>::class.java)
        arr.toList()
    } catch (_: Exception) {
        emptyList()
    }
}

private suspend fun saveChatHistory(history: List<ChatEntry>) = withContext(Dispatchers.IO) {
    try {
        val json = Gson().toJson(history)
        ApiClient.saveChats(json)
    } catch (_: Exception) { }
}

// ── Helpers ────────────────────────────────────────────────────

private fun formatTime(ts: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(ts))
    } catch (_: Exception) {
        ""
    }
}
