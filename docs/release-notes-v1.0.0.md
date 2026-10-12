## xiaohe影视 (XHTV) v1.0.0

首个可安装版本。**使用 debug 签名**（与后续更新包签名一致，可直接覆盖安装）。

### 本版要点

- **播放核心**：EXO（media3 1.7.1 + nextlib FFmpeg 软解）与 MPV（`libmpv.so` + `libplayer.so`，显式 `gpu-context=android`），可在播放器内热切换，切换不丢进度。
- **点播**：聚合搜索、跨站点自动换源、站内线路与选集、断点续播；播放记录在 EXO 与 MPV 两种引擎下都会写入（含每 10 秒定时落库）。
- **直播**：M3U / TXT / JSON 直播源 + XMLTV 节目表，上下滑动换台。
- **Spider**：JAR（`csp_`）+ JS（QuickJS 3.2.3 原生引擎，失败自动回退 Rhino）+ CMS 接口。
- **手势**：单击＝控制条，双击＝播放/暂停，横向滑动＝快进快退，竖向滑动＝上一集/下一集。
- **首页**：左上角图标 → 观看历史；站点名 → 站点管理（点名字切换站点，每行「搜索 / 换源」开关）。
- **权限最小化**：仅申请必要权限（已在清单显式移除 `READ_PHONE_STATE`，含第三方 SDK 带入的声明）。
- **应用内更新**：读取本仓库 `update.json`，取不到时回退 GitHub Releases API；下载后经 `FileProvider` 交系统安装器。

### 重要说明

- 本软件只是**播放器**，不提供、不内置任何影视订阅或片源；请自行添加你合法获得的配置地址。
- 仓库内 `app/libs/thunder-release.aar`（迅雷下载 SDK）为**第三方私有授权组件，不允许公开再分发**；二次分发前请阅读 `THIRD-PARTY-NOTICES.md` 并移除该文件（移除后磁力/ed2k 将不可用）。
- 因随包分发 `libmpv.so`/FFmpeg（GPL 系），本项目整体按 **GPL-3.0** 发布。
- 本版本**仅包含 `arm64-v8a`**（与参考项目 FongMi 一致）。32 位设备需要额外的 armv7 原生库（`libmpv`、FFmpeg 系列、`libplayer`），目前未提供。
- 仅供个人学习与技术研究使用，请勿用于商业用途。
