package com.example.filebrowser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_PICK_IMAGE = 10;
    private static final int REQ_CROP       = 11;

    private SharedPreferences prefs;

    private SwitchCompat switchHidden;
    private SwitchCompat switchFoldersFirst;
    private TextView     tvSortDesc;
    private TextView     tvBgStatus;
    private ImageView    ivBgPreview;
    private Button       btnClearBg;
    private SeekBar      seekBarDim;
    private TextView     tvDimValue;

    private static final String[] SORT_LABELS = {
            "文件名 A-Z", "文件名 Z-A",
            "修改时间 旧→新", "修改时间 新→旧",
            "大小 小→大", "大小 大→小"
    };
    private static final String[] SORT_VALUES = {
            "name_asc", "name_desc",
            "date_asc", "date_desc",
            "size_asc", "size_desc"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("设置");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        switchHidden      = findViewById(R.id.switchHiddenFiles);
        switchFoldersFirst= findViewById(R.id.switchFoldersFirst);
        tvSortDesc        = findViewById(R.id.tvSortOrderDesc);
        tvBgStatus        = findViewById(R.id.tvBgStatus);
        ivBgPreview       = findViewById(R.id.ivBgPreview);
        btnClearBg        = findViewById(R.id.btnClearBg);
        seekBarDim        = findViewById(R.id.seekBarDim);
        tvDimValue        = findViewById(R.id.tvDimValue);

        loadPrefs();
        setupListeners();
    }

    private void loadPrefs() {
        switchHidden.setChecked(prefs.getBoolean("show_hidden_files", false));
        switchFoldersFirst.setChecked(prefs.getBoolean("folders_first", true));

        String sortVal = prefs.getString("sort_order", "name_asc");
        for (int i = 0; i < SORT_VALUES.length; i++) {
            if (SORT_VALUES[i].equals(sortVal)) { tvSortDesc.setText(SORT_LABELS[i]); break; }
        }

        String bgPath = prefs.getString("background_image_path", null);
        updateBgPreview(bgPath);

        int opacity = prefs.getInt("background_opacity", 80);
        seekBarDim.setProgress(opacity);
        tvDimValue.setText(opacity + "%");
    }

    private void setupListeners() {
        switchHidden.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean("show_hidden_files", checked).apply());

        switchFoldersFirst.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean("folders_first", checked).apply());

        // 排序方式
        findViewById(R.id.itemSortOrder).setOnClickListener(v -> showSortDialog());

        // 背景图片
        findViewById(R.id.itemBackground).setOnClickListener(v -> pickImage());

        // 清除背景
        btnClearBg.setOnClickListener(v -> {
            prefs.edit().remove("background_image_path").apply();
            File f = new File(getFilesDir(), "background.jpg");
            if (f.exists()) f.delete();
            updateBgPreview(null);
            Toast.makeText(this, "背景已清除", Toast.LENGTH_SHORT).show();
        });

        // 不透明度
        seekBarDim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvDimValue.setText(progress + "%");
                if (fromUser) prefs.edit().putInt("background_opacity", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void showSortDialog() {
        String current = prefs.getString("sort_order", "name_asc");
        int checkedIdx = 0;
        for (int i = 0; i < SORT_VALUES.length; i++) {
            if (SORT_VALUES[i].equals(current)) { checkedIdx = i; break; }
        }
        new AlertDialog.Builder(this)
                .setTitle("排序方式")
                .setSingleChoiceItems(SORT_LABELS, checkedIdx, (dialog, which) -> {
                    prefs.edit().putString("sort_order", SORT_VALUES[which]).apply();
                    tvSortDesc.setText(SORT_LABELS[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "选择背景图片"), REQ_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_PICK_IMAGE) {
            Uri uri = data.getData();
            if (uri == null) return;
            Intent cropIntent = new Intent(this, CropActivity.class);
            cropIntent.putExtra(CropActivity.EXTRA_URI, uri.toString());
            int contentW = prefs.getInt("content_width", 0);
            int contentH = prefs.getInt("content_height", 0);
            if (contentW > 0 && contentH > 0) {
                cropIntent.putExtra(CropActivity.EXTRA_ASPECT_W, (float) contentW);
                cropIntent.putExtra(CropActivity.EXTRA_ASPECT_H, (float) contentH);
            }
            startActivityForResult(cropIntent, REQ_CROP);
        } else if (requestCode == REQ_CROP) {
            String path = data.getStringExtra(CropActivity.RESULT_PATH);
            if (path != null) {
                prefs.edit().putString("background_image_path", path).apply();
                updateBgPreview(path);
                Toast.makeText(this, "背景图片已设置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateBgPreview(String path) {
        if (path != null && new File(path).exists()) {
            tvBgStatus.setText("已设置");
            ivBgPreview.setVisibility(View.VISIBLE);
            btnClearBg.setVisibility(View.VISIBLE);
            Bitmap bm = BitmapFactory.decodeFile(path);
            if (bm != null) ivBgPreview.setImageBitmap(bm);
        } else {
            tvBgStatus.setText("未设置");
            ivBgPreview.setVisibility(View.GONE);
            btnClearBg.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}
