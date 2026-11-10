# 编译依赖

`app/libs/libxposed-api-102.jar` 是官方 libxposed API 102.0.0 AAR 中的 `classes.jar`，使用 `compileOnly`。运行时接口由框架提供，不打入模块 APK。

- 官方发布：https://github.com/libxposed/api/releases/tag/102.0.0
- AAR：https://github.com/libxposed/api/releases/download/102.0.0/api-102.0.0.aar
- AAR SHA-256：`423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0`
- 提取的 JAR SHA-256：`a515dd7a53cd7a47c05e101dff77d61acb3091a97b20a885b9ea3494412db985`
- 许可证：Apache License 2.0，https://github.com/libxposed/api/blob/102.0.0/LICENSE

模块图标直接复用 LX Music 的 `ic_launcher_foreground.xml`、`ic_launcher.xml` 和背景色资源，保留原有图形与配色。原项目：https://github.com/lyswhut/lx-music-mobile 。
