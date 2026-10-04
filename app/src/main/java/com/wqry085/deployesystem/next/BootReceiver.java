package com.wqry085.deployesystem.next;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.wqry085.deployesystem.PayloadPresetManager;

/**
 * BootReceiver
 *
 * 开机自启动注入（带安全保护）：
 *  - 监听 BOOT_COMPLETED（开机完成）和 USER_PRESENT（进入桌面解锁）
 *  - 为降低"注入参数导致 system_server / launcher 孵化异常而卡开机"的风险，
 *    只在进入桌面后延迟 15 秒才执行注入。
 *  - 通过 JobScheduler 延迟调度，任务真正运行时已过桌面解锁 + 15s，
 *    此时系统孵化已稳定。
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    // 桌面出现后延迟注入时间（毫秒）：15 秒
    private static final long BOOT_DELAY_MS = 15 * 1000L;
    private static final int INJECT_JOB_ID = 0x31317; // CVE-2024-31317

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "onReceive, action=" + action);

        // 响应开机完成 / 解锁进入桌面
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean isPresent = Intent.ACTION_USER_PRESENT.equals(action);
        if (!isBoot && !isPresent) {
            return;
        }

        // 检查是否启用了开机自启动注入
        if (!PayloadPresetManager.isBootAutoInjectEnabled(context)) {
            Log.i(TAG, "Boot auto-inject disabled, skip.");
            return;
        }

        // 检查预置 payload 是否有效
        if (!PayloadPresetManager.hasValidPayload(context)) {
            Log.i(TAG, "Preset payload is empty, skip.");
            return;
        }

        // 桌面解锁后延迟 15 秒调度注入，避免影响 system_server / launcher 孵化
        scheduleInject(context);
    }

    /**
     * 通过 JobScheduler 调度延迟注入任务（15 秒后）
     */
    private void scheduleInject(Context context) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) {
            Log.e(TAG, "JobScheduler not available");
            return;
        }

        // 取消可能存在的旧任务，避免重复注入
        jobScheduler.cancel(INJECT_JOB_ID);

        ComponentName component = new ComponentName(context, InjectJobService.class);
        JobInfo jobInfo = new JobInfo.Builder(INJECT_JOB_ID, component)
                .setMinimumLatency(BOOT_DELAY_MS)                 // 至少延迟 15 秒
                .setOverrideDeadline(BOOT_DELAY_MS + 30 * 1000L)  // 最长等待 45 秒
                .setPersisted(false)                              // 不需要持久化（开机后调度）
                .build();

        int result = jobScheduler.schedule(jobInfo);
        Log.i(TAG, "Desktop entered. Inject job scheduled in " + (BOOT_DELAY_MS / 1000)
                + "s, result=" + (result == JobScheduler.RESULT_SUCCESS ? "SUCCESS" : "FAILED"));
    }
}