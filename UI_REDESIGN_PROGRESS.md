# PoC-Deployer-System 二改版 - 项目续写交接文档

> 本文件由前一个对话的 AI 生成，供后续 AI 快速接手本项目。请先完整阅读本文，再操作代码。

## 📌 项目概况

- **原项目**：https://github.com/wqry085/PoC-Deployer-System （CVE-2024-31317 Android Zygote 注入工具，Java 原生 View，非 Compose）
- **二改者**：nianfan555（GitHub: https://github.com/nianfan555）
- **二改内容**：全新 MIUIx 风格 UI + 预制参数多管理 + 开机自启动 + 进阶备份/恢复 + 使用说明 + 多页导航
- **源码位置**：`/sdcard/Download/PoC-Deployer-System-main/`
- **已生成 APK**：`/sdcard/Download/PoC-Deployer-System-MIUIx-v30.apk`（最新，含备份管理 v25 + 注入统计 v26 + 危险二次确认 v27 + reboot 硬拦截 v28 + 蓝色主题/GROUPS 恢复 v29 + zygote 配置排错说明 v30）
- **编译环境**：Ubuntu chroot 容器（`/data/local/chroot-ubuntu`），Android SDK 在 `/home/android-sdk`

## 🚨 编译必读（重要经验）

### 编译命令（容器内）
```bash
cd /home/poc-build/project
export ANDROID_HOME=/home/android-sdk ANDROID_SDK_ROOT=/home/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
./gradlew :app:assembleDebug --no-daemon
```

### 同步源码到容器
```bash
cp -rf /sdcard/Download/PoC-Deployer-System-main/app/src/main/res /home/poc-build/project/app/src/main/
cp -rf /sdcard/Download/PoC-Deployer-System-main/app/src/main/java /home/poc-build/project/app/src/main/
cp -f /sdcard/Download/PoC-Deployer-System-main/app/src/main/AndroidManifest.xml /home/poc-build/project/app/src/main/
```

### 关键坑点（务必记住）
1. **aarch64 容器**：AGP 自带 aapt2 是 x86_64 无法运行 → 已用 `gradle.properties` 里 `android.aapt2FromMavenOverride=/home/android-sdk/build-tools/37.0.0/aapt2` 解决（qemu wrapper）
2. **必须用 JDK 17**：JDK 21 的 jlink 与 AGP 8.0 不兼容
3. **runpayload.java 冲突**：原项目有 `runpayload.java`（小写文件名但类名 RunPayload），Java 编译会报错。容器里 `app/src/main/java/com/wqry085/deployesystem/next/` 下**只能保留 `RunPayload.java`**，每次同步后要删掉旧 `runpayload.java`：
   ```bash
   cd /home/poc-build/project/app/src/main/java/com/wqry085/deployesystem/next && rm -f runpayload.java
   ```
4. **同步时 java 目录嵌套**：`cp -rf .../java /home/poc-build/project/app/src/main/java` 若目标已存在会生成 `java/java` 嵌套 → 同步前先清理
5. **Preference 主题坑**：androidx.preference 1.2.1 暴露的样式是 `Preference`、`Preference.Category`（无 Screen），全局覆盖 `preferenceTheme` 曾导致界面错乱 → 已弃用 Preference 方案，全部改自定义 View 布局
6. 每次改完源码后：同步 → 删 runpayload.java → 编译 → 复制 APK 到 /sdcard/Download

## 📱 当前界面结构（4 Tab）

```
载荷 | 预设 | 授权 | 进阶
```

### 1. 载荷页（ZygoteFragment.java + fragment_zygote_miuix.xml）
- 状态总览卡片（运行状态、日志）
- 网络配置卡片（IP、端口）
- 载荷注入卡片（命令、应用选择器、UID、GID）
- 服务控制卡片（启动/停止）
- 工具入口卡片（终端、执行命令、保存预设、运行预设、开机自启动开关）
- 高级面板（折叠）：SELinux、Nice Name、**Zygote 配置参数（挂载选项）**、Runtime Flags、远程终端
- **执行前 payload 预览 + 危险警告**（isRiskyPayload 检测 rm -rf/format/wipe/reboot/root uid）
- **UID 快捷选择**（长按 UID 行）

### 2. 预设页（PresetsFragment.java + fragment_presets.xml）
- 双列网格卡片（横屏3列），每卡片：图标+名称+自启动开关+参数摘要+运行+编辑
- 长按卡片 → 创建桌面快捷方式
- **导出/导入预设（JSON）**：导出走 FileProvider 分享，导入用 ACTION_OPEN_DOCUMENT
- 自启动开关 = 开机自动注入（bootEnabled）
- **✅ 已完成：注入统计（v26，Stellar 风格）**
  - Preset 增加 `injectCount` / `lastInjectTime` 字段（JSON 持久化）
  - 运行预设成功 → 次数 +1、记录最后注入时间
  - 卡片底部显示「⚡ 注入 N 次」+ 相对时间（刚刚/N 分钟前/N 小时前/N 天前/MM-dd HH:mm）
  - 编辑参数不清零统计；未注入过不显示该行

### 3. 授权页（AuthorizationListFragment.java）
- 白名单开关分组卡片 + 搜索 + 应用列表

### 4. 进阶页（AdvancedFragment.java + fragment_advanced.xml）
- **备份应用数据（cppkg 逻辑）** / **恢复应用数据（repkg 逻辑）**
- 包名输入 + 执行日志（等宽终端风格）
- 原理：Shizuku 获取 UID → 写脚本到 /data/local/tmp → 构建 payload 注入 → 以目标 UID 执行备份/恢复到 /sdcard/backup/包名/
- **✅ 已完成：备份管理（v25）**
  - 自动扫描 /sdcard/backup/ 目录，列出已备份应用（RecyclerView 卡片列表）
  - 每项显示：应用图标 + 应用名/包名 + 大小 + 最后备份时间 + data_data/Android_data 完整标记
  - 顶部显示汇总（备份数量 + 总大小）+ 刷新按钮
  - 点击卡片自动把包名填入输入框（方便直接恢复）
  - 删除按钮 → 二次确认对话框 → 递归删除（本地删除失败自动降级 Shizuku rm -rf）
  - 备份/恢复任务完成后自动刷新列表
  - 布局：item_backup_entry.xml（MaterialCardView 18dp 圆角卡片）、ic_delete.xml、ic_refresh.xml

## 🧩 数据模型

### PayloadPresetManager.java（多预设存储）
- SharedPreferences `payload_preset` 存 JSON 数组：`preset_list = [{id,name,payload,summary,ts,command,ip,port,uid,gid,groups,seinfo,niceName,runtimeFlags,zyg1,zyg2,zyg3,bootEnabled,mountOptions}]`
- 方法：getPresets / addPreset / updatePreset / updatePresetBoot / updatePresetFull / deletePreset / getPresetById
- 开机预设：`boot_preset_id` + `bootEnabled`，`injectBootPreset()` 注入开机预设
- 导入导出：`exportPresets` / `importPresets`

### 挂载选项（zygote 配置）
- 数组在 `arrays.xml`：multi_select_options（--mount-external-* 系列等 14 项）
- 存储键：`multi_select_preference`（SharedPreferences StringSet）
- 载荷页高级面板 "Zygote 配置参数" 多选对话框
- 预设也支持挂载选项（Preset.mountOptions）

## 🔧 开机自启动机制

- `BootReceiver.java`：监听 `BOOT_COMPLETED` + `USER_PRESENT`，桌面出现后 JobScheduler 延迟 **15秒** 注入
- `InjectJobService.java`：等待 Shizuku 就绪 → 二次确认开关/预设 → `injectBootPreset()`
- Manifest 已注册 receiver + service，权限 RECEIVE_BOOT_COMPLETED + WAKE_LOCK

## 🎨 UI 风格约定（MIUIx）

- 主色：**科技蓝 `#0054D6`**（colors.xml 的 md_theme_* 系列，v29 起由小米橙改为蓝色）
- 浅色背景 `#FAF9FF`，深色背景 `#111318`，primaryContainer `#DCE3FF`（浅）/`#0041A5`（深）
- 卡片：MaterialCardView 圆角 16-24dp + 描边（colorOutlineVariant）+ 无投影
- 分组标题：13sp bold 主色
- 弹窗：MaterialAlertDialogBuilder（主题已配 AppDialogShape 28dp 圆角）
- 右上角菜单：**自定义 PopupWindow**（popup_menu.xml + bg_popup_rounded），非系统菜单
- 关于页：自定义 activity_about.xml（弃用 drakeet 库）
- 使用说明页：GuideActivity + activity_guide.xml（参数说明含 uid 对照表、plat_seapp_contexts 参考）
- ⚠️ 硬编码橙色已全部清除：pref_item_ripple / pref_item_card / bg_bottom_sheet / bg_boot_badge / bg_step_circle / zygote_activity tabRipple / drawable-night pref_item_card

## 📝 使用说明要点（已在 GuideActivity）

- UID：1000=system、2000=shell、10xxx=u0_app、100xxx=u10_app；**不能填 0/9997/1023 等辅助 uid**
- GROUPS 绝大多数设备只能设 1 个（逗号多个被拦截）
- Nice Name：孵化 uid 2000 时必须为 `com.android.shell`
- seinfo 参考 /system/etc/selinux/plat_seapp_contexts

## ✅ 已完成功能清单

1. MIUIx 圆角卡片 UI 全面重构（载荷/预设/授权/进阶/设置/关于/说明）
2. 多 payload 预设管理（增删改查、运行、自启动、导入导出、快捷方式）
3. 开机自启动（15秒延迟，安全保护）
4. 进阶备份/恢复（cppkg/repkg 逻辑移植）
5. Payload 执行预览 + 危险警告
6. UID 快捷选择
7. Zygote 配置（挂载选项）+ 预设支持
8. 使用说明页（含参数规范）
9. 关于页（二改者 nianfan555 + 原作者 wqry085，含 GitHub 链接）
10. 日志导出（HelpActivity）
11. 圆角弹出菜单（PopupWindow）
12. **进阶页备份管理（v25）**：扫描 /sdcard/backup/ 列表、大小/时间/完整性、删除确认、点击填包名、任务后自动刷新
13. **预设注入统计（v26，Stellar 风格）**：卡片显示「⚡ 注入 N 次」+ 相对时间，JSON 持久化，编辑不清零
14. **危险执行二次确认加强（v27）**：风险明细列举 + 强制输入「确认执行」才能执行
15. **reboot 硬拦截（v28，PayloadGuard.java）**：reboot/shutdown/poweroff/halt/fastboot 一律禁止执行（100% 卡开机），覆盖全部注入入口（载荷页/预设/分享/ADB/开机自启/备份恢复），统一弹窗告知风险
16. **蓝色主题 + GROUPS 恢复（v29）**：主色改为科技蓝 #0054D6，清掉全部硬编码橙色；载荷页恢复 GROUPS 设置行（默认 9997）
17. **Zygote 配置排错说明（v30）**：使用说明页新增 param7「Zygote 配置」+ 载荷页挂载选项对话框顶部提示 —— 孵化进程 `ls /sdcard` 提示 No such file or directory 时 = 未勾选 --mount-external-* 挂载选项，需勾选 --mount-external-full 重新注入

## 🔜 下一轮建议（按优先级）

1. ~~进阶页备份管理~~ ✅ 已完成（v25）
2. ~~预设页卡片显示注入次数/时间~~ ✅ 已完成（v26）
3. ~~危险参数执行前二次确认弹窗加强~~ ✅ 已完成（v27）
4. ~~reboot 硬拦截~~ ✅ 已完成（v28）
5. ~~蓝色主题 + GROUPS 恢复~~ ✅ 已完成（v29）
6. 备份恢复时应用需保持卸载状态（恢复前自动卸载/安装提示）
7. 备份列表支持按大小/时间排序
8. 长按备份卡片 → 复制路径/查看详情

## 🔍 关键文件索引

| 文件 | 作用 |
|---|---|
| ZygoteFragment.java | 载荷页（最大文件） |
| PresetsFragment.java | 预设页 |
| AdvancedFragment.java | 进阶页（备份/恢复） |
| PayloadPresetManager.java | 多预设数据层 |
| PresetManagerBottomSheet.java | 预设管理弹窗 |
| GuideActivity.java | 使用说明 |
| AboutActivity.java | 关于（自定义） |
| BootReceiver.java / InjectJobService.java | 开机自启动 |
| HelpActivity.java | 日志页 + 导出 |
| ZygoteActivity.java | 主容器（4 Tab + 自定义菜单） |

## ⚠️ 安全提示

本工具为 CVE-2024-31317 安全研究用途，操作可能导致设备变砖/卡开机。仅供授权测试。