# LX 播放恢复（LSPosed）

给官方 LX Music 1.9.2 使用的小模块，版本 **0.3.0**，包名 `lx.fix`，使用 LX 原图标。保留两项处理：媒体 Stop 转暂停；在 Android 16 创建 LX 播放服务时，为其服务记录补齐 `mediaPlayback` 类型。无需修改 LX APK。

## 使用

1. 安装模块 APK，在 LSPosed 中启用“LX 播放恢复”。
2. 确认 **LX Music（`cn.toside.music.mobile`）** 和 **系统框架** 两项均已启用。
3. 重启手机，让系统与应用两部分一起加载。
4. 用官方 LX 复测暂停 11–12 分钟后的手机和耳机播放恢复，以及息屏、蓝牙断开后重连。

需要支持 **现代 Xposed API 102** 的框架，面向当前使用的 LSPosed 2.2.1。旧式 API 82 框架不再兼容。覆盖更新 APK 不会替你启用模块或授权作用域。

模块使用 `META-INF/xposed/module.prop` 的 `staticScope=true`，静态列表只声明上述两项，管理器据此收窄可选范围。它不会自动重置已有授权，也仍允许取消其中一项，更新后需确认两项均已启用；只启用 LX 不会加载服务类型修正。

模块没有设置页、桌面入口、权限或后台服务。关闭模块后重启手机可恢复官方行为。

## 处理范围与边界

**Stop 转暂停：** 仅在 LX 主进程中，匹配 TrackPlayer Intent action `com.guichaguri.trackplayer.event`、extra 键 `event`、原结果 `remote-stop`，将此次读取结果改成 `remote-pause`。复用官方暂停逻辑，不修改 Intent，不拦截 destroy 或应用退出。

转换覆盖所有进入该事件路径的媒体 Stop，不能区分系统自动清理与用户主动 Stop；相应停止操作会变成暂停。应用自己的退出和睡眠定时退出沿用官方处理。本模块不处理音源失效、网络缓冲或系统直接结束进程。

**服务类型补齐：** 仅在 Android 16 / SDK 36 的系统进程中，匹配已核验的完整 `ServiceRecord` 构造器，以及 LX 的 `com.guichaguri.trackplayer.service.MusicService`、主进程、targetSdk 29 和原类型 0。复制该条记录使用的 `ServiceInfo`，将副本类型改成 `mediaPlayback=2`；不修改 PackageManager 的共享元数据，不跳过原构造器、系统检查或原异常。不支持的系统形态跳过处理。

官方版已有媒体会话和前台服务，补齐类型的后台调度收益尚未证实。两项处理没有增加系统 Stop 来源拦截。

## 版本变化与验证

0.3.0 将原来两个旧式入口合并为一个现代入口，移除旧式 API 依赖及元数据，增加静态作用域并启用发布包代码、资源压缩。两项修复的目标条件与核心处理沿用 0.2.2。

0.2.2 已在用户设备观察到 Stop 转暂停和服务类型修正，并由用户测试恢复播放正常；这不替代 0.3.0 现代入口在 ART 上的重新验证。JVM 检查验证目标筛选、原调用/异常保留、元数据副本、重复事件日志上限及生命周期隔离；Android 构建和 APK 检查验证入口、静态声明、作用域与精简内容。构建通过不代表手机 Hook 已命中。

日志前缀为 `LXMusicStopFix 0.3.0`。LX 和系统进程各最多两条正常日志：安装、首次命中；各最多另记一条内部故障。重复事件继续处理，不重复输出成功日志，没有定时日志或独立日志文件。

服务类型以 `adb shell dumpsys activity services cn.toside.music.mobile` 中目标服务的 `types=0x00000002` 为准；日志本身不能代替运行态核对。

## 构建

JDK 17、Android platform 35、build-tools 36.0.0；在仓库根目录执行：

```sh
python3 lsposed/tests/run-jvm-tests.py --xposed-api lsposed/app/libs/libxposed-api-102.jar
bash android/gradlew -p lsposed :app:lintRelease :app:assembleRelease
```

Windows 使用 `python` 和 `android/gradlew.bat -p lsposed`。正式签名包由 `.github/workflows/build-lsposed.yml` 单独构建；编译接口与测试替身均不打入 APK。

现代入口和静态作用域格式参考：[LSPosed 官方开发说明](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)、[libxposed API 102](https://github.com/libxposed/api/tree/102.0.0)。
