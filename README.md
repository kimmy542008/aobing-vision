# 敖丙视界

开发者：**Rios敖丙**

Windows 与 Intel Mac 影视直播播放器：电影电视剧分类、片名搜索、选集、全屏、收藏、观看记录，以及可修改的 JSON 点播和 M3U 直播配置。

## 下载与平台状态

安装包发布在 [Releases](https://github.com/kimmy542008/aobing-vision/releases)。

| 平台 | 版本 | 状态 |
|---|---|---|
| Windows 10/11 x64 | 0.2.4 | 便携 EXE；本机曾实际播放抽样点播 1080p、直播 720p；非每个频道验证 |
| Intel Mac x86_64 | 0.2.4 | macOS 13+；测试版，未经过用户 Intel Mac 实机播放验证；没有 Apple Developer ID 签名或公证 |
| Android TV / Android 9.0 | 0.4.10 | ARM32、ARM64、双ARM架构独立APK，v1/v2签名；待用户机顶盒验证，见 [安卓说明](android/README.md) |
| Android 手机 / 平板 | 0.4.10 | [触屏版 APK](https://github.com/kimmy542008/aobing-vision/releases/download/android-v0.4.10/AobingTV-Mobile-Universal-v0.4.10.apk)，ARM32/ARM64，支持系统软键盘，可与电视版并存 |
| 志高 A10 / API19 老盒子 | 0.4.8 | [API19兼容版下载](https://github.com/kimmy542008/aobing-vision/releases/tag/android-api19-v0.4.8)，使用系统播放器；Android4.4模拟器安装启动通过，用户盒子待验证，见 [兼容版说明](android-legacy/README.md) |

志高A10用户设备检测显示版本字符串9.0、实际SDK/API19，原0.3/0.4包要求API21，不能用于此设备。此类盒子请按API选择兼容版，不能仅依据设置页面的Android版本名称。

桌面版是独立实现，并非 FongMi/TV 官方产品。Mac CI 构建使用本机临时签名，不等同于苹果公证。

## 直播目录

内置 **200 路**国内外频道候选，涵盖新闻、娱乐、体育、音乐、卡通动漫、文化、生活健康及电竞直播。194 路为播放清单/名称标注高清或抽样测得高清；不表示全部完成实际解码验证。

检测过程：汇总 5,288 条候选、抽查 425 条播放列表与媒体片段，202 条通过连接检测；进一步解码抽样移除 3 条，再添加已读取到 H264/AAC 1920×1080 的电竞频道，最终 200 路。详细数据见 `docs/live-report.json` 与 `docs/live-decode-samples.json`。连接耗时不是持续带宽测速，源和网络可能变化，4K/HEVC 依赖系统解码支持。

游戏专题、舞蹈、广场舞、养生操按钮是明确标注的官方平台入口，会打开系统浏览器，不能当作内置 24 小时电视直播。

在“源配置”中清空直播 M3U 地址并保存可使用内置列表；用户自定义订阅优先。点播 API 使用第三方服务，内容、广告和可用性由源决定。没有接入各平台会员账号或绕过付费权限。

## 构建

需要 Node.js 24。安装依赖后，在对应系统构建：

```sh
npm install
node scripts/build-desktop.mjs win32
npx electron-builder --win portable --prepackaged="release/敖丙视界-win32-x64" --publish never
```

Intel Mac：

```sh
npm install
node scripts/build-desktop.mjs darwin
```

Mac 构建脚本仅对本应用执行 ad-hoc 签名，不会关闭 Gatekeeper 或修改系统安全设置。GitHub Actions 在 Windows 和 Intel Mac runner 构建，并将测试包作为预发布版上传。

## 版权与数据

原创桌面代码采用 MIT；Android 蜂蜜衍生版本采用 GPL-3.0，见 `android/LICENSE-GPL-3.0.md`，完整对应源码随安卓Release附带。第三方版权及许可分别保留。Electron/Chromium 许可证随官方运行时附带，hls.js 使用 Apache-2.0，见 `desktop/vendor/HLS-LICENSE`。频道索引来自 [IPTV-org](https://github.com/iptv-org/iptv)，项目不托管节目视频。用户提供的图标在显示与打包时等比缩放。

应用在本机保存收藏、历史和源配置；片名搜索和播放请求会发送到用户所选的第三方源。
