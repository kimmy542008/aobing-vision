# 敖丙视界 Switch 自制软件原型 0.1.1

开发者：Rios敖丙。GPL-3.0-or-later，第三方库保持其原始许可。仅面向已经能运行自制软件的初代 Switch；不是任天堂官方商店应用，也不包含修改主机系统的工具。

解压构建产物到 SD 卡根目录，目录为 `/switch/aobing-vision/`，从现有自制软件启动器运行 `.nro`。建议使用有足够内存的应用模式。请勿将模拟器或安卓 APK 当作此版本安装包。

- L/R 切换首页、直播、电影、剧集；方向键选择，A 确定，B 返回，+ 退出。
- 直播 Y 切换分组；点播 Y 调用系统键盘搜索；X 配置 JSON 点播接口和 M3U 订阅。
- `vod-api.txt` 可以填写有权使用的 MacCMS JSON 接口；内置直播快照与其它平台清理后的列表一致，不能保证各地区均可用。自定义 `live.m3u` 优先于内置列表。
- 内置 FFmpeg 解码与 SDL 画面、音频输出，不跳转其它播放器。此原型采用软件解码，尚未接入 Tegra 硬件加速，不能承诺高清流畅或与电视版全部功能一致。
- 当前点播展示接口第一页并按返回类型初步分为电影/剧集；完整类别、多页、海报、续播、播放中换台、下方选集与语音尚待移植。请勿将此原型当作完整正式版。

CI 使用 devkitPro/libnx、SDL2、SDL2_ttf、curl、FFmpeg、nlohmann/json 交叉编译，产物在 Actions 的 `Switch-homebrew-prototype-0.1.1`。未经过 Switch 真机测试；编译成功不代表解码、音画同步或触控已验证。包中包含匹配提交的源码 ZIP，依赖版本可见构建日志。

构建：安装 devkitPro 的 Switch 开发环境和 workflow 所列库，准备固定版本 json.hpp、可信 CA 证书包，运行 `source /opt/devkitpro/switchvars.sh` 后 `make -C switch`。Makefile 改编自 switchbrew/switch-examples 的 SDL2 示例；第三方版权不被本项目署名替换。
