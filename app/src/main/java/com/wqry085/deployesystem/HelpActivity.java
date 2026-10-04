package com.wqry085.deployesystem;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.wqry085.deployesystem.next.LogView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class HelpActivity extends AppCompatActivity {

    private LogView logViewer;
    private Socket socket;
    private PrintWriter out;
    private Thread logThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        logViewer = findViewById(R.id.zygotelog);

        // 导出日志按钮
        findViewById(R.id.btn_export_log).setOnClickListener(v -> exportLog());

        startLogging();
    }

    private void exportLog() {
        try {
            // 抓取 logcat
            Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "*:W"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            // 保存到下载目录
            java.io.File dir = getExternalFilesDir(null);
            java.io.File file = new java.io.File(dir, "zygote_log_" + System.currentTimeMillis() + ".txt");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();

            // 分享
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".provider", file));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "导出日志"));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "导出失败: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void startLogging() {
        logThread = new Thread(() -> {
            try {
                socket = new Socket("localhost", 13568);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // First, dump the existing logs
                out.println("DUMP_LOGS");

                String line;
                while (!Thread.currentThread().isInterrupted() && (line = in.readLine()) != null) {
                    final String logLine = line;
                    runOnUiThread(() -> logViewer.appendLog(logLine));
                }
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    final String errorMessage = "Error connecting to log server: " + e.getMessage();
                    runOnUiThread(() -> logViewer.appendLog(errorMessage));
                }
            } finally {
                cleanup();
            }
        });
        logThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (logThread != null) {
            logThread.interrupt();
        }
    }

    private void cleanup() {
        try {
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Log or ignore
        }
    }
}