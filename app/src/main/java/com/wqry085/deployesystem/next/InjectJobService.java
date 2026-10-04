package com.wqry085.deployesystem.next;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import com.wqry085.deployesystem.PayloadPresetManager;

/**
 * InjectJobService
 *
 * 由 BootReceiver 通过 JobScheduler 调度的延迟注入任务。
 * 在开机后约 1 分钟执行预置 payload 的自动注入，
 * 避免在 system_server / launcher 孵化阶段注入导致卡开机。
 */
public class InjectJobService extends JobService {

    private static final String TAG = "InjectJobService";

    // 等待 Shizuku 就绪的最大重试次数与间隔
    private static final int MAX_SHIZUKU_RETRY = 10;
    private static final long SHIZUKU_RETRY_INTERVAL_MS = 3000L;

    @Override
    public boolean onStartJob(final JobParameters params) {
        Log.i(TAG, "onStartJob: start auto inject...");

        // 在后台线程执行注入，避免阻塞 JobService 主线程
        new Thread(() -> {
            try {
                // 等待 Shizuku 就绪（开机初期 Shizuku 可能尚未启动完成）
                if (!PayloadPresetManager.isShizukuReady()) {
                    Log.i(TAG, "Shizuku not ready, waiting...");
                    for (int i = 0; i < MAX_SHIZUKU_RETRY; i++) {
                        Thread.sleep(SHIZUKU_RETRY_INTERVAL_MS);
                        if (PayloadPresetManager.isShizukuReady()) {
                            break;
                        }
                    }
                }

                // 再次确认开关与 payload 仍然有效（防止用户在等待期间修改）
                if (!PayloadPresetManager.isBootAutoInjectEnabled(this)
                        || !PayloadPresetManager.hasValidPayload(this)) {
                    Log.i(TAG, "Auto-inject disabled or payload empty, abort.");
                    jobFinished(params, false);
                    return;
                }

                // 执行注入（使用开机预设）
                String result = PayloadPresetManager.injectBootPreset(this);
                if (result == null) {
                    Log.i(TAG, "Boot auto-inject success.");
                } else {
                    Log.e(TAG, "Boot auto-inject failed: " + result);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Inject interrupted", e);
            } finally {
                // 任务完成，通知系统
                jobFinished(params, false);
            }
        }).start();

        return true; // 任务在后台线程运行，返回 true 表示任务仍在执行
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.w(TAG, "onStopJob: job stopped");
        // 返回 false 表示不重试
        return false;
    }
}