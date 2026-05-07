package com.imcloud.app.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imcloud.app.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(Color(0xFFD4EAFF), Color(0xFFE8F4FF), Color(0xFFF5FAFF), Color.White)
    )

    val clouds = listOf(0.15f, 0.4f, 0.65f, 0.85f)
    val infiniteTransition = rememberInfiniteTransition(label = "clouds")

    val cloudOffsets = clouds.mapIndexed { index, _ ->
        infiniteTransition.animateFloat(
            initialValue = -30f, targetValue = 30f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000 + index * 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "cloud_$index"
        )
    }

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing)),
        label = "rotation"
    )

    val statusTexts = listOf("正在初始化...", "加载对话历史...", "连接服务器...", "准备就绪")
    var statusIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) { delay(600); statusIndex = (statusIndex + 1) % statusTexts.size }
    }

    LaunchedEffect(Unit) {
        delay(2500)
        try {
            val loggedIn = withContext(Dispatchers.IO) { ApiClient.checkAuth() }
            if (loggedIn) onNavigateToChat() else onNavigateToLogin()
        } catch (_: Exception) {
            onNavigateToLogin()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(gradient), contentAlignment = Alignment.Center) {
        clouds.forEachIndexed { index, yFraction ->
            Text(text = "☁\uFE0F", fontSize = (28 + index * 6).sp,
                modifier = Modifier.graphicsLayer {
                    translationX = (cloudOffsets[index].value + 40 + index * 80).dp.toPx()
                    translationY = (yFraction * 300 + 60).dp.toPx()
                    alpha = 0.4f + index * 0.1f
                })
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = "⚡", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "清云", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D1D1F))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "智能助手 · 你的专属 AI", fontSize = 15.sp, color = Color(0xFF8E8E93))
            Spacer(modifier = Modifier.height(48.dp))
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(32.dp)) {
                    drawArc(Color(0xFFE5E5EA), 0f, 360f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Color(0xFF007AFF), rotation, 90f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = statusTexts[statusIndex], fontSize = 13.sp, color = Color(0xFF8E8E93), textAlign = TextAlign.Center)
        }
    }
}
