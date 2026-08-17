# 自动连点器 AutoClicker（Android）

原生 Android 自动连点 App。借助无障碍服务 `AccessibilityService` 的 `dispatchGesture`
在任意界面注入系统级点击，配合悬浮窗与音量键快捷启停，支持多坐标点与定时连点。

## 功能
- **系统级连点**：通过无障碍服务在任意 App 上模拟点击，不依赖屏幕录制。
- **多坐标点**：可拾取并保存多个点击坐标，按顺序循环点击。
- **参数可调**：点击间隔（ms）、点击次数（0 = 无限）、循环模式。
- **悬浮窗控制**：可拖动面板，含开始/停止、设置入口、状态指示。
- **音量键启停**：双击音量下键切换连点（短按不冲突系统音量）。
- **坐标拾取**：半透明悬浮层捕获屏幕点击记录坐标。
- **开机自启悬浮窗**：设备启动后自动拉起（需已授权）。
- **配置持久化**：`SharedPreferences` 保存坐标点与参数。

## 技术栈
Kotlin · minSdk 24 · targetSdk 34 · compileSdk 34 · Gradle 8.4 · AGP 8.2.2 · Kotlin 1.9.22

## 构建与运行
1. 用 **Android Studio** 打开 `AutoClicker` 目录（已含 gradle-wrapper.jar，可直接同步构建）。
   或命令行：`./gradlew assembleDebug`（Windows 用 `gradlew.bat`）。
2. 连接 Android 7.0+（API 24+）设备或模拟器，安装生成的 APK。
3. 在手机上：
   - **设置 → 无障碍 → 自动连点器**，开启服务；
   - 首次会提示授予**悬浮窗（显示在其他应用上层）**权限，授予；
   - 回到 App 点「显示悬浮窗」→ 用悬浮面板「开始/停止」，或直接**双击音量下键**；
   - 点「添加点击点」进入拾取层，点击屏幕任意位置记录坐标；
   - 在设置里调整间隔 / 次数 / 循环。

## 生成 APK

当前沙箱环境无 JDK 与 Android SDK，**无法就地产出 APK**。项目已内置两种可行构建路径：

### 方式一：GitHub Actions 自动构建（无需本机工具链，推荐）
1. 将 `AutoClicker` 目录作为 Git 仓库推送到 GitHub（需含 `gradlew`、`gradlew.bat`、`.github/workflows/build.yml`、`gradle/wrapper/`）。
2. 在仓库 **Actions** 页手动触发 **Build Debug APK**，或 push 到 `main`/`master` 自动触发。
3. 构建完成后在 **Artifacts** 下载 `auto-clicker-debug-apk`（即 `app/build/outputs/apk/debug/app-debug.apk`）。

> 说明：CI 使用 ubuntu-latest + JDK 17 + Gradle 8.4，自动下载 AGP/Kotlin 依赖；Private 仓库需开启 Actions 额度。

### 方式二：本机 Android Studio 打开构建
直接打开 `AutoClicker` 目录 → 菜单 **Build → Build Bundle(s) / APK(s) → Build APK(s)**，
产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 目录结构
```
AutoClicker/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/            # gradle-wrapper.jar + .properties (Gradle 8.4)
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/autoclicker/
        │   ├── MainActivity.kt
        │   ├── ClickerService.kt          # 无障碍连点核心
        │   ├── OverlayService.kt          # 悬浮控制窗
        │   ├── CoordinatePickerService.kt # 坐标拾取层
        │   ├── BootReceiver.kt            # 开机拉起悬浮窗
        │   ├── ui/ClickPointAdapter.kt
        │   ├── data/{ClickPoint.kt,ConfigManager.kt}
        │   └── util/{PermissionUtils.kt,NotificationUtils.kt}
        ├── res/xml/clicker_accessibility_service.xml
        ├── res/layout/{activity_main,item_click_point,overlay_view,overlay_picker}.xml
        └── res/{values,drawable}/*
```

## 质量记录（QA 闭环）
独立静态审查（严过关）判定 `NEEDS FIX`，已修复以下缺陷与警告：
- [D1] 无障碍服务补 `flagRequestFilterKeyEvents` + `canRequestFilterKeyEvents` → 音量键启停生效
- [D2] `onKeyEvent` 增加 `event.repeatCount == 0` → 修复长按音量键误触疯狂切换
- [D3] `OverlayService.addView` 包 try-catch，且 `BootReceiver` 启动前校验悬浮窗权限 → 修复无权限崩溃
- [D4] 补齐 `gradle-wrapper.jar`（Gradle 8.4）→ 命令行可构建
- [W1] 点击点 id 由 `currentTimeMillis` 改为 `nanoTime()` → 避免同毫秒碰撞误删
- [W2] `onDestroy` 显式 `stopForeground(true)` → 避免通知残留
- [W3] `TAP_DURATION`/`MIN_INTERVAL` 由 1ms 提升到 10ms → 提升部分机型手势识别可靠性

## 已知限制
- 分发上架 Google Play 时，`specialUse` 前台服务类型需补充用途声明（合规项，非代码缺陷）。
- 通知小图标使用自适应图标前景（彩色矢量），Android 12+ 会被系统着色，建议后续提供单色图标。
- 部分定制 ROM 对 `dispatchGesture` 支持存在差异，属系统层限制。
