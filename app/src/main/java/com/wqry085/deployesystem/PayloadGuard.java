package com.wqry085.deployesystem;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PayloadGuard - 注入安全守卫
 *
 * 🔒 硬性拦截可能导致设备卡开机的危险命令，尤其是 reboot 系列。
 *
 * 原理（为什么 reboot 100% 卡开机）：
 * 注入链路中，系统全局设置 hidden_api_blacklist_exemptions 会在注入后约 200ms
 * 被异步重置为 null。若注入的命令包含 reboot 且设备先于重置完成重启，
 * 恶意 payload 将残留于全局设置 → 每次开机 Zygote 都会读取该参数 → bootloop。
 *
 * 因此 reboot / shutdown / poweroff / halt / fastboot 等重启类命令一律禁止执行，
 * 无论用户是否二次确认——这是硬性拦截，不是软警告。
 */
public final class PayloadGuard {

    private PayloadGuard() {}

    // 重启类命令：单词边界匹配
    // 命中示例：reboot / reboot -p / reboot recovery / svc power reboot / /system/bin/reboot
    //          shutdown -h now / poweroff / halt / fastboot reboot
    private static final Pattern REBOOT_PATTERN = Pattern.compile(
            "(?i)(^|[^a-zA-Z0-9_])(reboot|shutdown|poweroff|halt|fastboot)([^a-zA-Z0-9_]|$)");

    /**
     * 检测文本中是否包含重启类命令
     */
    public static boolean containsRebootCommand(String text) {
        if (text == null || text.isEmpty()) return false;
        return REBOOT_PATTERN.matcher(text).find();
    }

    /**
     * 返回命中的重启命令描述（用于告知用户具体拦截原因）
     *
     * @return 命中描述字符串，未命中返回 null
     */
    public static String findRebootReason(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = REBOOT_PATTERN.matcher(text);
        if (m.find()) {
            return "检测到重启类命令 \"" + m.group(2) + "\"";
        }
        return null;
    }

    /**
     * 统一的卡开机风险说明（供各入口弹窗复用）
     */
    public static String bootloopWarning() {
        return "重启类命令被硬性禁止：\n\n"
                + "注入后系统设置需约 200ms 才能重置为 null，若设备提前重启，\n"
                + "payload 会残留于 hidden_api_blacklist_exemptions，\n"
                + "导致 Zygote 启动参数被劫持，100% 卡开机（bootloop）。\n\n"
                + "如需重启设备，请手动长按电源键操作，切勿通过注入执行。";
    }
}
