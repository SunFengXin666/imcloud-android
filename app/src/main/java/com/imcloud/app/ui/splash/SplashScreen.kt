package com.imcloud.app.ui.splash

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TAG = "SplashScreen"

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) {
        mainHandler.post {
            val prefs = context.getSharedPreferences("imcloud_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("auth_token", null)
            // Token 格式：UUID 格式，长度 > 30
            val hasValidToken = !token.isNullOrEmpty() && token.length > 30
            Log.d(TAG, "Token: ${if (hasValidToken) "valid → Chat" else "invalid → Login"}")
            if (hasValidToken) {
                onNavigateToChat()
            } else {
                onNavigateToLogin()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "清云", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1D1F))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "智能助手", fontSize = 15.sp, color = Color(0xFF8E8E93))
        }
    }
}
