# SmartLock 智能门禁 Android APP v2.0

基于蓝牙 BLE 的 STM32 智能门禁 Android 原生应用。

## 项目结构

```
AndroidProject/
├── app/
│   ├── build.gradle                          # 模块构建配置
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml               # 权限声明 + BLE feature
│       ├── java/com/smartlock/app/
│       │   ├── MainActivity.java             # 主页面 + 底部导航
│       │   ├── ble/BleManager.java           # BLE 连接管理器（单例）
│       │   ├── protocol/BtProtocol.java      # 二进制帧协议编解码
│       │   ├── model/LockRecord.java         # 开锁记录数据模型
│       │   ├── model/CardInfo.java           # 卡片数据模型
│       │   └── ui/
│       │       ├── HomeFragment.java         # 首页（开锁+密码+最近记录）
│       │       ├── RecordsFragment.java      # 记录页面
│       │       ├── CardsFragment.java        # 卡片管理
│       │       └── SettingsFragment.java     # 设置页面
│       └── res/
│           ├── drawable/                      # 渐变背景、按钮样式、图标
│           ├── layout/                        # 页面布局文件
│           ├── menu/bottom_nav_menu.xml       # 底部导航菜单
│           ├── color/nav_tint.xml             # 导航图标颜色选择器
│           ├── mipmap-anydpi-v26/             # 自适应启动图标
│           └── values/                        # 颜色、字符串、主题
├── build.gradle                              # 根项目构建配置
├── gradle.properties
└── settings.gradle
```

## BLE 通信协议

帧格式：`[0xAA] [CMD] [LEN] [DATA...] [XOR校验]`

| 命令 | 方向 | 功能 |
|------|------|------|
| 0x01 | APP→MCU | 远程开锁 |
| 0x02 | APP→MCU | 远程关锁 |
| 0x03 | APP→MCU | 密码开锁 |
| 0x10 | APP→MCU | 添加卡片 |
| 0x11 | APP→MCU | 删除卡片 |
| 0x12 | APP→MCU | 获取卡片列表 |
| 0x20 | APP→MCU | 获取开锁记录 |
| 0x30 | APP→MCU | 设置人群模式 |
| 0x40 | MCU→APP | 状态上报 |
| 0x41 | MCU→APP | 通知上报 |

BLE UUID：
- Service: `0000ffe0-0000-1000-8000-00805f9b3fb5`
- Characteristic: `0000ffe1-0000-1000-8000-00805f9b3fb5`

## 构建方法

1. 用 **Android Studio Hedgehog (2023.1.1)** 或更高版本打开此项目
2. Gradle 同步完成后，点击 **Run** 安装到设备
3. 或使用命令行：`./gradlew assembleDebug`

## 最低要求

- minSdk: 26 (Android 8.0)
- 需要 BLE (Bluetooth Low Energy) 硬件支持
- 目标设备需 HC-05/HM-10 蓝牙模块（BLE 从机）
