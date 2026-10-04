package com.wqry085.deployesystem;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * PayloadPresetManager
 *
 * 多 payload 预设管理：
 *  - 支持保存多个预设（名称 + payload 内容 + 参数摘要）
 *  - 增删改查、一键注入
 *  - 指定开机自启动预设（boot preset）
 *  - 开机后延迟注入（由 BootReceiver/InjectJobService 触发）
 *
 * 存储结构（SharedPreferences JSON）：
 *   preset_list = [{"id":"...","name":"...","payload":"...","summary":"...","ts":123}]
 *   boot_preset_id = "xxx"
 */
public class PayloadPresetManager {

    private static final String TAG = "PayloadPresetManager";

    // SharedPreferences 键
    public static final String PREFS_NAME = "payload_preset";
    private static final String KEY_PRESET_LIST = "preset_list";
    private static final String KEY_BOOT_PRESET_ID = "boot_preset_id";
    public static final String KEY_BOOT_AUTO_INJECT = "boot_auto_inject";
    public static final String KEY_LAST_INJECT_TIME = "last_inject_time";

    // 系统设置键（与 ZygoteFragment.runPayload 保持一致）
    private static final String SETTINGS_KEY = "hidden_api_blacklist_exemptions";
    private static final String SETTINGS_URI = "content://settings/global";

    // 设备本地配置文件
    private static final String CONFIG_FILE_PATH = "/data/local/tmp/只读配置.txt";

    // 注入后重置设置的延迟
    private static final int RESET_DELAY_MS = 200;

    // ==================== 预设数据模型 ====================

    public static class Preset {
        public String id;
        public String name;
        public String payload;
        public String summary;
        public long ts;

        // 完整参数（参考载荷页所有设置）
        public String command;
        public String ip;
        public String port;
        public String uid;
        public String gid;
        public String groups;
        public String seinfo;
        public String niceName;
        public String runtimeFlags;
        public String zyg1;
        public String zyg2;
        public String zyg3;
        public boolean bootEnabled;
        public java.util.Set<String> mountOptions;

        // 注入统计（Stellar 风格卡片展示）
        public int injectCount;
        public long lastInjectTime;

        public Preset(String id, String name, String payload, String summary) {
            this.id = id;
            this.name = name;
            this.payload = payload;
            this.summary = summary;
            this.ts = System.currentTimeMillis();
            this.command = "id";
            this.ip = "";
            this.port = "9981";
            this.uid = "1000";
            this.gid = "1000";
            this.groups = "";
            this.seinfo = "platform:privapp:targetSdkVersion=29:complete";
            this.niceName = "zYg0te";
            this.runtimeFlags = "43267";
            this.zyg1 = "5000";
            this.zyg2 = "3157";
            this.zyg3 = "4999";
            this.bootEnabled = false;
            this.mountOptions = new java.util.HashSet<>();
            this.injectCount = 0;
            this.lastInjectTime = 0;
        }

        public Preset(JSONObject obj) {
            this.id = obj.optString("id");
            this.name = obj.optString("name");
            this.payload = obj.optString("payload");
            this.summary = obj.optString("summary");
            this.ts = obj.optLong("ts", System.currentTimeMillis());
            this.command = obj.optString("command", "id");
            this.ip = obj.optString("ip", "");
            this.port = obj.optString("port", "9981");
            this.uid = obj.optString("uid", "1000");
            this.gid = obj.optString("gid", "1000");
            this.groups = obj.optString("groups", "");
            this.seinfo = obj.optString("seinfo", "platform:privapp:targetSdkVersion=29:complete");
            this.niceName = obj.optString("niceName", "zYg0te");
            this.runtimeFlags = obj.optString("runtimeFlags", "43267");
            this.zyg1 = obj.optString("zyg1", "5000");
            this.zyg2 = obj.optString("zyg2", "3157");
            this.zyg3 = obj.optString("zyg3", "4999");
            this.bootEnabled = obj.optBoolean("bootEnabled", false);
            this.injectCount = obj.optInt("injectCount", 0);
            this.lastInjectTime = obj.optLong("lastInjectTime", 0);
            this.mountOptions = new java.util.HashSet<>();
            org.json.JSONArray mo = obj.optJSONArray("mountOptions");
            if (mo != null) {
                for (int i = 0; i < mo.length(); i++) {
                    this.mountOptions.add(mo.optString(i));
                }
            }
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", id);
                obj.put("name", name);
                obj.put("payload", payload);
                obj.put("summary", summary);
                obj.put("ts", ts);
                obj.put("command", command);
                obj.put("ip", ip);
                obj.put("port", port);
                obj.put("uid", uid);
                obj.put("gid", gid);
                obj.put("groups", groups);
                obj.put("seinfo", seinfo);
                obj.put("niceName", niceName);
                obj.put("runtimeFlags", runtimeFlags);
                obj.put("zyg1", zyg1);
                obj.put("zyg2", zyg2);
                obj.put("zyg3", zyg3);
                obj.put("bootEnabled", bootEnabled);
                obj.put("injectCount", injectCount);
                obj.put("lastInjectTime", lastInjectTime);
                if (mountOptions != null) {
                    obj.put("mountOptions", new org.json.JSONArray(new java.util.ArrayList<>(mountOptions)));
                }
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    // ==================== 预设列表（多 payload） ====================

    public static List<Preset> getPresets(Context context) {
        List<Preset> list = new ArrayList<>();
        try {
            SharedPreferences prefs = getPrefs(context);
            String json = prefs.getString(KEY_PRESET_LIST, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(new Preset(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse presets", e);
        }
        return list;
    }

    private static void savePresets(Context context, List<Preset> presets) {
        try {
            JSONArray arr = new JSONArray();
            for (Preset p : presets) {
                arr.put(p.toJson());
            }
            getPrefs(context).edit().putString(KEY_PRESET_LIST, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save presets", e);
        }
    }

    public static void addPreset(Context context, String name, String payload, String summary) {
        List<Preset> list = getPresets(context);
        Preset p = new Preset(String.valueOf(System.currentTimeMillis()), name, payload, summary);
        list.add(0, p);
        savePresets(context, list);
    }

    public static void updatePreset(Context context, String id, String name, String payload, String summary) {
        List<Preset> list = getPresets(context);
        for (Preset p : list) {
            if (p.id.equals(id)) {
                p.name = name;
                p.payload = payload;
                p.summary = summary;
                break;
            }
        }
        savePresets(context, list);
    }

    public static void updatePresetBoot(Context context, String id, boolean enabled) {
        List<Preset> list = getPresets(context);
        for (Preset p : list) {
            if (p.id.equals(id)) {
                p.bootEnabled = enabled;
                break;
            }
        }
        savePresets(context, list);
        if (enabled) {
            setBootPresetId(context, id);
        }
    }

    public static void updatePresetFull(Context context, String id, Preset updated) {
        List<Preset> list = getPresets(context);
        for (Preset p : list) {
            if (p.id.equals(id)) {
                p.name = updated.name;
                p.payload = updated.payload;
                p.summary = updated.summary;
                p.command = updated.command;
                p.ip = updated.ip;
                p.port = updated.port;
                p.uid = updated.uid;
                p.gid = updated.gid;
                p.groups = updated.groups;
                p.seinfo = updated.seinfo;
                p.niceName = updated.niceName;
                p.runtimeFlags = updated.runtimeFlags;
                p.zyg1 = updated.zyg1;
                p.zyg2 = updated.zyg2;
                p.zyg3 = updated.zyg3;
                p.bootEnabled = updated.bootEnabled;
                p.mountOptions = updated.mountOptions;
                // 保留注入统计（编辑参数不清零）
                p.injectCount = updated.injectCount;
                p.lastInjectTime = updated.lastInjectTime;
                break;
            }
        }
        savePresets(context, list);
        if (updated.bootEnabled) {
            setBootPresetId(context, id);
        }
    }

    public static void deletePreset(Context context, String id) {
        List<Preset> list = getPresets(context);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
            }
        }
        savePresets(context, list);
        // 如果删除的是开机预设，清理
        if (id.equals(getBootPresetId(context))) {
            setBootPresetId(context, null);
        }
    }

    public static Preset getPresetById(Context context, String id) {
        if (id == null) return null;
        for (Preset p : getPresets(context)) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    // ==================== 开机预设 ====================

    public static String getBootPresetId(Context context) {
        return getPrefs(context).getString(KEY_BOOT_PRESET_ID, null);
    }

    public static void setBootPresetId(Context context, String id) {
        getPrefs(context).edit().putString(KEY_BOOT_PRESET_ID, id).apply();
    }

    // ==================== 开机自启动开关（兼容原有） ====================

    public static boolean isBootAutoInjectEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(KEY_BOOT_AUTO_INJECT, false);
    }

    public static void setBootAutoInjectEnabled(Context context, boolean enabled) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(KEY_BOOT_AUTO_INJECT, enabled);
        editor.apply();
    }

    public static boolean hasValidPayload(Context context) {
        // 存在有效开机预设
        String bootId = getBootPresetId(context);
        if (bootId != null) {
            Preset p = getPresetById(context, bootId);
            if (p != null && p.payload != null && !p.payload.trim().isEmpty()) {
                return true;
            }
        }
        // 兼容旧版：单预设
        String old = getPrefs(context).getString("preset_payload", "");
        return !old.trim().isEmpty();
    }

    // ==================== 注入 ====================

    public static String injectBootPreset(Context context) {
        String bootId = getBootPresetId(context);
        if (bootId != null) {
            Preset p = getPresetById(context, bootId);
            if (p != null && p.payload != null && !p.payload.trim().isEmpty()) {
                return injectPayload(context, p.payload);
            }
        }
        // 兼容旧版：单预设
        String old = getPrefs(context).getString("preset_payload", "");
        if (!old.trim().isEmpty()) {
            return injectPayload(context, old);
        }
        return "No boot preset configured.";
    }

    public static String injectPresetById(Context context, String id) {
        Preset p = getPresetById(context, id);
        if (p == null) {
            return "Preset not found: " + id;
        }
        String result = injectPayload(context, p.payload);
        if (result == null) {
            // 注入成功 → 更新统计
            p.injectCount += 1;
            p.lastInjectTime = System.currentTimeMillis();
            updatePresetStats(context, id, p.injectCount, p.lastInjectTime);
        }
        return result;
    }

    /** 更新预设注入统计（次数 + 最后注入时间） */
    public static void updatePresetStats(Context context, String id, int count, long lastTime) {
        List<Preset> list = getPresets(context);
        for (Preset p : list) {
            if (p.id.equals(id)) {
                p.injectCount = count;
                p.lastInjectTime = lastTime;
                break;
            }
        }
        savePresets(context, list);
    }

    public static String injectPayload(Context context, String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return "Payload is empty.";
        }

        // 🔒 兜底硬拦截：预设/开机自启动注入路径同样禁止重启类命令
        String rebootReason = PayloadGuard.findRebootReason(payload);
        if (rebootReason != null) {
            Log.w(TAG, "BLOCKED reboot command: " + rebootReason);
            return "BLOCKED: " + rebootReason + " (重启类命令 100% 卡开机，已阻止)";
        }

        try {
            shizukuExec("pm grant " + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS");
            shizukuExec("am force-stop com.android.settings");
            writePayloadToFile(payload);
            boolean success = writeToSettings(context, payload);
            shizukuExec("am start -n com.android.settings/.Settings");
            scheduleSettingsReset(context);

            getPrefs(context).edit().putLong(KEY_LAST_INJECT_TIME, System.currentTimeMillis()).apply();
            return success ? null : "Failed to write system settings.";
        } catch (Exception e) {
            Log.e(TAG, "Inject failed", e);
            return "Inject failed: " + e.getMessage();
        }
    }

    // ==================== 导入导出 ====================

    public static String exportPresets(Context context) {
        List<Preset> list = getPresets(context);
        try {
            JSONArray arr = new JSONArray();
            for (Preset p : list) {
                arr.put(p.toJson());
            }
            return arr.toString();
        } catch (Exception e) {
            Log.e(TAG, "Export failed", e);
            return "[]";
        }
    }

    public static int importPresets(Context context, String json) {
        try {
            JSONArray arr = new JSONArray(json);
            List<Preset> list = getPresets(context);
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                Preset p = new Preset(arr.getJSONObject(i));
                p.id = String.valueOf(System.currentTimeMillis()) + "_" + i;
                list.add(p);
                count++;
            }
            savePresets(context, list);
            return count;
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return 0;
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void writePayloadToFile(String payload) {
        try {
            String base64 = Base64.encodeToString(
                    payload.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);
            shizukuExec("echo '" + base64 + "' | base64 -d > " + CONFIG_FILE_PATH);
        } catch (Exception e) {
            Log.w(TAG, "Base64 write failed, trying fallback", e);
            writePayloadFallback(payload);
        }
    }

    private static void writePayloadFallback(String payload) {
        try {
            String delimiter = "EOF_" + System.currentTimeMillis();
            String command = String.format(
                    "cat > %s << '%s'\n%s\n%s",
                    CONFIG_FILE_PATH, delimiter, payload, delimiter);
            shizukuExec(command);
        } catch (Exception e) {
            Log.e(TAG, "Fallback write failed", e);
        }
    }

    private static boolean writeToSettings(Context context, String payload) {
        ContentValues values = new ContentValues();
        values.put(Settings.Global.NAME, SETTINGS_KEY);
        values.put(Settings.Global.VALUE, payload);
        try {
            context.getContentResolver().insert(Uri.parse(SETTINGS_URI), values);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write settings", e);
            return false;
        }
    }

    private static void scheduleSettingsReset(Context context) {
        new Thread(() -> {
            try {
                Thread.sleep(RESET_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ContentValues values = new ContentValues();
            values.put(Settings.Global.NAME, SETTINGS_KEY);
            values.put(Settings.Global.VALUE, "null");
            try {
                context.getContentResolver().insert(Uri.parse(SETTINGS_URI), values);
            } catch (Exception e) {
                Log.w(TAG, "Failed to reset settings", e);
            }
        }).start();
    }

    @NonNull
    private static String shizukuExec(@NonNull String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Shizuku.newProcess(new String[]{"sh"}, null, null);
            try (OutputStream os = process.getOutputStream()) {
                os.write((command + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Exit code: ").append(exitCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Shizuku exec failed: " + command, e);
            return e.toString();
        }
        return output.toString();
    }

    public static boolean isShizukuReady() {
        try {
            return Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }
}