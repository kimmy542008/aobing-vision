# 敖丙视界 iPhone / iPad 0.1.0

开发者 Rios敖丙。最低 iOS16，SwiftUI 原生影片网格、搜索、可配置 JSON 点播接口、选集和 AVPlayer 播放。接口和解码能力仍需实机测试。当前为首版工程，直播、电视首页布局和跨源聚合尚待移植。

在 Mac 安装 Xcode 和 XcodeGen，在 ios 目录运行 xcodegen generate，打开生成的项目。选择自己的 Signing Team 和唯一 Bundle ID，再连接 iPhone 编译安装。TestFlight 或 App Store 发布需要 Apple Developer 团队、有效签名及对应分发配置。

CI 仅编译模拟器应用。模拟器产物不能安装到 iPhone，当前没有可供用户直接安装的签名 IPA。
