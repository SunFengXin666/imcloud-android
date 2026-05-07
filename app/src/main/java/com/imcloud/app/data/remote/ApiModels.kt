package com.imcloud.app.data.remote

import com.google.gson.annotations.SerializedName

// ── Auth ──────────────────────────────────────────────────────

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val ok: Boolean? = null,
    val token: String? = null,
    val username: String? = null,
    val error: String? = null,
    val message: String? = null
)

// ── Chat ──────────────────────────────────────────────────────

data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true
)

data class ApiMessage(
    val role: String,
    val content: Any   // can be String or List<Map<String, Any>>
)

data class ChatEntry(
    val id: String,
    val title: String? = null,
    val messages: List<ApiMessage>? = null,
    val time: String? = null
)

// ── Provider ──────────────────────────────────────────────────

data class Provider(
    val id: String? = null,
    val name: String,
    @SerializedName("base_url") val baseUrl: String,
    @SerializedName("api_key") val apiKey: String,
    val models: List<String>? = null
)

data class ProviderResponse(
    val ok: Boolean? = null,
    val provider: Provider? = null,
    val error: String? = null
)

// ── Models ────────────────────────────────────────────────────

data class ModelsResponse(
    val data: List<ModelItem>? = null
)

data class ModelItem(
    val id: String,
    @SerializedName("owned_by") val ownedBy: String? = null
)

// ── Memory ────────────────────────────────────────────────────

data class MemoryItem(
    val date: String,
    val title: String,
    val size: Long
)

data class MemoryDetail(
    val date: String,
    val content: String
)

// ── Token Usage ───────────────────────────────────────────────

data class TokenUsage(
    val date: String? = null,
    val used: Long? = null,
    val limit: Long? = null
)

// ── Disk / SFTP ─────────────────────────────────────────────

data class DiskConnectRequest(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val root_path: String
)

data class DiskConnectResponse(
    val ok: Boolean?,
    val conn_id: String?,
    val host: String?,
    val root_path: String?,
    val error: String?
)

data class DiskListResponse(
    val ok: Boolean?,
    val entries: List<FileEntry>?,
    val path: String?,
    val error: String?
)

data class FileEntry(
    val name: String,
    val size: Long,
    val mtime: Long,
    val is_dir: Boolean
)

data class DiskResponse(
    val ok: Boolean?,
    val error: String?
)

data class DiskDownloadResponse(
    val ok: Boolean?,
    val url: String?,
    val filename: String?
)

data class DiskPathRequest(
    val path: String
)

data class DiskConfigData(
    val host: String?,
    val port: Int?,
    val username: String?,
    val password: String?,
    val root_path: String?
)
