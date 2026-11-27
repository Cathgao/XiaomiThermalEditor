# 小米温控编辑器 (Xiaomi Thermal Editor)

专门用于查看、解密、修改与加密小米 HyperOS / MIUI 温控配置文件（`/data/vendor/thermal/config/*.conf`），并管理电量与性能（`com.miui.powerkeeper`）应用温控分类规则的 Android 原生 Root 工具。

---

## 核心特性

### 1. 温控配置加解密与双向编辑
- **AES-128-CBC 自动识别与加解密**：内置小米经典密钥（`thermalopenssl.h`）与新版 AES-128-CBC 密钥，同时也兼容明文配置；读取时自动探测格式，写回时自动按原密钥重新加密，并处理 `chattr -i`、`0771` 权限、`root:system` 属主与 SELinux `restorecon`。
- **可视化编辑与源码编辑**：
  - 自动识别各温控区域（如 CPU、GPU、无线充、电池等）的基础参数与多档位规则（`trig` 触发温度、`clr` 回退温度、`target` 限制目标）。
  - 支持触摸左侧手柄拖拽调整档位顺序、一键增删档位（自动递增温度并填充推荐参数），基础参数支持折叠/展开。
  - 可视化编辑与源码编辑模式无缝切换，自动双向同步模型与文本。

### 2. 真实硬件频率探测与 GPU 档位映射
- **动态扫描内核 sysfs**：获取 Root 权限后实时读取 `/sys/devices/system/cpu/cpufreq/policy*` 与 GPU 节点，展示本机真实的 CPU 丛集与 GPU 可用频率表。
- **GPU 档位双向翻译**：界面中以实际频率（MHz）直观展示与选择 GPU 限制，保存时自动转换回温控配置所需的原始 Level 数值；也支持任意目标值手动输入。

### 3. 应用温控规则管理（PowerKeeper 数据库）
- **可视化应用管理**：
  - 自动匹配本机已安装应用的真实图标与名称（支持检测 HyperOS / MIUI `GET_INSTALLED_APPS` 读取应用列表权限并一键通过 Root 授予）。
  - 支持按名称搜索、过滤“仅已安装”、从应用选择器添加应用（自动检测其他分类冲突并支持一键移动），以及从规则中移出应用。

### 4. 安全备份与即时生效
- **温控配置**：每次保存自动备份原文件为 `<文件名>.bak`，支持一键恢复备份并重启 `mi_thermald` 温控服务（`setprop ctl.restart mi_thermald`）。
- **应用规则库**：每次保存自动备份原库为 `user_configure.db.bak`，支持一键恢复数据库备份并重启 `com.miui.powerkeeper` 服务使规则立即生效。

---

## 系统要求

- **系统版本**：Android 8.0 及以上（`minSdk 26` / `targetSdk 35`）
- **权限要求**：必须具备 Root 权限（KernelSU / SukiSU / APatch / Magisk）
- **适用设备**：小米 / Redmi HyperOS、MIUI 系列手机

---

## 项目结构

```text
XiaomiThermalEditor/
├── .github/workflows/
│   └── release.yml                      # 打 v* 标签自动构建签名 Release APK
├── app/
│   ├── build.gradle.kts                 # 模块构建配置 (compileSdk 35, Java 17)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/xiaomi/thermaleditor/
│       │   ├── MainActivity.java        # 主界面、三模式切换与温控文件编辑控制
│       │   ├── AppRulesController.java  # 「应用规则」标签页交互与聚合管理
│       │   ├── PowerKeeperDbManager.java# PowerKeeper 数据库与 Thermal.xml 映射解码/写回
│       │   ├── RootShell.java           # Root 命令执行、全局命名空间读写与服务重启
│       │   ├── ThermalCipher.java       # 小米 AES-128-CBC 自动探测解密与加密引擎
│       │   ├── ThermalParser.java       # 温控配置无损解析器与序列化器
│       │   ├── ThermalFile.java         # 温控文件数据模型
│       │   ├── ThermalSection.java      # 温控区域与基础参数模型
│       │   ├── ThermalTier.java         # 温控档位模型 (trig / clr / target)
│       │   ├── ThermalRule.java         # PowerKeeper 单条应用规则模型
│       │   ├── FrequencyManager.java    # CPU/GPU 真实硬件频率动态探测与映射
│       │   ├── AppInfoHelper.java       # 应用列表权限检查/Root 授权与图标名称解析
│       │   ├── AppEntry.java            # 已安装应用信息实体
│       │   ├── TierAdapter.java         # 档位列表适配器
│       │   ├── TierItemTouchHelperCallback.java # 档位拖拽排序回调
│       │   ├── RuleAppAdapter.java      # 规则内应用列表适配器
│       │   ├── AppPickerAdapter.java    # 添加应用弹窗列表适配器
│       │   └── SpinnerHelper.java       # 统一下拉框样式与弹窗宽度适配
│       └── res/
│           ├── drawable/                # 卡片、按钮、输入框、弹窗背景及图标
│           ├── layout/                  # 主界面、档位项、应用项、下拉项与选应用弹窗布局
│           └── values/                  # 颜色、字符串与深色主题样式
├── build.gradle.kts                     # 根项目 Gradle 配置
└── LICENSE
```

---

## 编译与安装

### 本地构建

确保已安装 **JDK 17** 与 **Android SDK**（包含 `compileSdk 35` 对应平台与 Build-Tools）：

```bash
# Linux / macOS
./gradlew assembleDebug

# Windows PowerShell（若未配置全局 ANDROID_HOME 或 local.properties）
$env:ANDROID_HOME = "D:\sdk"
.\gradlew.bat assembleDebug
```

构建完成后，安装生成的 Debug APK：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### CI 自动发布

项目配置了 [`.github/workflows/release.yml`](.github/workflows/release.yml)，推送 `v*` 标签后会自动使用仓库 Secrets（`RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`）构建签名版 Release APK 并发布 GitHub Release。

---

## 注意事项

- 修改系统温控配置可能影响设备发热、性能调度与充电策略，请务必保留自动生成的 `.bak` 备份文件，勿将触发温度设置过高。
- 如在「应用规则」中无法显示第三方应用名称或图标，可点击页面提示卡片中的「立即授权」通过系统弹窗或 Root 授予读取应用列表权限。
- 本项目基于仓库根目录 [`LICENSE`](LICENSE) 开源协议发布。
