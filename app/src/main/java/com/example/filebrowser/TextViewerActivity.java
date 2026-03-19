package com.example.filebrowser;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class TextViewerActivity extends AppCompatActivity {

    private static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB 限制
    private TextView tvContent;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private String filePath;
    private float currentTextSize = 14f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_viewer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvContent = findViewById(R.id.tvContent);
        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);

        filePath = getIntent().getStringExtra("file_path");
        if (filePath == null) {
            Toast.makeText(this, "文件路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File file = new File(filePath);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(file.getName());
        }

        loadTextFile(filePath);
    }

    private void loadTextFile(final String path) {
        progressBar.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.GONE);

        new Thread(() -> {
            final String content = readFile(path);
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (content != null) {
                    scrollView.setVisibility(View.VISIBLE);
                    tvContent.setText(content);
                } else {
                    Toast.makeText(TextViewerActivity.this, "无法读取文件", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        }).start();
    }

    private String readFile(String path) {
        File file = new File(path);

        if (file.length() > MAX_FILE_SIZE) {
            // 大文件只读取前 1MB
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[MAX_FILE_SIZE];
                int bytesRead = fis.read(buffer);
                String content = detectEncodingAndDecode(buffer, bytesRead);
                return content + "\n\n--- 文件过大，仅显示前 1MB 内容 ---";
            } catch (Exception e) {
                return null;
            }
        }

        // 尝试不同编码读取
        String[] encodings = {"UTF-8", "GBK", "GB2312", "GB18030", "BIG5", "ISO-8859-1"};
        for (String encoding : encodings) {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), Charset.forName(encoding)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                }
                String content = sb.toString();
                // 简单验证：如果包含太多乱码字符则跳过
                if (isValidContent(content)) {
                    return content;
                }
            } catch (Exception e) {
                // 尝试下一种编码
            }
        }
        return null;
    }

    private String detectEncodingAndDecode(byte[] buffer, int length) {
        // 检测 BOM
        if (length >= 3 && buffer[0] == (byte) 0xEF && buffer[1] == (byte) 0xBB && buffer[2] == (byte) 0xBF) {
            return new String(buffer, 3, length - 3, StandardCharsets.UTF_8);
        }
        // 默认 UTF-8
        try {
            return new String(buffer, 0, length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new String(buffer, 0, length, Charset.forName("GBK"));
        }
    }

    private boolean isValidContent(String content) {
        if (content == null || content.isEmpty()) return false;
        // 统计乱码字符比例
        int invalidCount = 0;
        for (char c : content.toCharArray()) {
            if (c == '\uFFFD') invalidCount++;
        }
        return invalidCount < content.length() * 0.1; // 乱码不超过 10%
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "字体变大").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, 2, 0, "字体变小").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            currentTextSize = Math.min(currentTextSize + 2f, 30f);
            tvContent.setTextSize(currentTextSize);
            return true;
        } else if (item.getItemId() == 2) {
            currentTextSize = Math.max(currentTextSize - 2f, 8f);
            tvContent.setTextSize(currentTextSize);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
