# 清云 AI 助手 - Android

## 环境要求

- Android SDK（API 34）
- JDK 17+

## 首次构建

克隆后需要配置 Android SDK 路径：

```bash
# Linux/Mac
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 或设置环境变量
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
```

Windows 用户直接创建 `local.properties` 文件，内容：
```
sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

## 构建

```bash
./gradlew assembleDebug
```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## 版本

当前版本：1.6.95
