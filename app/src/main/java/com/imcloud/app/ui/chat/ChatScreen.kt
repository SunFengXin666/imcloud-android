package com.imcloud.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("登录成功！", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1D1F))
            Spacer(modifier = Modifier.height(8.dp))
            Text("欢迎使用清云智能助手", fontSize = 15.sp, color = Color(0xFF999999))
        }
    }
}
