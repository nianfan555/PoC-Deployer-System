# PoC-Deployer-System（二改版）

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-9~13-green.svg)](https://developer.android.com)

> ⚠️ 本仓库为**二改项目**，原项目由 [wqry085](https://github.com/wqry085/PoC-Deployer-System) 制作。
> 本项目在保留原有核心功能的基础上，**新增了部分功能并美化了 UI 界面**。
> 本人为新人 coder，项目由 AI 辅助完成，出现任何 bug 可向本人反馈。

[English](./README_EN.md) | 中文

基于 CVE-2024-31317 的 Zygote 注入工具的可视化版本，集成远程终端和文件传输能力，并加入了全新的 MIUIx 风格界面与多种便捷功能。

## ✨ 二改新增功能

| 功能 | 说明 |
|------|------|
| 🎨 **MIUIx 风格 UI** | 全面重构界面，圆角卡片、科技蓝主题（#0054D6）、明暗双模式 |
| 📦 **多预设管理** | 支持保存多份 payload 预设，一键运行、编辑、导入导出（JSON） |
| ⚡ **预设注入统计** | 卡片显示注入次数与最后注入时间 |
| 🔄 **开机自启动** | 开机进入桌面后 15 秒自动注入指定预设（带安全保护） |
| 💾 **进阶备份/恢复** | 基于 cppkg/repkg 逻辑的应用数据备份与恢复（含备份管理） |
| 🛡️ **reboot 硬拦截** | 重启类命令 100% 卡开机，全部注入入口强制拦截并告知风险 |
| 🔍 **危险操作确认** | root/rm -rf 等危险命令执行前二次确认 + 风险明细 |
| 📖 **使用说明页** | 内置参数规范、UID 对照表、常见排错指引 |

## 快速开始

### 环境要求
- Android 9-13，安全补丁 2024.6 之前
- 已激活 Shizuku
- 特殊设备需关闭厂商限制（MIUI/ColorOS/OriginOS）

### 使用步骤
1. 安装 Shizuku 并激活
2. 安装应用，授予 Shizuku 权限
3. 在「载荷」页配置目标参数（命令/UID/GID/SELinux 上下文等）
4. 点击「执行命令」一键注入；或「保存为预设」复用
5. 远程终端（反弹 shell）：
```bash
stty raw -echo; nc 127.0.0.1 8080; stty sane
```

## 界面结构（4 Tab）

```
载荷 | 预设 | 授权 | 进阶
```

- **载荷**：状态总览、网络配置、注入命令、UID/GID/GROUPS、SELinux、Nice Name、挂载选项、服务控制、日志
- **预设**：网格卡片管理多份预设（运行/编辑/自启动/导出/导入/快捷方式）
- **授权**：应用白名单管理
- **进阶**：应用数据备份/恢复 + 备份管理

## 功能

| 功能 | 说明 |
|------|------|
| Zygote 注入 | 通过 hidden_api_blacklist_exemptions 实现 |
| 远程终端 | 完整 PTY，支持窗口调整 |
| 应用数据传输 | 速度 50-100 MB/s |
| 访问控制 | UID 白名单 |
| 备份/恢复 | cppkg/repkg 逻辑，备份到 /sdcard/backup/ |

## 端口说明

| 端口 | 用途 | 认证 |
|------|------|------|
| 8080 | 本地反弹shell | 本地UID白名单 |
| 8081 | 控制接口 | MD5密钥 |
| 56423 | 文件接收 | 本地 |

## 8081控制命令
```
EXEC <cmd>       - 执行命令
STATUS           - 系统状态
POLICY_ADD <uid> - 添加白名单
POLICY_LIST      - 查看白名单
SEND_APP_DIR     - 发送应用目录
```

## 故障排查
```bash
# 检查进程
ps -A | grep zYg0te
# 检查端口
netstat -tlnp | grep 56423
# 查看日志
logcat -s FolderReceiver:*
```

> 💡 排错：孵化进程执行 `ls /sdcard` 提示 `No such file or directory` → 在「载荷」页高级面板 →「Zygote 配置参数」勾选 `--mount-external-full` 后重新注入。

## 免责声明
仅用于安全研究，禁止非法用途。使用者承担全部责任。本工具涉及 Zygote 注入与内核级操作，可能导致设备变砖/卡开机，请仅在授权设备上测试。

## 致谢
- 原项目：[wqry085/PoC-Deployer-System](https://github.com/wqry085/PoC-Deployer-System)
- 二改仓库：[nianfan555/PoC-Deployer-System](https://github.com/nianfan555/PoC-Deployer-System)
- https://github.com/Webldix