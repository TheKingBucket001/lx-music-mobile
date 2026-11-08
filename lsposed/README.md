# LX 播放恢复（LSPosed）

给原版 LX Music 使用的独立模块。它把媒体停止指令转为暂停，保留播放器，让息屏等待或蓝牙重连后仍可继续播放。无需修改或重新签名 LX Music APK。

## 使用

1. 安装模块 APK。
2. 在 LSPosed 中启用“LX 播放恢复”，作用域只选择 **LX Music（`cn.toside.music.mobile`）**。
3. 完整结束 LX Music 进程，再重新打开。后台已有的旧进程不会自动加载新 Hook。
4. 播放一首歌，暂停并等待超过 10 分钟，再检查耳机播放键和应用内播放按钮。也请按自己的习惯复测息屏、蓝牙断开后重连。

模块没有桌面图标和设置页，在 LSPosed 中管理即可。关闭模块并重新启动 LX 可以恢复原版行为。

## 生效范围

模块仅在 LX Music 的主进程中工作。它匹配 TrackPlayer 事件 Intent 的 action `com.guichaguri.trackplayer.event`，在读取 `event=remote-stop` 时返回 `remote-pause`，让原版进入已有的暂停流程。

转换覆盖耳机、系统媒体控制和通知栏等来源的媒体 Stop。原事件没有可靠来源标识，无法只识别蓝牙 Stop。通知栏的停止或滑除也会变成暂停；需要关闭应用时请使用 LX 自己的退出功能。

应用内主动退出和睡眠定时退出没有被模块拦截。本模块不处理音源失效、网络缓冲或系统强行结束整个进程。

## 兼容性与验证

- 目标 APK：官方 LX Music **1.9.2 / ARM64**。已核对官方发布包的签名、哈希和 DEX：它仍通过 `Intent.getStringExtra("event")` 将 TrackPlayer 事件交给 React Native。
- 需要支持经典 Xposed API 82 的 LSPosed。模块最低 Android 8.0。
- Hook 使用 Android 系统方法，避开了原版播放器内部类和方法的混淆名称。其他 LX 版本需要重新核对调用链，不保证兼容。
- JVM 行为测试和 Android 构建验证不等于 ART 上的 Hook 已生效。首次启用后仍需一次原版 LX 的真机复测。

LSPosed 日志包含 `LXMusicStopFix 0.1.0 installed` 时，表示 LX 进程中已安装 Hook；出现 `remote-stop -> remote-pause; reads=N` 时，表示实际改写了该次事件读取。`reads` 是改写读取次数，不是去重后的系统指令数。模块不记录歌曲地址或音源信息。

## 构建与检查

在仓库根目录执行：

```sh
python3 lsposed/tests/run-jvm-tests.py --xposed-api lsposed/app/libs/xposed-api-82.jar
bash android/gradlew -p lsposed :app:lintRelease :app:assembleDebug
```

Windows 使用 `python` 和 `android\gradlew.bat -p lsposed`。JDK 为 17，Android SDK 需要 platform 35 和 build-tools 36.0.0。

正式签名包由 `.github/workflows/build-lsposed.yml` 单独构建。该工作流只构建模块，不构建 LX Music 应用。发布 APK 没有申请权限，也不包含 Xposed API 或测试替身。
