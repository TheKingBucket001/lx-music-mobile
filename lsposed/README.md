# LX 播放恢复（LSPosed）

给官方 LX Music 1.9.2 使用的独立测试模块，版本 0.2.0。它把媒体停止指令转为暂停，保留播放器；同时在 Android 16 系统创建 LX 播放服务时，为该服务记录补齐 `mediaPlayback` 类型。无需修改 LX Music APK。

## 使用

1. 安装模块 APK。
2. 在 LSPosed 中启用“LX 播放恢复”，作用域选择 **LX Music（`cn.toside.music.mobile`）** 和 **系统框架（`android`）**。
3. **重启手机**，让系统部分和应用部分一起加载。仅结束 LX 进程不能加载系统部分。
4. 打开官方 LX，播放一首歌，暂停并等待 11–12 分钟，再检查耳机播放键和应用内播放按钮。也请按自己的习惯复测息屏、蓝牙断开后重连。

模块没有桌面图标和设置页，在 LSPosed 中管理即可。关闭模块后重启手机可恢复原版运行行为。

## 生效范围

应用部分仅在 LX Music 的主进程中工作。它匹配 TrackPlayer 事件 Intent 的 action `com.guichaguri.trackplayer.event`，在读取 `event=remote-stop` 时返回 `remote-pause`，让原版进入已有的暂停流程。

转换覆盖耳机、系统媒体控制和通知栏等来源的媒体 Stop。原事件没有可靠来源标识，无法只识别蓝牙 Stop。通知栏的停止或滑除也会变成暂停；需要关闭应用时请使用 LX 自己的退出功能。

应用内主动退出和睡眠定时退出没有被模块拦截。本模块不处理音源失效、网络缓冲或系统强行结束整个进程。

系统部分仅匹配 `cn.toside.music.mobile/com.guichaguri.trackplayer.service.MusicService`。在系统创建该服务记录前，复制其 `ServiceInfo` 并将原本为 0 的前台服务类型设为 `mediaPlayback=2`。其他服务、组件、进程、非零类型或不同 target SDK 均不处理；不改共享的原始元数据，也不跳过系统原有检查。已安装 APK 的 Manifest 和 PackageManager 的原始记录不会被改写。

官方版已有音乐入口、媒体会话和前台服务，缺少服务类型不等于它完全被系统当成普通后台应用。补齐类型尚未证实能改善这台手机的后台调度或缓冲；Stop 转暂停与服务类型补齐是两项独立处理。

## 兼容性与验证

- 目标 APK：官方 LX Music **1.9.2 / ARM64**。已核对官方发布包的签名、哈希和 DEX：它仍通过 `Intent.getStringExtra("event")` 将 TrackPlayer 事件交给 React Native。
- 需要支持经典 Xposed API 82 的 LSPosed。Stop 转暂停部分最低 Android 8.0；**服务类型补齐仅在 Android 16 / SDK 36 装载**，已核对用户设备实际系统包的构造器和字段。其他版本会记录不支持并跳过系统部分。
- Hook 使用 Android 系统方法，避开了原版播放器内部类和方法的混淆名称。其他 LX 版本需要重新核对调用链，不保证兼容。
- 20 项事件转换检查、27 项系统元数据检查和 Android 编译验证不等于 ART 上的 Hook 已生效。首次启用后仍需一次原版 LX 的真机复测。

LSPosed 日志前缀为 `LXMusicStopFix 0.2.0`：

- `installed Intent.getStringExtra`：应用事件 Hook 已装入；`remote-stop -> remote-pause; reads=N`：实际改写了事件读取。`reads` 是读取次数，不是去重后的系统指令数。
- `installed ServiceRecord`：系统构造器 Hook 已装入；`mediaPlayback 0x0 -> 0x2`：已为目标服务记录替换元数据副本。

服务类型仍需以 `adb shell dumpsys activity services cn.toside.music.mobile` 中该播放器服务的 `types=0x00000002` 为准，单看模块日志不足以确认系统运行状态。模块不记录歌曲地址或音源信息。

## 构建与检查

在仓库根目录执行：

```sh
python3 lsposed/tests/run-jvm-tests.py --xposed-api lsposed/app/libs/xposed-api-82.jar
bash android/gradlew -p lsposed :app:lintRelease :app:assembleDebug
```

Windows 使用 `python` 和 `android\gradlew.bat -p lsposed`。JDK 为 17，Android SDK 需要 platform 35 和 build-tools 36.0.0。

正式签名包由 `.github/workflows/build-lsposed.yml` 单独构建。该工作流只构建模块，不构建 LX Music 应用。发布 APK 没有申请权限，也不包含 Xposed API 或测试替身。
