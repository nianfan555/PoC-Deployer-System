package com.wqry085.deployesystem;

import android.content.Context;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * 使用说明页面
 */
public class GuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        fillSteps();
        fillParams();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void fillSteps() {
        fillStepItem(R.id.step1, "1", "连接 Shizuku：打开 Shizuku 管理器，通过 ADB 或 Root 启动服务，并在本应用内授权");
        fillStepItem(R.id.step2, "2", "配置参数：在「载荷」页设置注入命令、UID/GID、SELinux、Nice Name 等参数");
        fillStepItem(R.id.step3, "3", "执行注入：点击「执行命令」一键注入，或先「保存为预设」供后续快速使用");
        fillStepItem(R.id.step4, "4", "预设管理：在「预设」页可运行/编辑任意预设，开启「自启动」可在开机后自动注入");
    }

    private void fillParams() {
        fillParamItem(R.id.param1, "命令", "要注入执行的 shell 命令，如 id、whoami、getprop、su -c '...' 等");
        fillParamItem(R.id.param2, "UID", "注入进程的用户标识，只能填 Android 应用可用的 uid：\n· 1000 = system\n· 2000 = shell\n· 10xxx = u0_app（如 10001 = u0_a1）\n· 100xxx = u10_app（如 100001 = u10_a1）\n不能填 0、9997、1023 等辅助 uid，否则注入失败");
        fillParamItem(R.id.param3, "GID / GROUPS", "可任意填写，如 0（root）、9997（everybody）等。注意：GROUPS 在绝大多数设备上只能设置一个，注入逗号分隔的多个会被拦截，推荐只填一个");
        fillParamItem(R.id.param4, "SELinux", "SELinux 安全上下文策略（seinfo）。参考 /system/etc/selinux/plat_seapp_contexts 的系统孵化规范（因设备不同而定），例如：\n· platform:privapp:targetSdkVersion=29:complete\n· platform:app:targetSdkVersion=29:complete\n· untrusted_app:targetSdkVersion=29:complete\n非专业勿改");
        fillParamItem(R.id.param5, "Nice Name", "注入进程的显示名称（proc name）。参考 plat_seapp_contexts 规范：\n· 孵化 uid 2000 的进程时，nice name 必须为 com.android.shell\n· 其他可自定义，如 zYg0te");
        fillParamItem(R.id.param6, "Runtime Flags", "Zygote 运行时标志，控制隐藏 API 检查等。常用值：\n· 43267 = 默认\n· 16384 = 禁用隐藏 API 检查\n· 32768 = 安全模式");
        fillParamItem(R.id.param7, "Zygote 配置", "载荷页高级面板中的「Zygote 配置参数」用于选择挂载选项（--mount-external-* 系列等）。\n\n⚠️ 排错：如果注入的进程执行 ls /sdcard 提示 No such file or directory，说明 Zygote 配置参数未勾选 --mount 相关选项，导致新孵化进程没有挂载 /sdcard。\n\n解决办法：在载荷页展开「高级」面板 → 点「Zygote 配置参数」→ 勾选 --mount-external-full（或所需挂载选项）→ 重新执行注入。");
    }

    private void fillStepItem(int includeId, String num, String text) {
        ViewGroup item = findViewById(includeId);
        if (item == null) return;
        TextView stepNum = item.findViewById(R.id.step_num);
        TextView stepText = item.findViewById(R.id.step_text);
        if (stepNum != null) stepNum.setText(num);
        if (stepText != null) stepText.setText(text);
    }

    private void fillParamItem(int includeId, String name, String desc) {
        ViewGroup item = findViewById(includeId);
        if (item == null) return;
        TextView paramName = item.findViewById(R.id.param_name);
        TextView paramDesc = item.findViewById(R.id.param_desc);
        if (paramName != null) paramName.setText(name);
        if (paramDesc != null) paramDesc.setText(desc);
    }
}