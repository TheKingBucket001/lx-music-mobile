# 编译依赖

`app/libs/xposed-api-82.jar` 是 Xposed API 82 的编译接口，由 LSPosed 在运行时提供。Gradle 使用 `compileOnly`，APK 不包含这些 API 类。

- 原始下载地址：https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar
- 项目：https://github.com/rovo89/XposedBridge
- 许可证：Apache License 2.0
- SHA-256：`f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`

模块图标直接复用 LX Music 的 `ic_launcher_foreground.xml`、`ic_launcher.xml` 和背景色资源，保留原有图形与配色。原项目：https://github.com/lyswhut/lx-music-mobile 。
