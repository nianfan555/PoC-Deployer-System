package com.wqry085.deployesystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import android.graphics.Color;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.wqry085.deployesystem.databinding.ZygoteActivityBinding;
import com.wqry085.deployesystem.sockey.FolderReceiver;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import rikka.shizuku.Shizuku;

public class ZygoteActivity extends AppCompatActivity  {

    private ZygoteActivityBinding binding;
    private ViewPager2 viewPager;
    private static Process process;
    private static Thread thread;
    private TabLayout tabLayout;
    private static final String PREF_NAME = "settings";
    private static final String KEY_CHECK_UPDATE = "check_update_enabled";
    private android.widget.PopupWindow menuPopup;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageHelper.attachBaseContext(newBase));
    }
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.zygote_activity);
        ZygoteFragment.ShizukuExec(getApplicationInfo().nativeLibraryDir +
                                    "/libpolicy_daemon.so -d > /dev/null 2>&1 &");
        startLogMonitor(this); // App all Log
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        boolean shouldCheckUpdate = prefs.getBoolean(KEY_CHECK_UPDATE, true);
        if (shouldCheckUpdate) {
            checkForUpdates();
        }
        Toolbar tox = findViewById(R.id.tox);
        setSupportActionBar(tox);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.app_name));
        }

        // 使用自定义圆角菜单（替代系统三点菜单）
        setupCustomMenu(tox);

        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new ZygoteFragment();
                    case 1: return new PresetsFragment();
                    case 2: return new AuthorizationListFragment();
                    case 3: return new AdvancedFragment();
                    default: return new ZygoteFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 4;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(getString(R.string.zygote_activity_tab_payload)); break;
                case 1: tab.setText("预设"); break;
                case 2: tab.setText(getString(R.string.zygote_activity_tab_auth)); break;
                case 3: tab.setText("进阶"); break;
            }
        }).attach();

        FolderReceiver receiver = new FolderReceiver(ZygoteActivity.this);
        receiver.startReceiving();
        
        updateWhitelist(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLogMonitor();
    }
    
    // ... (rest of the methods remain the same)
    // 自定义圆角菜单（替代系统三点菜单）
    private void setupCustomMenu(Toolbar toolbar) {
        // 使用 XML 中声明的菜单按钮（位于 Toolbar 最右侧）
        android.widget.ImageButton menuBtn = findViewById(R.id.custom_menu_btn);
        if (menuBtn != null) {
            menuBtn.setOnClickListener(v -> showCustomMenu(menuBtn));
        }
    }

    private void showCustomMenu(View anchor) {
        if (menuPopup != null && menuPopup.isShowing()) {
            menuPopup.dismiss();
            return;
        }

        View content = getLayoutInflater().inflate(R.layout.popup_menu, null);
        menuPopup = new android.widget.PopupWindow(content,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        menuPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                android.graphics.Color.TRANSPARENT));
        menuPopup.setElevation(dp(8));
        menuPopup.setOutsideTouchable(true);
        menuPopup.setFocusable(true);

        // 菜单项点击
        content.findViewById(R.id.menu_zygote_log).setOnClickListener(v -> {
            menuPopup.dismiss();
            Intent xh = new Intent();
            xh.setClass(ZygoteActivity.this, HelpActivity.class);
            startActivity(xh);
        });
        content.findViewById(R.id.menu_guide).setOnClickListener(v -> {
            menuPopup.dismiss();
            Intent gi = new Intent();
            gi.setClass(ZygoteActivity.this, GuideActivity.class);
            startActivity(gi);
        });
        content.findViewById(R.id.menu_settings).setOnClickListener(v -> {
            menuPopup.dismiss();
            Intent xh = new Intent();
            xh.setClass(ZygoteActivity.this, SettingsActivity.class);
            startActivity(xh);
        });
        content.findViewById(R.id.menu_about).setOnClickListener(v -> {
            menuPopup.dismiss();
            Intent xh = new Intent();
            xh.setClass(ZygoteActivity.this, AboutActivity.class);
            startActivity(xh);
        });

        // 显示在 anchor 下方（右对齐）
        menuPopup.showAsDropDown(anchor, -dp(172), dp(4));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 已使用自定义菜单，返回 false 禁用系统菜单
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;

        // ✅ 新增：检查更新开关逻辑
        } else if (id == R.id.action_check_update) {
            boolean newState = !item.isChecked();
            item.setChecked(newState);

            SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(KEY_CHECK_UPDATE, newState);
            editor.apply();
            return true;

        } else if (id == R.id.action_zygote) {
            // 打开系统日志查看页
            Intent xh = new Intent();
            xh.setClass(ZygoteActivity.this, HelpActivity.class);
            startActivity(xh);
            return true;

        } else if (id == R.id.action_settings) {
            
          Intent xh = new Intent();
            xh.setClass(ZygoteActivity.this, SettingsActivity.class);
            startActivity(xh);
            return true;

        } else if (id == R.id.action_guide) {
            Intent gi = new Intent();
            gi.setClass(ZygoteActivity.this, GuideActivity.class);
            startActivity(gi);
            return true;

        } else if (id == R.id.action_about) {
            Intent xh = new Intent();
            xh.setClass(ZygoteActivity.this, AboutActivity.class);
            startActivity(xh);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public interface PortCheckCallback {
        void onResult(boolean isPortAvailable);
    }

    public static void checkPort13568Async(PortCheckCallback callback) {
        new Thread(() -> {
            boolean result = checkPort13568Sync();
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(result));
        }).start();
    }

    public static boolean checkPort13568Sync() {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", 13568), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }


void checkForUpdates() {
    new Thread(() -> {
        try {
            String jsonUrl = "https://codeberg.org/wqry085/PoC-Deployer-System/raw/branch/main/appdata.json";

            String jsonText = Jsoup.connect(jsonUrl)
                    .ignoreContentType(true)
                    .timeout(5000)
                    .get()
                    .body()
                    .text();

            JSONObject json = new JSONObject(jsonText);
            int latestCode = json.getInt("version_code");
            String latestVersion = json.getString("latest_version");
            String changelog = json.optString("update_log", getString(R.string.no_update_desc));
            String downloadLink = json.optString("update_url", "");
            boolean force = json.optBoolean("force_update", false);

            int currentCode = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionCode;

            File apkFile = new File(getExternalFilesDir(null), "update_" + latestVersion + ".apk");

            if (latestCode > currentCode) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (apkFile.exists()) {
                        showInstallDialog(latestVersion, changelog, apkFile, force, true);
                    } else {
                        showUpdateDialog(latestVersion, changelog, downloadLink, force);
                    }
                });
            } else {
                }

        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() ->
                    showToast(getString(R.string.check_update_failed) + e.getMessage()));
        }
    }).start();
}

private void showUpdateDialog(String version, String log, String url, boolean force) {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle(getString(R.string.found_new_version) + version);
    builder.setMessage(log);

    builder.setPositiveButton(getString(R.string.download_now), (dialog, which) -> {
        startDownload(url, version, force);
    });

    if (!force) {
        builder.setNegativeButton(getString(R.string.later), (dialog, which) -> dialog.dismiss());
    } else {
        builder.setCancelable(false);
    }

    builder.show();
}

private void startDownload(String url, String version, boolean force) {
    // MD3 风格进度对话框
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle(getString(R.string.downloading) + version);
    builder.setCancelable(false);

    LinearProgressIndicator progressIndicator = new LinearProgressIndicator(this);
    progressIndicator.setIndeterminate(false);
    progressIndicator.setProgress(0);
    progressIndicator.setMax(100);
    progressIndicator.setPadding(50, 40, 50, 40);
    builder.setView(progressIndicator);

    AlertDialog progressDialog = builder.show();

    File outputFile = new File(getExternalFilesDir(null), "update_" + version + ".apk");

    new Thread(() -> {
        try {
            URL downloadUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
            conn.connect();

            int total = conn.getContentLength();
            InputStream in = new BufferedInputStream(conn.getInputStream());
            OutputStream out = new FileOutputStream(outputFile);

            byte[] buffer = new byte[8192];
            int count;
            long downloaded = 0;
            while ((count = in.read(buffer)) != -1) {
                downloaded += count;
                out.write(buffer, 0, count);
                int progress = (int) ((downloaded * 100) / total);
                new Handler(Looper.getMainLooper()).post(() ->
                        progressIndicator.setProgress(progress, true));
            }

            out.flush();
            out.close();
            in.close();
            conn.disconnect();

            new Handler(Looper.getMainLooper()).post(() -> {
                progressDialog.dismiss();
                showInstallDialog(version, getString(R.string.download_complete), outputFile, force, false);
            });

        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(() -> {
                progressDialog.dismiss();
                showToast(getString(R.string.download_failed) + e.getMessage());
            });
        }
    }).start();
}

private void showInstallDialog(String version, String log, File apkFile, boolean force, boolean cached) {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
    builder.setTitle(cached ? getString(R.string.update_downloaded) + version : getString(R.string.download_complete_title));
    builder.setMessage(cached ? getString(R.string.install_ask) : log);

    builder.setPositiveButton(getString(R.string.install), (dialog, which) -> {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            showToast(getString(R.string.start_install_error) + e.getMessage());
        }
    });

    if (!force) {
        builder.setNegativeButton(getString(R.string.install_later), (dialog, which) -> dialog.dismiss());
    } else {
        builder.setCancelable(false);
    }

    builder.show();
}

    public static void updateWhitelist(Context context) {
        new Thread(() -> {
            SharedPreferences prefs = context.getSharedPreferences(AppDetailActivity.PREFS_NAME, MODE_PRIVATE);
            Set<String> authorizedPackages = prefs.getStringSet(AppDetailActivity.KEY_AUTHORIZED_PACKAGES, new HashSet<>());

            StringBuilder whitelistContent = new StringBuilder();
            PackageManager pm = context.getPackageManager();

            for (String packageName : authorizedPackages) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                    whitelistContent.append(appInfo.uid).append("\n");
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }

            try {
                File tempFile = new File(context.getCacheDir(), "whitelist.txt");
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(whitelistContent.toString().getBytes());
                }
                
                String command = "echo \"" + whitelistContent.toString() + "\" > /data/data/com.android.shell/zygote_term.tcp";
                ZygoteFragment.ShizukuExec(command);
                ZygoteFragment.ShizukuExec("chmod 777 /data/data/com.android.shell");
                tempFile.delete();

            } catch (IOException e) {
                e.printStackTrace();
            }

        }).start();
    }
    public static void startLogMonitor(android.content.Context context) {
    try {
        String pkgName = context.getPackageName();
        File logDir = new File(context.getExternalCacheDir(), "error_logs");
        if (!logDir.exists()) logDir.mkdirs();
        
        String logFile = new File(logDir, 
            new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date()) + ".txt").getAbsolutePath();
        
        // 改为记录Warning及以上级别，更容易捕获日志
        Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-c"});
        process.waitFor();
        process = Runtime.getRuntime().exec(new String[]{"logcat", "*:W", "-f", logFile});
        // 强制立即写入
        Runtime.getRuntime().exec(new String[]{"logcat", "-f", logFile, "-d", "*:W"});
        
    } catch (Exception e) {
        android.util.Log.e("LogMonitor", "Start failed", e);
    }
}
    // 取消监听
    public static void stopLogMonitor() {
        try {
            if (process != null) {
                process.destroy();
                process = null;
            }
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
            Runtime.getRuntime().exec(new String[]{"logcat", "-c"}).waitFor();
        } catch (Exception e) {
            android.util.Log.e("LogMonitor", "Stop failed", e);
        }
    }
}