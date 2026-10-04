package com.wqry085.deployesystem;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButton;
import com.wqry085.deployesystem.PayloadPresetManager.Preset;

import java.util.ArrayList;
import java.util.List;

/**
 * 预制 payload 页面
 * - 网格卡片（参考 Stellar 命令界面）
 * - 每个预设：独立自启动开关、运行、编辑（完整参数）
 */
public class PresetsFragment extends Fragment {

    private RecyclerView grid;
    private LinearLayout emptyLayout;
    private PresetAdapter adapter;
    private List<Preset> presets = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_presets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        grid = view.findViewById(R.id.preset_grid);
        emptyLayout = view.findViewById(R.id.preset_empty_layout);

        // 导入导出
        view.findViewById(R.id.btn_export_presets).setOnClickListener(v -> exportPresets());
        view.findViewById(R.id.btn_import_presets).setOnClickListener(v -> importPresets());

        int columns = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
        grid.setLayoutManager(new GridLayoutManager(getContext(), columns));

        adapter = new PresetAdapter();
        grid.setAdapter(adapter);

        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    void refresh() {
        presets = PayloadPresetManager.getPresets(requireContext());
        adapter.notifyDataSetChanged();
        boolean empty = presets.isEmpty();
        emptyLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        grid.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * 运行预设并弹出输出对话框（读取载荷页日志缓冲中的新增输出）
     */
    private void runPresetWithOutput(PayloadPresetManager.Preset p, int position) {
        // 🔒 硬性拦截：预设含重启类命令 → 100% 卡开机，直接阻止
        String rebootReason = PayloadGuard.findRebootReason(p.payload);
        if (rebootReason != null) {
            Toast.makeText(requireContext(), "已阻止: " + rebootReason + "（重启命令 100% 卡开机）", Toast.LENGTH_LONG).show();
            return;
        }

        // 记录运行前日志基线
        final String before = ZygoteFragment.sLastLog;
        String result = PayloadPresetManager.injectPresetById(requireContext(), p.id);
        if (result != null) {
            Toast.makeText(requireContext(), "注入失败: " + result, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(), "已注入: " + p.name + "，等待输出...", Toast.LENGTH_SHORT).show();
        refresh(); // 更新注入统计

        // 异步等待输出回传（注入后输出经 socket 回传日志）
        new Thread(() -> {
            String output = "";
            for (int i = 0; i < 30; i++) { // 最多等 6 秒
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
                String cur = ZygoteFragment.sLastLog;
                if (!cur.equals(before) && cur.length() > before.length()) {
                    output = cur.substring(Math.min(before.length(), cur.length()));
                    // 已收集到新增输出
                    if (output.contains(":") || output.trim().length() > 5) {
                        break;
                    }
                }
            }
            final String finalOutput = output;
            requireActivity().runOnUiThread(() -> showPresetOutputDialog(p.name, finalOutput));
        }).start();
    }

    /** 展示预设执行输出对话框（主题配色） */
    private void showPresetOutputDialog(String name, String output) {
        String content = output == null || output.trim().isEmpty()
                ? "(无输出回传)\n\n提示：请确认载荷页「启动服务」已开启，输出经 9981 端口回传；\n或切到「载荷」页展开日志查看。"
                : output.trim();

        // 构建对话框内容：滚动 TextView（跟随主题配色，非深色块）
        android.widget.ScrollView scroll = new android.widget.ScrollView(requireContext());
        android.widget.TextView tv = new android.widget.TextView(requireContext());
        tv.setText(content);
        tv.setTextSize(12f);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextColor(requireContext().getColor(R.color.md_theme_light_onSurface));
        tv.setPadding(20, 16, 20, 16);
        tv.setTextIsSelectable(false);
        scroll.addView(tv);
        scroll.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (getResources().getDisplayMetrics().density * 280)));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("执行输出 · " + name)
                .setView(scroll)
                .setPositiveButton("关闭", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void exportPresets() {
        String json = PayloadPresetManager.exportPresets(requireContext());
        if (json.equals("[]") || json.isEmpty()) {
            Toast.makeText(requireContext(), "暂无预设可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        // 写入下载目录
        try {
            java.io.File dir = new java.io.File(requireContext().getExternalFilesDir(null), "presets");
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = new java.io.File(dir, "presets_backup.json");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();

            // 分享
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                    requireContext(), requireContext().getPackageName() + ".provider", file));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "导出预设"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importPresets() {
        // 用文件选择器选择 JSON
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, 1001);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            try {
                android.net.Uri uri = data.getData();
                java.io.InputStream is = requireContext().getContentResolver().openInputStream(uri);
                byte[] buf = new byte[is.available()];
                is.read(buf);
                is.close();
                String json = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
                int count = PayloadPresetManager.importPresets(requireContext(), json);
                if (count > 0) {
                    Toast.makeText(requireContext(), "成功导入 " + count + " 个预设", Toast.LENGTH_SHORT).show();
                    refresh();
                } else {
                    Toast.makeText(requireContext(), "导入失败：文件格式错误", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "导入失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_preset_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Preset p = presets.get(position);
            holder.name.setText(p.name);
            holder.summary.setText(buildSummary(p));

            // 注入统计（Stellar 风格：次数 + 最后注入时间）
            if (p.injectCount > 0) {
                holder.statsRow.setVisibility(View.VISIBLE);
                holder.injectCount.setText("⚡ 注入 " + p.injectCount + " 次");
                holder.lastInject.setText(formatInjectTime(p.lastInjectTime));
            } else {
                holder.statsRow.setVisibility(View.GONE);
            }

            // 自启动开关
            holder.bootSwitch.setChecked(p.bootEnabled);
            holder.bootSwitch.setOnCheckedChangeListener((btn, checked) -> {
                p.bootEnabled = checked;
                PayloadPresetManager.updatePresetBoot(requireContext(), p.id, checked);
                if (checked) {
                    Toast.makeText(requireContext(), "已设为开机自启动: " + p.name, Toast.LENGTH_SHORT).show();
                }
            });

            // 运行
            holder.runBtn.setOnClickListener(v -> runPresetWithOutput(p, holder.getBindingAdapterPosition()));

            // 编辑
            holder.editBtn.setOnClickListener(v -> showEditDialog(p, position));

            // 长按卡片创建桌面快捷方式
            holder.itemView.setOnLongClickListener(v -> {
                createShortcut(p);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return presets.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            TextView summary;
            MaterialSwitch bootSwitch;
            MaterialButton runBtn;
            ImageButton editBtn;
            LinearLayout statsRow;
            TextView injectCount;
            TextView lastInject;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.card_icon);
                name = itemView.findViewById(R.id.card_name);
                summary = itemView.findViewById(R.id.card_summary);
                bootSwitch = itemView.findViewById(R.id.card_boot_switch);
                runBtn = itemView.findViewById(R.id.btn_run);
                editBtn = itemView.findViewById(R.id.btn_edit_card);
                statsRow = itemView.findViewById(R.id.card_stats_row);
                injectCount = itemView.findViewById(R.id.card_inject_count);
                lastInject = itemView.findViewById(R.id.card_last_inject);
            }
        }
    }

    // 创建桌面快捷方式
    private void createShortcut(Preset p) {
        try {
            android.content.Intent shortcutIntent = new Intent(requireContext(), com.wqry085.deployesystem.next.RunPayload.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra("payload", p.payload);

            android.content.pm.ShortcutManager shortcutManager =
                    requireContext().getSystemService(android.content.pm.ShortcutManager.class);
            if (shortcutManager == null) return;

            android.content.pm.ShortcutInfo shortcut = new android.content.pm.ShortcutInfo.Builder(requireContext(), "preset_" + p.id)
                    .setShortLabel(p.name)
                    .setLongLabel("运行预设: " + p.name)
                    .setIcon(android.graphics.drawable.Icon.createWithResource(requireContext(), R.drawable.ic_bolt))
                    .setIntent(shortcutIntent)
                    .build();

            shortcutManager.requestPinShortcut(shortcut, null);
            Toast.makeText(requireContext(), "已请求创建快捷方式: " + p.name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "创建快捷方式失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String formatInjectTime(long ts) {
        if (ts <= 0) return "";
        long diff = System.currentTimeMillis() - ts;
        if (diff < 60_000) return "刚刚";
        if (diff < 3_600_000) return (diff / 60_000) + " 分钟前";
        if (diff < 86_400_000) return (diff / 3_600_000) + " 小时前";
        if (diff < 7 * 86_400_000L) return (diff / 86_400_000) + " 天前";
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(ts));
    }

    private String buildSummary(Preset p) {
        StringBuilder sb = new StringBuilder();
        if (p.command != null && !p.command.isEmpty()) sb.append("cmd: ").append(p.command).append("\n");
        sb.append("uid: ").append(p.uid)
          .append("  gid: ").append(p.gid).append("\n");
        if (p.groups != null && !p.groups.isEmpty()) sb.append("groups: ").append(p.groups).append("\n");
        if (p.seinfo != null && !p.seinfo.isEmpty())
            sb.append("seinfo: ").append(p.seinfo.substring(0, Math.min(p.seinfo.length(), 24))).append("\n");
        if (p.niceName != null && !p.niceName.isEmpty()) sb.append("nice-name: ").append(p.niceName);
        return sb.toString().trim();
    }

    private void showEditDialog(Preset p, int position) {
        Context ctx = requireContext();
        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_preset_edit, null);

        final TextView tvCommand = dialogView.findViewById(R.id.tv_command_value);
        final TextView tvUid = dialogView.findViewById(R.id.tv_uid_value);
        final TextView tvGid = dialogView.findViewById(R.id.tv_gid_value);
        final TextView tvGroups = dialogView.findViewById(R.id.tv_groups_value);
        final TextView tvSeinfo = dialogView.findViewById(R.id.tv_seinfo_value);
        final TextView tvNiceName = dialogView.findViewById(R.id.tv_nice_name_value);
        final TextView tvMountOptions = dialogView.findViewById(R.id.tv_mount_options_value);

        tvCommand.setText(p.command);
        tvUid.setText(p.uid);
        tvGid.setText(p.gid);
        tvGroups.setText(p.groups);
        tvSeinfo.setText(p.seinfo);
        tvNiceName.setText(p.niceName);
        tvMountOptions.setText(formatMountOptions(p.mountOptions));

        // 各字段点击编辑
        dialogView.findViewById(R.id.row_edit_command).setOnClickListener(v -> editField("命令", tvCommand));
        dialogView.findViewById(R.id.row_edit_uid).setOnClickListener(v -> editField("UID", tvUid));
        dialogView.findViewById(R.id.row_edit_gid).setOnClickListener(v -> editField("GID", tvGid));
        dialogView.findViewById(R.id.row_edit_groups).setOnClickListener(v -> editField("GROUPS", tvGroups));
        dialogView.findViewById(R.id.row_edit_seinfo).setOnClickListener(v -> editField("SELinux", tvSeinfo));
        dialogView.findViewById(R.id.row_edit_nice_name).setOnClickListener(v -> editField("Nice Name", tvNiceName));
        dialogView.findViewById(R.id.row_edit_mount_options).setOnClickListener(v -> editMountOptions(p, tvMountOptions));

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("编辑预设: " + p.name)
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    p.command = tvCommand.getText().toString();
                    p.uid = tvUid.getText().toString();
                    p.gid = tvGid.getText().toString();
                    p.groups = tvGroups.getText().toString();
                    p.seinfo = tvSeinfo.getText().toString();
                    p.niceName = tvNiceName.getText().toString();
                    // 重新构建 payload
                    p.payload = buildPayloadFromParams(p);
                    p.summary = buildSummary(p);
                    PayloadPresetManager.updatePresetFull(ctx, p.id, p);
                    adapter.notifyItemChanged(position);
                    Toast.makeText(ctx, "预设已更新", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void editField(String title, TextView target) {
        Context ctx = requireContext();
        View dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_single_input, null);
        com.google.android.material.textfield.TextInputLayout til =
                dialogView.findViewById(R.id.text_input_layout);
        android.widget.EditText et = til.getEditText();
        til.setHint(title);
        et.setText(target.getText());
        et.setSelection(et.getText().length());

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton("确定", (dialog, which) -> {
                    if (et == null) return;
                    target.setText(et.getText().toString());
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String formatMountOptions(java.util.Set<String> mountOptions) {
        if (mountOptions == null || mountOptions.isEmpty()) return "未选择";
        String[] options = getResources().getStringArray(R.array.multi_select_options);
        String[] values = getResources().getStringArray(R.array.multi_select_values);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (mountOptions.contains(values[i])) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(options[i]);
            }
        }
        return sb.length() == 0 ? "未选择" : sb.toString();
    }

    private void editMountOptions(Preset p, TextView target) {
        Context ctx = requireContext();
        String[] options = getResources().getStringArray(R.array.multi_select_options);
        String[] values = getResources().getStringArray(R.array.multi_select_values);
        boolean[] checked = new boolean[options.length];
        java.util.Set<String> current = p.mountOptions != null ? p.mountOptions : new java.util.HashSet<>();
        for (int i = 0; i < values.length; i++) {
            checked[i] = current.contains(values[i]);
        }

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Zygote 配置参数")
                .setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("确定", (dialog, which) -> {
                    java.util.Set<String> newSet = new java.util.HashSet<>();
                    for (int i = 0; i < values.length; i++) {
                        if (checked[i]) newSet.add(values[i]);
                    }
                    p.mountOptions = newSet;
                    target.setText(formatMountOptions(newSet));
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String buildPayloadFromParams(Preset p) {
        // 简化 payload 构建：基于参数生成注入命令
        // 实际由 ZygoteFragment 的构建逻辑生成，这里保存时用构建器
        return p.payload != null ? p.payload : "id";
    }
}