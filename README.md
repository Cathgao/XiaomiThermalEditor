# 小米温控编辑器 (Xiaomi Thermal Editor)

专门用于查看、解密、修改和加密小米 HyperOS / MIUI 温控配置文件（`/data/vendor/thermal/config/*.conf`）的 Android 原生工具。

---

## 核心特性

- **自动解密与加密**
  - 内置小米经典密钥（`thermalopenssl.h`）与新版 AES-128-CBC 密钥。
  - 打开配置文件时自动探测并无损解密，保存时自动加密写回。
- **档位可视化与手势拖动排序**
  - 自动识别并解析各温控区域（`SS-CPU`、`MONITOR-GPU`、`MONITOR-WIRELESS` 等）的规则档位。
  - 支持直接长按卡片或触摸手柄拖拽调整档位顺序。
  - 支持一键增删档位并自动按阶梯填充推荐参数。
- **硬件频率动态探测与列表选择**
  - 启动时自动从内核 sysfs 扫描本机真实的 CPU 丛集与 GPU 可用频率表。
  - 目标频率（`target`）支持直接下拉列表点选。
- **GPU 档位双向翻译**
  - 读取时自动将配置文件中的 GPU 限制等级（`Level 0 ~ 11`）翻译为实际工作频率（MHz）。
  - 保存时自动将频率转换回系统温控守护进程所需的原始 Level 数值。
- **安全与即时生效**
  - 保存时自动创建 `.bak` 原文件备份，支持一键回退还原。
  - 支持在应用内一键重启 `mi_thermald` 温控服务，修改立即生效，无需重启手机。
  - 提供可视化编辑与原生源码模式双向同步切换。

---

## 系统要求

- **运行环境**：Android 8.0 及以上 (API 26+)
- **权限需求**：必须具有 Root 权限（KernelSU / SukiSU / APatch / Magisk）
- **适用机型**：搭载高通骁龙或联发科平台的小米 / Redmi 系列手机

---

## 项目结构

```text
r:\XiaomiThermalEditor
├── app
│   ├── src/main/java/com/xiaomi/thermaleditor
│   │   ├── MainActivity.java         # 主页面交互与文件/区域控制
│   │   ├── FrequencyManager.java     # CPU/GPU 真实硬件频率动态探测与映射
│   │   ├── RootShell.java            # Root 提权执行与文件安全读写
│   │   ├── ThermalCipher.java        # 小米 AES-128-CBC 自动解密与加密引擎
│   │   ├── ThermalParser.java        # 温控配置无损解析器与序列化器
│   │   ├── ThermalSection.java       # 温控区域数据模型
│   │   ├── ThermalTier.java          # 单个温控档位数据模型
│   │   ├── TierAdapter.java          # 档位 RecyclerView 适配器
│   │   └── TierItemTouchHelperCallback.java # 手势拖拽与动画调度
│   └── src/main/res/                 # 布局与主题资源
└── build.gradle.kts                  # Gradle 构建配置
```

---

## 编译与安装

通过根目录 Gradle Wrapper 构建 Debug APK：

```bash
# 构建 APK
./gradlew assembleDebug

# 直接安装到已连接的手机
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
