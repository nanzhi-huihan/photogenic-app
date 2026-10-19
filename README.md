# AI 踩点拍照 APP — Photogenic 📸

> 单人出游也能拍出"有人"的打卡照

## 功能流程

```
拍正面照 → 选择姿势 → 选择风格 → AI 生成 2D 人物 → 摄像头叠加 → 拍照合成 → 保存分享
```

## 项目结构

```
lib/
├── main.dart                    # 入口
├── screens/
│   ├── home_screen.dart         # 首页（上传照片）
│   ├── camera_screen.dart       # 摄像头拍照页
│   └── preview_screen.dart      # 预览合成页
├── widgets/
│   ├── pose_selector.dart       # 姿势选择器
│   └── character_overlay.dart   # 2D人物叠加（拖拽缩放）
├── services/
│   └── aigc_service.dart        # AIGC API 服务层
└── models/
    └── pose.dart                # 姿势/风格数据模型

android/                         # Android 配置
pubspec.yaml                     # Flutter 依赖
```

## 内置姿势（8种）

站立 / 比耶 / 坐姿 / 思考 / 走路 / 靠墙 / 跳跃 / 挥手

## 内置风格（5种）

日系动漫 / 水彩风 / 赛博朋克 / Q版大头 / 铅笔素描

## 构建运行

### 前置要求

1. **Flutter SDK 3.16+** → [flutter.dev](https://flutter.dev)
2. **Android Studio** + Android SDK 34
3. 一台 Android 手机（支持 USB 调试）

### 构建步骤

```bash
# 1. 进入项目目录
cd 踩点拍照APP

# 2. 安装依赖
flutter pub get

# 3. 连接手机后运行
flutter run

# 4. 打包 APK
flutter build apk --release

# APK 输出路径: build/app/outputs/flutter-apk/app-release.apk
```

## AI 接入说明

MVP 阶段使用本地占位（用户照片），正式接入需配置 `AigcService`：

### 方式 1：Stable Diffusion WebUI（推荐，免费自部署）

```dart
AigcService(config: AigcConfig(
  apiUrl: 'http://你的SD地址:7860',
  apiKey: '',
  model: 'sdxl',
));
```

SD WebUI 需要安装 **IP-Adapter FaceID** 扩展以支持参考人脸。

### 方式 2：Replicate API（付费，开箱即用）

```dart
AigcService(config: AigcConfig(
  apiUrl: 'https://api.replicate.com',
  apiKey: 'r8_xxxxxxxx',
  model: 'replicate',
));
```

### 方式 3：通义万相 / 文心一格 / 6pen 等国内 API

修改 `aigc_service.dart` 中的 `_callApi` 方法适配对应接口。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Flutter 3.16 (Dart) |
| 摄像头 | camera 插件 |
| AI 生成 | 第三方 AIGC API (SD/Replicate/通义万相) |
| 图片合成 | dart:ui Canvas |
| 存储 | path_provider |
| 分享 | share_plus |

## 路线图

- [x] MVP: 摄像头 + 姿势选择 + 照片叠加 + 保存分享
- [ ] 接入真实 AIGC API
- [ ] 内容审核（鉴黄/敏感内容）
- [ ] 隐私协议 + 用户协议
- [ ] 更多姿势和风格
- [ ] 批量生成 / 视频输出
