package com.wqry085.deployesystem;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * 关于页面（自定义圆角卡片版）
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageHelper.attachBaseContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // 二改者 GitHub
        findViewById(R.id.row_remaker).setOnClickListener(v -> openUrl("https://github.com/nianfan555"));

        // 原作者酷安
        findViewById(R.id.row_original).setOnClickListener(v -> openUrl("http://www.coolapk.com/u/21820733"));

        // 项目 GitHub（原项目）
        findViewById(R.id.row_github).setOnClickListener(v -> openUrl("https://github.com/wqry085/PoC-Deployer-System"));

        // 二改仓库 GitHub
        findViewById(R.id.row_github_remake).setOnClickListener(v -> openUrl("https://github.com/nianfan555/PoC-Deployer-System"));
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}