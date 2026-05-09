package com.imcloud.app.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imcloud.app.data.remote.ApiClient
import com.imcloud.app.ui.chat.ChatScreen
import com.imcloud.app.ui.disk.DiskScreen
import com.imcloud.app.ui.settings.SettingsScreen
import com.imcloud.app.ui.models.ModelsScreen

private val PAGE_BG         = Color(0xFFE8EBF5)
private val CARD_BG         = Color.White
private val ACCENT           = Color(0xFF5B8DEF)
private val ACCENT_LIGHT     = Color(0xFFEEF2FF)
private val TEXT_PRIMARY     = Color(0xFF1A1A2E)
private val TEXT_SECONDARY   = Color(0xFF8B8D9B)
private val DANGER           = Color(0xFFE5544E)
private val DIVIDER          = Color(0xFFE8E8F0)

private data class NavItem(val label: String, val icon: ImageVector, val index: Int)

private val navItems = listOf(
    NavItem("新对话",  Icons.Default.Bolt,          0),
    NavItem("云盘",   Icons.Default.Folder,         1),
    NavItem("设置",   Icons.Default.Settings,        2),
    NavItem("模型",   Icons.Default.AutoAwesome,     3),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onNavigateToLogin: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(0) }
    var drawerOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "清云",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TEXT_PRIMARY
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { drawerOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "菜单",
                            tint = ACCENT,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CARD_BG,
                    titleContentColor = TEXT_PRIMARY
                )
            )
        },
        containerColor = PAGE_BG
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 主内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CARD_BG,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 1.dp
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            slideInHorizontally { direction * it } + fadeIn() togetherWith
                                slideOutHorizontally { -direction * it } + fadeOut()
                        },
                        contentKey = { it }
                    ) { tab ->
                        when (tab) {
                            0 -> ChatScreen()
                            1 -> DiskScreen()
                            2 -> SettingsScreen(onNavigateToLogin = onNavigateToLogin)
                            3 -> ModelsScreen()
                        }
                    }
                }
            }

            // 遮罩 + 侧边栏（作为 overlay）
            Row(modifier = Modifier.fillMaxSize()) {
                // 侧边栏
                AnimatedVisibility(
                    visible = drawerOpen,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = spring(stiffness = 400f, dampingRatio = 0.85f)
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = spring(stiffness = 400f, dampingRatio = 0.85f)
                    )
                ) {
                    SidebarCard(
                        onItemClick = { index ->
                            if (index == 4) {
                                runCatching { ApiClient.logout() }
                                onNavigateToLogin()
                            } else {
                                selectedTab = index
                                drawerOpen = false
                            }
                        }
                    )
                }

                // 遮罩（占满剩余空间）
                AnimatedVisibility(
                    visible = drawerOpen,
                    enter = fadeIn(animationSpec = spring(stiffness = 400f, dampingRatio = 0.85f)),
                    exit = fadeOut(animationSpec = spring(stiffness = 400f, dampingRatio = 0.85f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { drawerOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarCard(onItemClick: (Int) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    Surface(
        modifier = Modifier
            .width(244.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
        color = CARD_BG
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ACCENT, Color(0xFF8BA5F5))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("清", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("清云", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TEXT_PRIMARY)
                    Text("AI 助手", fontSize = 12.sp, color = TEXT_SECONDARY)
                }
            }

            Divider(color = DIVIDER, thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(12.dp))

            navItems.forEach { item ->
                val isSelected = selectedTab == item.index
                SidebarItem(
                    icon = item.icon,
                    label = item.label,
                    isSelected = isSelected,
                    onClick = {
                        selectedTab = item.index
                        onItemClick(item.index)
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Divider(color = DIVIDER, thickness = 0.6.dp)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onItemClick(4) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ExitToApp, null, tint = DANGER, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("退出登录", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DANGER)
            }

            Text("v1.6.10", fontSize = 10.sp, color = TEXT_SECONDARY, modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor   = if (isSelected) ACCENT_LIGHT else Color.Transparent
    val iconColor = if (isSelected) ACCENT else TEXT_SECONDARY
    val textColor = if (isSelected) ACCENT else Color(0xFF4A4D5E)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = textColor)
    }
}
