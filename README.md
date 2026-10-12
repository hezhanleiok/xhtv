# Xiaohe影视（xiaohetv）

基于 CatVod / 影视TV 生态的 Android 影音聚合客户端，**独立实现、协议兼容**：
不内置任何内容来源，全部来自你自己添加的订阅地址。

- 应用名：**Xiaohe影视**；包名：`com.xiaohetv`（手机版；TV 版为后续计划）
- 界面：浅色 iOS 风格（Compose Material3，iOS 系统色 + 分组列表 + 大标题）
- 播放：Media3 / ExoPlayer（硬解 + 系统软解）+ 迅雷核心（磁力 / ed2k）
- 图标：蓝→青渐变 + 小盒内播放三角（`res/mipmap` 矢量自适应图标）

---

## 1. 本次更新（对齐参考项目）

### 1.1 设置页 → 与参考 App 功能对齐

| 项目 | 行为 |
| --- | --- |
| **点播** | 显示当前点播订阅（名称或地址）。点击=输入/切换订阅；长按=编辑当前订阅；🏠=选择点播主页站点；☆=已保存的点播订阅列表（切换/编辑/删除） |
| **直播** | 同上；🏠=选择直播主页线路；☆=直播订阅列表 |
| **壁纸** | 点击=设置壁纸；🏠=切换内置壁纸；↻=重新加载壁纸配置 |
| **模块管理** | 查看内置/未内置能力清单 |
| **播放设置** | 播放引擎(EXO/MPV)、解码方式(硬解/软解)、画面比例、后台播放、播放缓冲、播放 UA、播放倍速 |
| **弹幕设置** | 加载弹幕、弹幕 API 地址、自动加载弹幕、优先使用爬虫弹幕 |
| **无痕模式** | 开/关（开启后不写入播放记录） |
| **图片尺寸** | 小/中/大/超大（影响首页海报尺寸） |
| **主题色彩** | 关/自动/12 种预设色（整个 App 主题色实时生效） |
| **DoH** | 系统 / 腾讯 / 阿里 / 360（含点播配置里自定义的 doh） |
| **缓存** | 显示缓存大小，点击即清理 |
| **备份 / 恢复** | 备份订阅+记录+收藏+设置到 `filesDir/backup`，最多保留 7 份 |
| **版本** | 显示版本号 |

### 1.2 订阅「添加即解析」（重点修复）

- 兼容 TVBox 生态的三种"反爬/伪装"分发方式（实测报文确认）：
  1. **UA 判定**：只对 UA 含 `okhttp` 的请求返回配置（否则返回首页/`你好！`）。
     核心网络默认 UA 已改为 `okhttp/4.12.0`，配置请求还会按
     `okhttp/4.12.0 → okhttp/3.12.0 → 无 UA → Chrome` 依次重试
  2. **HTTP → HTTPS 兜底**：域名只允许 https 时（http 被 Cloudflare 400）自动改用 https 重试
  3. **伪装格式**：响应体是「图片二进制 + `**` + Base64(配置)」，例如 19KB 的 JPEG
     后面藏着 12KB JSON；解码器会定位 `**`、剥离二进制、按偏移与尾部裁剪还原出完整 JSON
- 同时兼容：纯 Base64（`**`）、TvBox CBC（`2423` 开头）、UTF-8 BOM
- 相对路径以**跳转后的最终地址**为基准展开（`./`、`../` 与 `;md5;` 后缀均已支持）
- 地址**不写 `http://` 也会自动补全**；对 deflate 压缩 / HTML 错误页 / 非 JSON 响应会给出明确原因
- 支持「选择本地文件」导入配置（自动复制到 App 私有目录后以 `file://` 加载）
- 设置页新增 **刷新全部订阅**（点播 + 直播一次性重载）
- 输入地址后**立即解析并生效**，不再需要手动刷新：
  - 普通配置 → 解析 `sites/parses/lives/wallpaper/headers/proxy/doh`
  - **配置仓库 `urls`** → 自动把仓库里的每个地址登记为独立订阅，并加载第一条
  - **点播配置内嵌 `lives`** → 自动登记一条**同址同名的「直播」订阅**，设置页直播行会同步显示
  - **点播配置内嵌 `wallpaper`** → 自动登记壁纸订阅
  - **纯 M3U / TXT 文本**（直播订阅）→ 直接按频道列表解析
- 三类订阅（点播/直播/壁纸）分开保存，各自 `time` 最大的那条即"当前选中"，与参考项目一致。
- 加载成功才切换为当前；失败会保留在订阅列表里可重试/删除。

### 1.3 权限（首次启动会弹说明并申请）

| 权限 | 用途 |
| --- | --- |
| 网络 / 网络状态 | 加载订阅、播放（系统默认授予） |
| 通知 | 后台播放、磁力解析状态提示 |
| 存储 / 媒体（Android 13+ 用 `READ_MEDIA_*`） | 播放本机视频、导入本地配置文件 |
| 电话状态 | 磁力（迅雷）核心生成设备标识 |

> **订阅解析失败与存储权限无关**：配置走的是网络请求 + App 私有目录，不需要存储权限。
> 现在解析过程中的"解析中 / 成功 / 失败原因"都会用 Toast + 进度条明确提示。

### 1.4 软解码

播放设置 → 解码方式 → **软解**：使用系统的软件解码器（`c2.android.*` / `OMX.google.*`），
通过自定义 `MediaCodecSelector` 过滤掉硬件解码器实现；设备上没有任何软件解码器时自动回退硬解，不会黑屏。
（若后续补入 `lib-*.aar` 的 ffmpeg 扩展，可切换为 ffmpeg 软解。）

### 1.5 直接输入网址播放 + 磁力链接

- 首页右上角 **「网址」** 按钮：粘贴任意地址即可播放，支持
  `http/https`（含 m3u8）、`magnet:`、`ed2k:`、`thunder://`，也可直接**选择本地文件**播放
- 磁力解析：种子里含多个媒体文件时弹出文件列表（含文件大小），选择后播放
- 外部唤起：被其他 App 分享文本（`ACTION_SEND`）/ 打开链接（`ACTION_VIEW`）会自动进入播放
- 迅雷核心已内置（`app/libs/thunder-release.aar`，含 `arm64-v8a` / `armeabi-v7a` 原生库）；
  在 x86 模拟器上不可用，会给出明确提示

---

## 2. 工程结构

```
settings.gradle            # :app  :catvod
catvod/                    # 核心协议层（TV 版可直接复用）
  com.github.catvod.*            Spider / SpiderDebug / Init / Prefers / Path（兼容垫片，供第三方 jar 与 AAR 使用）
  com.xiaohe.tv.core.bean        Config / Site / Parse / Live / Group / Channel / Result / Vod / Flag / Episode …
  com.xiaohe.tv.core.config      ConfigStore（订阅持久化）/ VodConfig / LiveConfig / WallConfig / ConfigLoader
  com.xiaohe.tv.core.spider      SpiderRouter / CmsSpider / JarLoader / JsSpider
  com.xiaohe.tv.core.parser      XmlResult / LiveParser / EpgParser
  com.xiaohe.tv.core.net         OkHttp / OkDns / OkProxySelector
app/                       # UI 层
  data/      Store（记录/收藏/设置）/ ConfigStore 使用 / Backup / Session
  player/    Playback（播放入口）/ Thunder（磁力·ed2k）
  vm/        SiteVM / HomeVM / SearchVM / DetailVM / LiveVM / ConfigVM / PlayerVM
  ui/screen  Home / Search / Detail / Live / Mine / Settings / PlayerSetting / DanmakuSetting / History / Collect
  libs/      thunder-release.aar（迅雷磁力核心）
```

---

## 3. 配置格式

字段与示例见官方配置字典 <https://fongmi.github.io/TV/config/>。

```json
{
  "spider": "https://example.com/spider.jar",
  "sites": [
    { "key": "java_demo", "name": "Java Demo", "type": 3, "api": "csp_Demo" },
    { "key": "js_demo",   "name": "JS Demo",   "type": 3, "api": "./demo.js" },
    { "key": "cms",       "name": "CMS",       "type": 1, "api": "https://example.com/api.php/provide/vod/" }
  ],
  "parses": [
    { "name": "JSON 解析", "type": 1, "url": "https://example.com/parse?url=",
      "ext": { "flag": ["demo"], "header": { "User-Agent": "ExampleClient/1.0" } } }
  ],
  "lives": [
    { "name": "内嵌直播", "url": "./live.m3u", "epg": "https://example.com/epg.xml" }
  ],
  "wallpaper": "https://example.com/wall.jpg"
}
```

添加这个地址后：点播站点/解析器立即生效，**直播行会自动出现「内嵌直播」**，壁纸行自动出现该图片。

配置仓库：

```json
{ "urls": [ { "name": "我的配置", "url": "./vod.json" } ] }
```

---

## 4. 构建

环境：**JDK 17**、Android SDK（compileSdk 35）、Gradle 8.9（wrapper 已配置）。
`app/libs/thunder-release.aar` 已在仓库内（磁力核心）；若要打 arm64 真机包无需额外配置。

```powershell
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 本机踩坑：`gradlew` 通过代理下载 Gradle 发行版可能得到损坏的 zip，可直接用本机已解压的
> `C:\Users\jeryli\.gradle\wrapper\dists\gradle-8.9-bin\<hash>\gradle-8.9\bin\gradle.bat`。
> 依赖走代理：在 `~/.gradle/gradle.properties` 配置
> `systemProp.https.proxyHost=127.0.0.1` / `systemProp.https.proxyPort=10808`。

---

## 5. 与 FongMi/TV 的关系与差异

- 参考项目：<https://github.com/FongMi/TV>；本项目为**独立实现**，仅协议/配置字段兼容（不是其分支，也不含其源码）。
- `Spider` 抽象类保留 `com.github.catvod.crawler.Spider` 全限定名，第三方 `spider.jar` 可直接挂载；
  同时提供 `com.github.catvod.Init / utils.Prefers / utils.Path` 垫片供 thunder AAR 使用。
- 磁力/ed2k 复用迅雷下载核心（`thunder-release.aar`，与参考项目同一套 native 库）。

### 功能对照（截至 2026-10-12，已按实现情况校准）

| 能力 | 状态 | 说明 |
|---|---|---|
| 点播 / 直播 / 聚合搜索 / 收藏 / 播放记录 | ✅ | 跨站点换源、站内线路与选集、断点续播（EXO 与 MPV 都写记录） |
| EXO 播放核心 | ✅ | media3 1.7.1 |
| EXO 软解（真 FFmpeg） | ✅ | nextlib `libmedia3ext.so` |
| MPV 播放核心 | ✅ | `libmpv.so` + `libplayer.so`（`is.xyz.mpv.MPVLib`），显式 `gpu-context=android` |
| JAR Spider（`csp_`） | ✅ | DexClassLoader 动态加载 |
| JS Spider | ✅ | QuickJS 3.2.3 原生引擎（4 ABI `.so` 随包）+ Rhino 自动回退 |
| WebView 嗅探解析（parse type 0） | ✅ | 网页嗅探取流 |
| 并行解析（parse type 4） | ✅ | 多解析器并行取首个成功结果 |
| 本地代理服务（`127.0.0.1/proxy`） | ✅ | 内置 HTTP 服务，spider 给出的本地地址可直接播 |
| 弹幕 | ✅ | 设置项与开关已接入（弹幕数据由所配置接口提供） |
| 应用内更新 | ✅ | 走本仓库 `update.json` / GitHub Releases，可下载安装 |
| Python Spider（Chaquopy） | ❌ | 需 Chaquopy 运行时（约 15MB），本仓库不提供 |
| Node.js Spider | ❌ | 需 `libnode.so`（参考项目该库 46.5MB），本仓库不提供 |
| libass 字幕渲染 | ❌ | 未内置 libass（字幕依赖播放器/系统能力） |
| **Android TV（leanback）UI 与遥控器适配** | ❌ | 目前是手机/平板触屏 UI；遥控器仅能完成基本点击，未做焦点导航 |
| DLNA / 投屏 | ❌ | 未实现（播放器内为提示） |
| 字幕搜索 | ❌ | 未实现 |

## 6. 授权与合规

- 本项目按 **GNU GPL v3.0** 发布（见 `LICENSE`）。原因：随包分发的 `libmpv.so` 与 FFmpeg 属 GPL 系授权，
  整个 APK 需以 GPL 兼容方式分发。
- 第三方组件与许可证清单见 `THIRD-PARTY-NOTICES.md`（含 mpv / FFmpeg / nextlib / QuickJS / AndroidX 等）。
- ⚠️ **`app/libs/thunder-release.aar`（迅雷下载 SDK）为第三方私有授权组件，不允许公开再分发。**
  若要公开本仓库，请先移除该文件并同步移除 `Thunder` 调用（磁力/ed2k 会不可用），或自行取得授权。

## 7. 权限说明（只申请必要权限，与参考项目对齐）

| 权限 | 用途 | 可否拒绝 |
|---|---|---|
| `INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE` | 加载配置、播放、网络状态探测 | 必需 |
| `WAKE_LOCK` | 播放期间解码不被系统中断 | 必需 |
| `POST_NOTIFICATIONS` | 后台播放 / 解析状态提示 | 可拒绝 |
| `READ_MEDIA_VIDEO`、`READ_MEDIA_AUDIO`（13+）；`READ_EXTERNAL_STORAGE`（≤12） | 播放本机视频音频、导入本地配置 | 可拒绝 |
| `REQUEST_INSTALL_PACKAGES` | 应用内更新后调起系统安装器 | 可拒绝 |

**刻意不申请**：`READ_PHONE_STATE`（清单里显式 `tools:node="remove"`，含第三方 SDK 可能带入的声明）、
`CAMERA`、`MANAGE_EXTERNAL_STORAGE`、生物识别、DLNA 组播等（本 App 不需要）。
所有数据仅保存在本机，不上传服务器。

## 8. 更新与发布

- App 内「设置 → 版本」可检查更新：先读仓库根目录 `update.json`，取不到再走
  GitHub Releases API（`https://api.github.com/repos/hezhanleiok/xhtv/releases/latest`）。
- 发版流程：打 tag → GitHub 上创建 Release 并上传 APK → 修改 `update.json` 的 `version` 与 `url`。
- 自更新下载到 `cacheDir/update.apk`，经 `FileProvider` 交给系统安装器。

## 9. 免责声明

本项目仅供个人学习与技术研究使用，请勿用于任何商业用途。软件本身不提供、不内置任何影视内容，
所有播放内容均来自使用者自行配置的第三方来源，请确保来源合法并自行承担相应责任。
