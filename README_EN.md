# PoC-Deployer-System (Remake)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-9~13-green.svg)](https://developer.android.com)

> ⚠️ This is a **remake project**. The original project was made by [wqry085](https://github.com/wqry085/PoC-Deployer-System).
> This project keeps the core features and **adds new features and beautifies the UI**.
> I'm a newbie coder, and the project was completed with AI assistance. Please report any bugs to me.

English | [中文](./README.md)

A visualization tool based on CVE-2024-31317 for Zygote injection, integrating remote terminal, file transfer, and a brand-new MIUIx-style UI with multiple convenient features.

## ✨ New Features in This Remake

| Feature | Description |
|------|------|
| 🎨 **MIUIx-style UI** | Fully redesigned interface, rounded cards, tech-blue theme (#0054D6), light/dark modes |
| 📦 **Multi-preset management** | Save multiple payload presets, one-tap run/edit/import/export (JSON) |
| ⚡ **Preset injection stats** | Cards show injection count and last injection time |
| 🔄 **Boot auto-inject** | Auto-inject preset 15s after boot (with safety protection) |
| 💾 **Backup/Restore** | App data backup & restore (cppkg/repkg logic) with backup manager |
| 🛡️ **Reboot hard-block** | Reboot commands cause 100% bootloop, blocked at all injection entries |
| 🔍 **Dangerous op confirm** | Second confirmation + risk details for root/rm -rf etc. |
| 📖 **Guide page** | Built-in parameter specs, UID reference, troubleshooting tips |

## Quick Start

### Requirements
- Android 9-13, security patch before 2024.6
- Shizuku activated
- Disable vendor restrictions on special devices (MIUI/ColorOS/OriginOS)

### Usage
1. Install and activate Shizuku
2. Install app, grant Shizuku permission
3. Configure target parameters (command/UID/GID/SELinux context) on "Payload" tab
4. Tap "Execute" to inject, or "Save as Preset" for reuse
5. Reverse shell:
```bash
stty raw -echo; nc 127.0.0.1 8080; stty sane
```

## Interface (4 Tabs)

```
Payload | Presets | Auth | Advanced
```

## Features

| Feature | Description |
|------|------|
| Zygote Injection | via hidden_api_blacklist_exemptions |
| Remote Terminal | Full PTY, window resize support |
| App Data Transfer | 50-100 MB/s transfer speed |
| Access Control | UID whitelist |
| Backup/Restore | cppkg/repkg logic, backup to /sdcard/backup/ |

## Ports

| Port | Purpose | Authentication |
|------|------|------|
| 8080 | Local reverse shell | Local + UID whitelist |
| 8081 | Control interface | MD5 key |
| 56423 | File receiver | Local |

## Control Commands (Port 8081)
```
EXEC <cmd>       - Execute command
STATUS           - System status
POLICY_ADD <uid> - Add to whitelist
POLICY_LIST      - List whitelist
SEND_APP_DIR     - Send app directory
```

## Troubleshooting
```bash
# Check process
ps -A | grep zYg0te
# Check ports
netstat -tlnp | grep 56423
# View logs
logcat -s FolderReceiver:*
```

> 💡 Tip: If the injected process gets `No such file or directory` on `ls /sdcard`, open "Advanced" → "Zygote Config" and check `--mount-external-full`, then re-inject.

## Disclaimer
For security research only. Illegal use is prohibited. Users assume all responsibility. This tool involves Zygote injection and kernel-level operations which may brick the device or cause bootloop. Test only on authorized devices.

## Credits
- Original project: [wqry085/PoC-Deployer-System](https://github.com/wqry085/PoC-Deployer-System)
- Remake repo: [nianfan555/PoC-Deployer-System](https://github.com/nianfan555/PoC-Deployer-System)
- https://github.com/Webldix