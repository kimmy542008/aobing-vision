# 敖丙视界 API19 兼容版 0.4.3

开发者：Rios敖丙 · 联系微信：liao542008。

用户志高 CHIGO A10 旗舰版报告 Android 字符串 9.0，但设备检测返回 SDK_INT=19、armeabi-v7a/armeabi、bigfish、970x_box，固件包含 KOT49H。安装器按 API 等兼容条件判断，原0.3/0.4包要求API21，因此与此设备API不匹配。此版使用Android19的android.jar编译全部Java与资源，minSdk19、targetSdk27，无AndroidX/Media3/WebView，使用 Conscrypt 2.5.2 提供 TLS，许可证为 Apache-2.0。

这是独立的基础播放器兼容版，包名 `com.rios.aobing.legacy`，不覆盖或清除原应用。不是把原版所有功能直接移植。它提供：

- 内置原有直播快照，可按分类和名称筛选；支持M3U/频道TXT地址配置。
- 原有JSON点播接口配置、搜索、分页、剧集选择；接口不可用时可替换。仅播放可直接访问的HTTP/HTTPS视频，不提供VIP/DRM解析。
- 视频地址播放，系统VideoView硬解，遥控器确定暂停/继续、左右快进退、直播上下换台、菜单打开操作、返回列表。
- 只监听127.0.0.1的随机路径HTTP转接，转写HLS相对路径、变体及URI属性，通过Java TLS1.2请求远端；保留证书和主机名校验，不绕过证书失败。旧系统信任库仍可能不支持部分站点证书。
- 配置保存在应用私有设置，直播留空恢复内置；“关于”显示版本、作者、微信。

播放能力取决于盒子系统解码器；HEVC/AV1、高码率、某些HLS特性可能不支持。频道名称中的清晰度来自快照，不代表盒子已成功解码或网速保证。源站可能变动或受地区限制。此APK没有新引入ABI限制，直接使用盒子已有ARM32播放器。尚未经过用户CHIGO盒子安装与播放实测。

构建：Python3、JDK17、Android SDK platform19、build-tools34.0.0。设置JAVA_HOME和ANDROID_HOME，执行 `python scripts/build-legacy.py`。产物在 `installer/legacy`。首次在用户目录 `.aobing/legacy-debug.keystore` 生成本地测试签名；密钥不会包含在源码包或上传GitHub。不同构建者的测试签名可能不同。CI产物与本地包不能保证可覆盖安装，切换前应备份配置。

源码采用GPL-3.0，见LICENSE。原有频道数据来源见assets/SOURCES.md；图标来自用户品牌素材；所有第三方署名保留。
