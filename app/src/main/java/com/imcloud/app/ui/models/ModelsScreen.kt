package com.imcloud.app.ui.models

import android.content.Context
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.imcloud.app.data.remote.ApiClient
import com.imcloud.app.data.remote.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BLUE = Color(0xFF5B8DEF)
private val BG = Color(0xFFF2F2F7)
private val CARD = Color.White
private val TEXT = Color(0xFF1D1D1F)
private val GRAY = Color(0xFF8E8E93)
private val RED = Color(0xFFFF3B30)
private val GREEN = Color(0xFF34C759)

// ── 固定厂商列表 ──────────────────────────────────────────
data class KnownProvider(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val baseUrl: String,
    val models: String,
    val color: Color
)

private val KNOWN_PROVIDERS = listOf(
    KnownProvider("openai", "GPT", Icons.Default.SmartToy, "https://api.openai.com/v1", "gpt-4o,gpt-4o-mini,gpt-4,gpt-3.5-turbo", Color(0xFF10A37F)),
    KnownProvider("anthropic", "Claude", Icons.Default.Psychology, "https://api.anthropic.com/v1", "claude-sonnet-4-20250514,claude-3-5-sonnet-20241022,claude-3-5-haiku-20241022,claude-3-opus-20240229", Color(0xFFD97706)),
    KnownProvider("google", "Gemini", Icons.Default.Bolt, "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash,gemini-1.5-flash,gemini-1.5-pro,gemini-1.0-pro", Color(0xFF4285F4)),
    KnownProvider("doubao", "Doubao", Icons.Default.Whatshot, "https://ark.cn-beijing.volces.com/api/v3", "doubao-pro-32k,doubao-pro-128k,doubao-lite-32k", Color(0xFFFF6B35)),
    KnownProvider("baidu", "ERNIE", Icons.Default.LocalFireDepartment, "https://qianfan.baidubce.com/v2", "ernie-4.0-8k-latest,ernie-3.5-8k-latest,ernie-speed-128k,ernie-speed-8k,ernie-lite-8k", Color(0xFF2932E0)),
    KnownProvider("qwen", "Qwen", Icons.Default.AutoAwesome, "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max,qwen-plus,qwen-turbo,qwen2.5-72b-instruct,qwen2.5-7b-instruct", Color(0xFF1FABFF)),
    KnownProvider("hunyuan", "Hunyuan", Icons.Default.Cloud, "https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-pro,hunyuan-standard,hunyuan-lite", Color(0xFFFF4757)),
    KnownProvider("spark", "Spark", Icons.Default.Star, "https://spark-api.xf-yun.com/v3.5/chat", "generalv3.5,generalv3,generalv2,general", Color(0xFFFFCC00)),
    KnownProvider("zhipu", "GLM", Icons.Default.GridOn, "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus,glm-4,glm-4-flash,glm-4-air,glm-4-airx,glm-3-turbo", Color(0xFF00D9C0)),
    KnownProvider("moonshot", "Kimi", Icons.Default.NightsStay, "https://api.moonshot.cn/v1", "moonshot-v1-128k,moonshot-v1-32k,moonshot-v1-8k,kimi-math,kimi-coder", Color(0xFF7B68EE)),
    KnownProvider("deepseek", "DeepSeek", Icons.Default.FlashOn, "https://api.deepseek.com/v1", "deepseek-chat,deepseek-reasoner,deepseek-coder", Color(0xFF2C3E50)),
    KnownProvider("llama", "Llama", Icons.Default.Pets, "https://openrouter.ai/api/v1", "meta-llama/llama-3-70b-instruct,meta-llama/llama-3-8b-instruct,meta-llama/llama-2-70b-chat", Color(0xFFF97316)),
    KnownProvider("mistral", "Mistral", Icons.Default.WbCloudy, "https://api.mistral.ai/v1", "mistral-large-latest,mistral-medium-latest,mistral-small-latest,mixtral-8x7b-instruct", Color(0xFF00C7B7)),
    KnownProvider("internlm", "InternLM", Icons.Default.AccountTree, "https://api.siliconflow.cn/v1", "internlm2.5-20b,internlm2.5-7b,internlm2-20b,internlm2-7b", Color(0xFF6DB3F2)),
    KnownProvider("yi", "Yi", Icons.Default.Lightbulb, "https://api.siliconflow.cn/v1", "yi-large,yi-medium,yi-small,yi-large-rag,yi-spark", Color(0xFFFFB800)),
)

private fun findKnownProvider(name: String): KnownProvider? =
    KNOWN_PROVIDERS.find { it.name.equals(name, ignoreCase = true) || it.id == name.lowercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: (() -> Unit)? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = ctx.getSharedPreferences("imcloud_prefs", android.content.Context.MODE_PRIVATE)

    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedModel by remember { mutableStateOf(prefs.getString("selected_model", "deepseek-chat") ?: "deepseek-chat") }

    var showAddDialog by remember { mutableStateOf(false) }
    var showPickDialog by remember { mutableStateOf(false) }
    var knownProvider by remember { mutableStateOf<KnownProvider?>(null) }
    var editingProvider by remember { mutableStateOf<Provider?>(null) }
    var deletingProvider by remember { mutableStateOf<Provider?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var formName by remember { mutableStateOf("") }
    var formUrl by remember { mutableStateOf("") }
    var formKey by remember { mutableStateOf("") }
    var formModels by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun loadProviders() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                val json = withContext(Dispatchers.IO) { ApiClient.getProviders() }
                @Suppress("UNCHECKED_CAST")
                val list = Gson().fromJson(json, Array<Provider>::class.java).toList()
                providers = list
            } catch (e: Exception) {
                errorMsg = e.message ?: "加载失败"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadProviders() }

    // ── 添加/编辑 步骤一：选厂商 ──────────────────────────────
    if (showPickDialog) {
        AlertDialog(
            onDismissRequest = { showPickDialog = false },
            title = { Text("选择渠道", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 3列grid用Row模拟
                    KNOWN_PROVIDERS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { kp ->
                                Card(
                                    modifier = Modifier.weight(1f).clickable {
                                        formName = kp.name
                                        formUrl = kp.baseUrl
                                        formModels = kp.models
                                        knownProvider = kp
                                        showPickDialog = false
                                        showAddDialog = true
                                    },
                                    colors = CardDefaults.cardColors(containerColor = BG),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(kp.icon, null, tint = kp.color, modifier = Modifier.size(28.dp))
                                        Spacer(Modifier.height(6.dp))
                                        Text(kp.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TEXT, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            // 不足3个时占位
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 添加/编辑 步骤二：填 Key ─────────────────────────────
    if (showAddDialog || editingProvider != null) {
        val provider = editingProvider
        // 编辑时尝试匹配已知厂商
        LaunchedEffect(provider) {
            if (provider != null) {
                val matched = findKnownProvider(provider.name)
                knownProvider = matched
                formName = provider.name
                formUrl = provider.baseUrl ?: ""
                formKey = provider.apiKey ?: ""
                formModels = provider.models?.joinToString(", ") ?: ""
            }
        }
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false; editingProvider = null; knownProvider = null
                formName = ""; formUrl = ""; formKey = ""; formModels = ""; addError = null
            },
            title = { Text(if (knownProvider != null) "配置 ${knownProvider!!.name}" else if (provider != null) "编辑渠道" else "添加渠道") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (knownProvider != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(BG, RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Icon(knownProvider!!.icon, null, tint = knownProvider!!.color, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(knownProvider!!.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TEXT)
                                Text(knownProvider!!.baseUrl, fontSize = 11.sp, color = GRAY)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = formName, onValueChange = { formName = it },
                            label = { Text("渠道名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = formUrl, onValueChange = { formUrl = it },
                            label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = formKey, onValueChange = { formKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = formModels, onValueChange = { formModels = it },
                        label = { Text("模型列表（逗号分隔）") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (addError != null) {
                        Text(addError!!, color = RED, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (formKey.isBlank()) {
                            addError = "请填写 API Key"
                            return@TextButton
                        }
                        isSaving = true
                        scope.launch {
                            try {
                                val json = withContext(Dispatchers.IO) {
                                    if (provider != null && provider.id != null) {
                                        ApiClient.updateProvider(provider.id, com.google.gson.Gson().toJson(mapOf("name" to formName.trim(), "base_url" to formUrl.trim(), "api_key" to formKey.trim(), "models" to formModels.trim())))
                                    } else {
                                        ApiClient.addProvider(com.google.gson.Gson().toJson(mapOf("name" to formName.trim(), "base_url" to formUrl.trim(), "api_key" to formKey.trim(), "models" to formModels.trim())))
                                    }
                                }
                                val obj = org.json.JSONObject(json)
                                if (obj.optBoolean("ok", false)) {
                                    showAddDialog = false; editingProvider = null; knownProvider = null
                                    formName = ""; formUrl = ""; formKey = ""; formModels = ""; addError = null
                                    loadProviders()
                                } else {
                                    addError = obj.optString("error", "操作失败")
                                }
                            } catch (e: Exception) {
                                addError = e.message ?: "网络错误"
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving
                ) {
                    if (isSaving) { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false; editingProvider = null; knownProvider = null
                    formName = ""; formUrl = ""; formKey = ""; formModels = ""; addError = null
                }) { Text("取消") }
            }
        )
    }

    // ── 删除确认弹窗 ──────────────────────────────────────────
    if (deletingProvider != null) {
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text("删除渠道") },
            text = { Text("确定删除「${deletingProvider!!.name}」？该操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                deletingProvider!!.id?.let { ApiClient.deleteProvider(it) }
                            }
                        } catch (_: Exception) {}
                        deletingProvider = null
                        loadProviders()
                    }
                }) { Text("删除", color = RED) }
            },
            dismissButton = { TextButton(onClick = { deletingProvider = null }) { Text("取消") } }
        )
    }

    // ── 主界面 ────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        formName = ""; formUrl = ""; formKey = ""; formModels = ""
                        knownProvider = null; editingProvider = null; addError = null
                        showPickDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加", tint = BLUE)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CARD, titleContentColor = TEXT
                )
            )
        },
        containerColor = BG
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BLUE)
                    }
                }
                errorMsg != null -> {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(errorMsg!!, color = RED, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { loadProviders() }, shape = RoundedCornerShape(10.dp)) {
                            Text("重试")
                        }
                    }
                }
                providers.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CloudQueue, null, Modifier.size(64.dp), tint = GRAY)
                        Spacer(Modifier.height(16.dp))
                        Text("暂无渠道", color = GRAY, fontSize = 16.sp)
                        Text("点击右上角 + 添加", color = GRAY.copy(alpha = 0.7f), fontSize = 14.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                formName = ""; formUrl = ""; formKey = ""; formModels = ""
                                knownProvider = null; editingProvider = null; addError = null
                                showPickDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BLUE)
                        ) { Text("添加渠道") }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 当前模型
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CARD),
                                shape = RoundedCornerShape(14.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, null, tint = BLUE, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("当前模型", fontSize = 13.sp, color = GRAY)
                                        Text(selectedModel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BLUE)
                                    }
                                }
                            }
                        }

                        // 渠道列表
                        items(providers, key = { it.id ?: it.name }) { provider ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CARD),
                                shape = RoundedCornerShape(14.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(provider.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TEXT)
                                            Text(provider.baseUrl ?: "", fontSize = 12.sp, color = GRAY, maxLines = 1)
                                        }
                                        IconButton(onClick = {
                                            val matched = findKnownProvider(provider.name)
                                            knownProvider = matched
                                            formName = provider.name
                                            formUrl = provider.baseUrl ?: ""
                                            formKey = provider.apiKey ?: ""
                                            formModels = provider.models?.joinToString(", ") ?: ""
                                            editingProvider = provider; addError = null
                                        }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Edit, "编辑", tint = GRAY, modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { deletingProvider = provider }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Delete, "删除", tint = RED.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    val models = provider.models ?: emptyList()
                                    if (models.isNotEmpty()) {
                                        Spacer(Modifier.height(10.dp))
                                        Divider(color = BG)
                                        Spacer(Modifier.height(10.dp))
                                        models.forEach { model ->
                                            val isSelected = model == selectedModel
                                            val thisProvider = provider
                                            Row(
                                                Modifier.fillMaxWidth().clickable {
                                                    selectedModel = model
                                                    prefs.edit()
                                                        .putString("selected_model", model)
                                                        .putString("selected_provider", thisProvider.id)
                                                        .apply()
                                                }.padding(vertical = 8.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                                    null, tint = if (isSelected) BLUE else GRAY, modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(model, fontSize = 14.sp, color = if (isSelected) BLUE else TEXT,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(GREEN.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Text("使用中", fontSize = 11.sp, color = GREEN)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.height(8.dp))
                                        Text("暂无可用模型", fontSize = 13.sp, color = GRAY.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}
