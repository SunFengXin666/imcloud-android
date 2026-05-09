package com.imcloud.app.data.remote

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

private const val TAG = "ApiClient"

// Singleton API client that avoids Retrofit entirely (fewer crash vectors)
object ApiClient {

    private var appContext: android.content.Context? = null
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private const val BASE_URL = "http://49.232.224.90:8080"

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        Log.d(TAG, "ApiClient initialized, BASE_URL=$BASE_URL")
    }

    private fun getToken(): String? {
        return try {
            val prefs = appContext?.getSharedPreferences("imcloud_prefs", android.content.Context.MODE_PRIVATE)
            prefs?.getString("auth_token", null)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToken(token: String) {
        try {
            appContext?.getSharedPreferences("imcloud_prefs", android.content.Context.MODE_PRIVATE)
                ?.edit()?.putString("auth_token", token)?.apply()
        } catch (e: Exception) { /* ignore */ }
    }

    private fun saveUsername(username: String) {
        try {
            appContext?.getSharedPreferences("imcloud_prefs", android.content.Context.MODE_PRIVATE)
                ?.edit()?.putString("username", username)?.apply()
        } catch (e: Exception) { /* ignore */ }
    }

    fun clearToken() {
        try {
            appContext?.getSharedPreferences("imcloud_prefs", android.content.Context.MODE_PRIVATE)
                ?.edit()?.remove("auth_token")?.remove("username")?.apply()
        } catch (e: Exception) { /* ignore */ }
    }

    private fun buildRequest(path: String, body: String? = null, method: String = "GET"): Request {
        val url = "$BASE_URL$path"
        val builder = Request.Builder().url(url)

        // Add auth token for non-auth endpoints
        if (path != "/api/auth/login" && path != "/api/auth/register") {
            getToken()?.let { builder.addHeader("Authorization", "Bearer $it") }
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(mediaType))
            "PUT" -> builder.put((body ?: "{}").toRequestBody(mediaType))
            "DELETE" -> builder.delete(body?.toRequestBody(mediaType) ?: "".toRequestBody(null))
            else -> builder.get()
        }
        return builder.build()
    }

    // ── Raw HTTP helpers ──

    private fun executeSync(request: Request): String {
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: $body")
        }
        return body
    }

    // ── Generic JSON POST ──
    fun postJson(path: String, jsonBody: String): String {
        val request = buildRequest(path, jsonBody, "POST")
        return executeSync(request)
    }

    // ── Auth API ──

    fun login(username: String, password: String): AuthResponse {
        val body = com.google.gson.Gson().toJson(LoginRequest(username, password))
        val request = buildRequest("/api/auth/login", body, "POST")
        return try {
            val raw = executeSync(request)
            val resp = com.google.gson.Gson().fromJson(raw, AuthResponse::class.java)
            if (resp.ok == true && resp.token != null) {
                saveToken(resp.token)
                saveUsername(resp.username ?: username)
            }
            resp
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            AuthResponse(ok = false, error = e.message ?: "网络错误")
        }
    }

    fun register(username: String, password: String): AuthResponse {
        val body = com.google.gson.Gson().toJson(RegisterRequest(username, password))
        val request = buildRequest("/api/auth/register", body, "POST")
        return try {
            val raw = executeSync(request)
            com.google.gson.Gson().fromJson(raw, AuthResponse::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Register failed", e)
            AuthResponse(ok = false, error = e.message ?: "网络错误")
        }
    }

    fun checkAuth(): Boolean {
        return try {
            val request = buildRequest("/api/auth/check")
            val raw = executeSync(request)
            val resp = com.google.gson.Gson().fromJson(raw, AuthResponse::class.java)
            resp.ok == true
        } catch (e: Exception) {
            clearToken()
            false
        }
    }

    fun logout() {
        try {
            val request = buildRequest("/api/auth/logout", "{}", "POST")
            executeSync(request)
        } catch (_: Exception) {}
        clearToken()
    }

    fun getTokenUsage(): String {
        val request = buildRequest("/api/token/usage")
        return executeSync(request)
    }

    // ── Chat API (streaming) ──

    fun chat(model: String, messages: List<ApiMessage>): Response {
        val gson = com.google.gson.Gson()
        val body = gson.toJson(mapOf("model" to model, "messages" to messages, "stream" to true))
        val request = buildRequest("/api/chat", body, "POST")
        return client.newCall(request).execute()
    }

    // ── Chat History ──

    fun getChats(): String {
        val request = buildRequest("/api/chats")
        return executeSync(request)
    }

    fun saveChats(chats: String) {
        val request = buildRequest("/api/chats", chats, "PUT")
        executeSync(request)
    }

    // ── Providers ──

    fun getProviders(): String {
        val request = buildRequest("/api/providers")
        return executeSync(request)
    }

    fun addProvider(body: String): String {
        val request = buildRequest("/api/providers", body, "POST")
        return executeSync(request)
    }

    fun updateProvider(id: String, body: String): String {
        val request = buildRequest("/api/providers/$id", body, "PUT")
        return executeSync(request)
    }

    fun deleteProvider(id: String): String {
        val request = buildRequest("/api/providers/$id", null, "DELETE")
        return executeSync(request)
    }

    // ── Disk ──

    fun diskConnect(body: String): String {
        val request = buildRequest("/api/disk/connect", body, "POST")
        return executeSync(request)
    }

    fun diskList(connId: String, path: String): String {
        val request = buildRequest("/api/disk/$connId/list?path=$path")
        return executeSync(request)
    }

    fun diskMkdir(connId: String, path: String): String {
        val body = com.google.gson.Gson().toJson(mapOf("path" to path))
        val request = buildRequest("/api/disk/$connId/mkdir", body, "POST")
        return executeSync(request)
    }

    fun diskDelete(connId: String, path: String): String {
        val body = com.google.gson.Gson().toJson(mapOf("path" to path))
        val request = buildRequest("/api/disk/$connId/delete", body, "POST")
        return executeSync(request)
    }

    fun diskDisconnect(connId: String): String {
        val request = buildRequest("/api/disk/$connId/disconnect", "{}", "POST")
        return executeSync(request)
    }

    // ── Memories ──

    fun listMemories(): String {
        val request = buildRequest("/api/memories")
        return executeSync(request)
    }

    fun getMemory(date: String): String {
        val request = buildRequest("/api/memories/$date")
        return executeSync(request)
    }

    fun getDiskConfig(): String {
        val request = buildRequest("/api/disk/config")
        return executeSync(request)
    }

    fun saveDiskConfig(body: String): String {
        val request = buildRequest("/api/disk/config", body, "PUT")
        return executeSync(request)
    }

    fun checkUpdate(): String {
        val request = buildRequest("/api/version")
        return executeSync(request)
    }

    // ── Disk Upload/Download ──

    fun diskUpload(connId: String, path: String, inputStream: java.io.InputStream, fileName: String): Boolean {
        return try {
            val url = "$BASE_URL/api/disk/$connId/upload"
            val boundary = "----FormBoundary${System.currentTimeMillis()}"

            val bodyStream = java.io.PipedInputStream()
            val out = java.io.PipedOutputStream(bodyStream)

            val t = Thread {
                try {
                    val writer = java.io.OutputStreamWriter(out, Charsets.UTF_8)
                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"path\"\r\n\r\n")
                    writer.write(path)
                    writer.write("\r\n")
                    writer.write("--$boundary\r\n")
                    writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                    writer.write("Content-Type: application/octet-stream\r\n\r\n")
                    writer.flush()

                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    inputStream.close()

                    writer.write("\r\n")
                    writer.write("--$boundary--\r\n")
                    writer.flush()
                    out.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            t.start()
            t.join(60000)

            val requestBody = okhttp3.RequestBody.create(
                "multipart/form-data; boundary=$boundary".toMediaType(),
                bodyStream.readBytes()
            )

            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${getToken() ?: ""}")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()
            val json = com.google.gson.Gson().fromJson(body, Map::class.java)
            json["ok"] == true
        } catch (e: Exception) {
            Log.e(TAG, "diskUpload failed", e)
            false
        }
    }

    fun diskDownload(connId: String, path: String): Map<String, Any>? {
        return try {
            val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
            val request = buildRequest("/api/disk/$connId/download?path=$encodedPath")
            val raw = executeSync(request)
            @Suppress("UNCHECKED_CAST")
            val json = com.google.gson.Gson().fromJson(raw, Map::class.java) as? Map<String, Any>
            json?.takeIf { it["ok"] == true }
        } catch (e: Exception) {
            Log.e(TAG, "diskDownload failed", e)
            null
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
