# 第三方组件与许可证清单

本仓库包含的依赖与二进制，以及各自许可证。分「随 APK 分发」与「仅构建期使用」两类。

## 一、随 APK 分发的原生库 / AAR

| 组件 | 位置 | 许可证 | 说明 |
|---|---|---|---|
| mpv（libmpv + libplayer） | `app/src/main/jniLibs/arm64-v8a/libmpv.so`、`libplayer.so` | **GPL-2.0-or-later**（mpv 默认构建） | 来自 mpv-android 系构建，JNI 封装即 `is.xyz.mpv.MPVLib` |
| FFmpeg（libavcodec / avformat / avfilter / avutil / swscale / swresample） | `app/src/main/jniLibs/arm64-v8a/` | LGPL-2.1+，或构建时启用 `--enable-gpl` 则为 GPL-2.0+ | libmpv 的解码依赖 |
| nextlib media3 扩展（FFmpeg 软解） | `app/src/main/jniLibs/arm64-v8a/libmedia3ext.so` | Apache-2.0 | https://github.com/anilbeesetti/nextlib |
| QuickJS（`wang.harlon.quickjs:wrapper-android:3.2.3`） | 经 Maven 依赖引入；独立 `.so` 也随仓库放在 `modules/quickjs/` | Apache-2.0（wrapper）/ MIT（QuickJS 本体） | https://github.com/HarlonWang/quickjs-android |
| 迅雷下载 SDK | `app/libs/thunder-release.aar` | **第三方私有授权** | ⚠️ 见下方「合规提示 2」 |
| AndroidX / Media3 / ExoPlayer | Gradle 依赖（`media3 1.7.1` 等） | Apache-2.0 | |
| OkHttp / Okio | Gradle 依赖（经 `catvod` 模块传递） | Apache-2.0 | |
| Gson | Gradle 依赖 | Apache-2.0 | |
| Coil（图片加载） | Gradle 依赖 | Apache-2.0 | |
| Rhino（JS Spider 回退引擎） | Gradle 依赖 | MPL-2.0 | |
| 应用图标与品牌图形 | `logo.png`、`app/src/main/res/mipmap-*`、`app/src/main/res/drawable/ic_logo_mark.png` | 本项目作者保留 | 由项目作者提供，非第三方素材 |

## 二、仅在构建期使用（不随 APK 分发）

Gradle、Android Gradle Plugin、Kotlin 编译器、Android SDK/NDK —— 许可证见各自官网。

## 三、合规提示

1. **本项目整体按 GPL-3.0 发布**（见 `LICENSE`）：因为随包分发的 `libmpv.so` / FFmpeg 为 GPL 系授权，
   整个 APK 需要以 GPL 兼容的方式分发。
2. **`app/libs/thunder-release.aar`（迅雷下载 SDK）属于第三方私有授权组件，不允许公开再分发。**
   若要把本仓库公开，请先删除该文件；删除后需要在代码里同步移除 `Thunder` 相关调用（磁力/ed2k 功能将不可用），
   或自行向授权方取得分发许可。
3. 二次分发时请保留 `LICENSE`、本文件，以及 `logo.png` 的归属说明。
4. 本项目不提供、不内置任何影视来源；所有可播放内容由使用者自行配置的第三方接口决定。
