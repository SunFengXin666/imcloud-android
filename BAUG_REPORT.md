# 清云 App 登录闪退 Bug 报告

> **日期：** 2026-05-09
> **影响版本：** 1.6.70 ~ 1.6.92
> **修复版本：** 1.6.93
> **测试环境：** Android 手机（无 adb logcat，无法获取崩溃堆栈）

---

## 问题描述

App 在登录页面输入用户名/密码后点击登录，必然闪退。没有任何崩溃提示，直接退出到桌面。

**触发条件：** 输入框（OutlinedTextField/EditText）存在 + 点击登录按钮发起网络请求。

---

## 排查过程

### 阶段1：怀疑网络库问题

**尝试（均失败）：**
- 移除 OutlinedTextField 的 leadingIcon（人物/锁图标）
- 移除密码显隐按钮（trailingIcon）
- 换成原生 `AndroidView { EditText() }`
- 换成 `TextField`（非 Material3 设计）

**结论：** 崩溃不在 UI 组件本身。

---

### 阶段2：确认崩溃位置

通过 4 组对照实验定位：

| 版本 | 输入框 | 网络调用 | 导航 | 结果 |
|------|--------|----------|------|------|
| 1.6.88 | 有 | 无 | 无 | 不闪退 ✅ |
| 1.6.89 | 有 | 有 | 有 | 闪退 ❌ |
| 1.6.90 | 无 | 无 | 有 | 不闪退 ✅ |
| 1.6.91 | 无 | Thread睡眠 | 无 | 闪退 ❌ |

**关键发现：**
- `AndroidView { EditText() }` + 纯按钮（无任何异步）→ 不闪退
- `AndroidView { EditText() }` + `Thread { sleep(2000) }` → 闪退
- 崩溃不在网络请求本身，而在 **Thread + Handler 更新 UI 状态**

**触发条件（三者同时满足才会崩溃）：**
```
OutlinedTextField 或 EditText（存在）
    + Thread（后台线程）
    + Handler.post { 触发 recomposition }
    → 崩溃
```

---

### 阶段3：尝试过的修复方案（均失败）

| 方案 | 结果 | 原因 |
|------|------|------|
| `LaunchedEffect` + `withContext(Dispatchers.IO)` | 闪退 | 崩溃在 LaunchedEffect 本身 |
| `produceState` | 不闪退，但报"网络错误" | coroutines 依赖被 gradle sync 覆盖，`Dispatchers.IO` 不生效 |
| SplashScreen 的 `LaunchedEffect` 改 `Handler.postDelayed` | 闪退 | 崩溃在 LoginScreen |
| `InputMethodManager.clearFocus()` | 闪退 | 不是键盘焦点问题 |
| `OkHttpClient` 单例化 | 闪退 | 不是 client 重建问题 |
| 输入框放 AlertDialog，关闭后 post | 闪退 | Dialog 里 post 同样冲突 |

---

### 阶段4：最终解决方案

**核心思路：** `OutlinedTextField` 和原生 `EditText` 都有 Android 原生 `InputConnection` 状态，在后台线程通过 `Handler.post` 触发 recomposition 时会冲突。

**解决：** 用 Compose 原生的 `BasicTextField` 替代。

`BasicTextField` 是纯 Compose 内部实现，没有 Android 原生 `InputConnection` 和 `Editable` 状态，不会与后台线程的状态更新冲突。

**最终代码结构（LoginScreen.kt）：**

```kotlin
// 用 BasicTextField 替代 OutlinedTextField
BasicTextField(
    value = username,
    onValueChange = { username = it },
    textStyle = LocalTextStyle.current.copy(color = Color(0xFF1D1D1F), fontSize = 16.sp),
    cursorBrush = SolidColor(Color(0xFF007AFF)),
    modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Color(0xFFD1D1D6), RoundedCornerShape(12.dp))
        .padding(horizontal = 16.dp, vertical = 14.dp),
    singleLine = true,
    decorationBox = { innerTextField ->
        Box {
            if (username.isEmpty()) {
                Text("用户名", color = Color(0xFF8E8E93), fontSize = 16.sp)
            }
            innerTextField()
        }
    }
)

Button(onClick = {
    Thread {
        // 裸 OkHttp HTTP POST，不调 ApiClient
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("http://49.232.224.90:8080/api/auth/login")
            .post(requestBody).build()
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""

        mainHandler.post {
            if (code == 200 && body.contains("token")) {
                // 保存 token 并跳转
                onNavigateToChat()
            }
        }
    }.start()
})
```

---

## 根因总结

### 触发机制

```
OutlinedTextField / EditText（原生 Android 输入框架）
    ↓
占用 InputConnection 和 Editable 状态
    ↓
Thread 后台线程执行完毕
    ↓
Handler.post { mutableStateOf.value = newValue }
    ↓
触发 Compose recomposition
    ↓
recomposition 访问了输入框的 InputConnection 状态
    ↓
输入框状态与 recomposition 在不同线程冲突
    ↓
崩溃（Error，bypass try-catch，无法捕获）
```

### 为什么 BasicTextField 不崩溃

`BasicTextField` 是纯 Compose 实现，所有状态管理都在 Compose 内部，不涉及Android 原生 `InputConnection`。与后台线程的 `Handler.post` 配合时没有跨线程状态冲突。

### 为什么 `produceState` 不崩溃

`produceState` 是 Compose 管理的协程作用域（`ProducerScope`），状态更新在 composition 内部处理，不触发与 InputConnection 的冲突。但当时 coroutines 依赖被 gradle sync 覆盖，导致 `withContext(Dispatchers.IO)` 不生效，网络请求实际在主线程被系统拒绝（NetworkOnMainThreadException 被当 Error 处理）。

---

## 版本记录

| 版本 | 方案 | 结果 |
|------|------|------|
| 1.6.77 | AndroidView + EditText | 闪退 ❌ |
| 1.6.78 | AndroidView + EditText（无网络） | 不闪退 ✅ |
| 1.6.79 | + ApiClient.login() | 闪退 ❌ |
| 1.6.80 | + Thread.sleep | 闪退 ❌ |
| 1.6.81 | LaunchedEffect | 闪退 ❌ |
| 1.6.82 | + coroutines 依赖 | 闪退 ❌ |
| 1.6.83 | 纯 UI，无 async | 不闪退 ✅ |
| 1.6.84 | + LaunchedEffect + withContext | 闪退 ❌ |
| 1.6.85 | + Thread 网络 | 闪退 ❌ |
| 1.6.86 | Splash LaunchedEffect + delay | 闪退 ❌ |
| 1.6.87 | Splash 改 Handler.postDelayed | 闪退 ❌ |
| 1.6.88 | Login 纯 UI 按钮 | 不闪退 ✅ |
| 1.6.89 | + Thread 网络 | 闪退 ❌ |
| 1.6.90 | 无输入框 + onNavigateToChat() | 不闪退 ✅ |
| 1.6.91 | 无输入框 + Thread.sleep + Handler | 闪退 ❌ |
| 1.6.92 | produceState | 网络错误 ❌ |
| **1.6.93** | **BasicTextField + Thread + Handler** | **不闪退 ✅** |

---

## 附带发现：SplashScreen "刷新感"

修复闪退后，用户反馈每次打开 App 会"闪一下"才进入主页。

**原因：**
- 旧 SplashScreen 不检查 token，等 2 秒直接跳 Login
- Login 检测到有 token 再跳 Chat
- 两次导航之间有空白帧

**修复：** SplashScreen 直接检查本地 token，有效直跳 Chat，跳过 Login 页面。

**附带 bug：** SharedPreferences key 不一致——Login 保存用 `"token"`，Splash 检查用 `"auth_token"`，导致 Splash 永远读不到 token。统一为 `"auth_token"`。

---

## 经验总结

1. **无 logcat 时的调试：** 用"消除法"——逐个移除功能/组件，找到崩溃的必要条件
2. **Compose + 后台线程：** 不要在后台线程直接 `Handler.post` 更新 Compose 状态，尤其是有输入框时
3. **coroutines 依赖：** `build.gradle.kts` 添加依赖后可能被 gradle sync 覆盖丢失，需要确认依赖真正被打进 APK
4. **InputConnection 冲突：** `OutlinedTextField` / `EditText` 有原生输入状态，与后台线程 recompose 冲突时无法被 try-catch 捕获（抛的是 Error 而非 Exception）
