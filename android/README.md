# Android TV 0.4.0 兼容测试版

开发者：Rios敖丙 · 联系微信：liao542008。

Android 9.0 对应 API28。本包最低 API21、目标 API28，提供 ARM32、ARM64 和两种 ARM 架构合并的单 APK，明确启用 v1/v2 签名。安装包采用 ASCII 文件名，不是拆分安装包。

优先试 `AobingTV-Android9-Universal-v0.4.0.apk`，无需猜测 32/64 位；ARM32 和 ARM64 为独立备选。通用包不包含架构专用的可选 X5 内核及 go_proxy_video 资产，默认使用系统 WebView，内置 JSON 点播及 M3U 直播不依赖这些可选组件。它不是原生鸿蒙 HAP。用户确认华为电视现有32位包可用，因此不再开发鸿蒙版。

旧0.3.0包本来就支持API21+，已通过包与签名校验，尚未拿到报错机顶盒的具体型号/安装日志，故不能声称已确定解析失败根因。本版增加兼容选项，仍需用户Android9机顶盒验证。若再次报错，请检查是否真正下载APK、传输后的大小/SHA256是否匹配、文件管理器是否完整保存文件，并提供设备型号与完整安装提示。

调试测试包使用本次构建生成的签名。GitHub构建与本机旧测试包的签名可能不同，覆盖安装如报签名冲突，需要先备份配置再卸载旧版；不会自动卸载或清除数据。

基于 GPL-3.0 的 okcaptain/TV 2.5.8 固定提交 `2362d6845f4b91b7846b5cd77f947cd5884e0894`，源于 FongMi/TV。`compatibility.patch` 保存修改，`resources` 保存新配置；原图标由桌面资产等比缩放生成。完整对应源码随 Android Release 附带，原项目与第三方版权保持不变，桌面MIT许可不替代安卓GPL许可。

构建需 Python3、Git、JDK17、Gradle8.4、Android SDK34/build-tools34.0.0，设置 ANDROID_HOME 后运行 `python scripts/build-android.py`。在全新 staging 目录执行；本脚本不是重复增量打补丁工具。
