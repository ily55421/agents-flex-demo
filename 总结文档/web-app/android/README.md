# DocBlog Android App

基于 Chaquopy (Python on Android) + WebView 的文档博客 Android 应用。

## 架构

```
┌─────────────────────────────────┐
│         Android App             │
│  ┌───────────┐  ┌────────────┐  │
│  │ WebView   │  │ Chaquopy   │  │
│  │ (前端UI)  │←→│ (Python)   │  │
│  └───────────┘  └────────────┘  │
│       ↑              ↑          │
│  http://127.0.0.1:8080         │
│       │              │          │
│  ┌──────────┐  ┌────────────┐  │
│  │ static/  │  │ docs.db    │  │
│  │ (前端资源)│  │ (SQLite)   │  │
│  └──────────┘  └────────────┘  │
└─────────────────────────────────┘
```

- **WebView**: 加载前端页面，显示文档内容，已完全适配移动端
- **Chaquopy**: 在 Android 上运行 Python HTTP 服务器
- **SQLite**: 文档数据存储（FTS5 全文检索，包含 550+ 篇文档）
- **数据源**: 打包时将 `docs.db` 压缩后放入 assets，首次启动解压到内部存储

## 前置要求

1. **Android Studio** (Flamingo 或更新版本)
2. **JDK 17**
3. **Android SDK**: compileSdk 34, minSdk 24
4. **Python 3.10+** (用于准备资源)

## 快速开始

### 步骤 1: 准备数据库（如需要更新文档）

```bash
cd d:\阅读\总结文档\web-app
python server.py
# 等待文档导入完成后 Ctrl+C 停止
```

### 步骤 2: 生成应用图标和打包资源

```bash
cd d:\阅读\总结文档\web-app\android
python create_icons.py
python prepare_assets.py
```

### 步骤 3: 用 Android Studio 构建

1. 打开 Android Studio
2. File → Open → 选择 `d:\阅读\总结文档\web-app\android` 目录
3. 等待 Gradle 同步完成（首次会下载 Chaquopy 和依赖，较慢）
4. Build → Build Bundle(s) / APK(s) → Build APK(s)
5. 生成的 APK 在 `app/build/outputs/apk/debug/app-debug.apk`

### 步骤 4: 安装运行

- 通过 USB 连接手机开启USB调试，在 Android Studio 中点击 Run ▶
- 或手动安装: `adb install app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
android/
├── README.md                         # 本文件
├── build.gradle                      # 项目级 Gradle 配置
├── settings.gradle
├── gradle.properties
├── create_icons.py                   # 生成 mipmap 应用图标
├── prepare_assets.py                 # 资源打包脚本（推荐使用）
├── prepare-assets.ps1                # PowerShell 打包脚本（备选）
├── app/
│   ├── build.gradle                  # 应用级 Gradle (含 Chaquopy)
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml       # 应用清单
│       ├── java/com/docblog/app/
│       │   └── MainActivity.kt       # 主 Activity (WebView + Python 启动)
│       ├── python/
│       │   └── server_android.py     # Python HTTP 服务器 (Android 适配版)
│       ├── assets/                   # 打包资源（prepare_assets.py 生成）
│       │   ├── static/               # 前端文件 (HTML/CSS/JS)
│       │   └── data/
│       │       └── docs.db.zip       # 压缩的 SQLite 数据库
│       └── res/
│           ├── layout/activity_main.xml
│           ├── mipmap-*/ic_launcher.png  # 应用图标
│           └── values/
│               ├── strings.xml
│               ├── colors.xml
│               └── themes.xml
```

## 移动端特性

✅ **完全离线运行**: 不需要网络连接
✅ **响应式设计**: 适配各种屏幕尺寸
✅ **安全区域适配**: 支持刘海屏/挖孔屏
✅ **触摸优化**: 44x44dp 最小触摸目标
✅ **流畅滚动**: -webkit-overflow-scrolling 支持
✅ **代码块横向滚动**: 避免代码溢出
✅ **深色模式**: 跟随系统/支持手动切换
✅ **返回键导航**: WebView 内页支持返回
✅ **启动加载屏**: 优雅的启动界面

## 数据更新

当文档有新增/变更时：

1. 重新运行 web-app 的 `python server.py` 更新数据库
2. 执行 `python prepare_assets.py` 重新打包资源
3. 在 Android Studio 中重新构建 APK

## 注意事项

- APK 体积约 80-100MB（含 Python 运行时 + 数据库）
- 数据库压缩后约 63MB，首次启动需解压（几秒钟）
- 支持 arm64-v8a 和 x86_64 架构（主流手机都是 arm64）
- 外部链接会自动用系统浏览器打开
- 数据库解压后存储在应用私有目录，不会丢失初始数据

## 故障排除

### Chaquopy 下载慢
可在 `gradle.properties` 中配置国内镜像。

### 数据库首次解压慢
约 60MB 数据库，在中端手机上解压需要 3-5 秒，请耐心等待。

### 构建失败
- 确保 Android Studio 版本 ≥ Flamingo
- 确保 compileSdk = 34 已安装
- 尝试 File → Invalidate Caches → Restart
