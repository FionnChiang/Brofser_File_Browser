package com.example.filebrowser;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
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
import java.util.ArrayList;
import java.util.List;

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

    // 打开方式副标题 Views（顺序与 OPEN_WITH_CATS 一致）
    private static final String[] OPEN_WITH_CATS = {
            FileOpenPrefs.CAT_IMAGE, FileOpenPrefs.CAT_VIDEO,
            FileOpenPrefs.CAT_AUDIO, FileOpenPrefs.CAT_TEXT, FileOpenPrefs.CAT_OTHER
    };
    private static final int[] OPEN_WITH_ITEM_IDS = {
            R.id.itemOpenImage, R.id.itemOpenVideo,
            R.id.itemOpenAudio, R.id.itemOpenText, R.id.itemOpenOther
    };
    private static final int[] OPEN_WITH_DESC_IDS = {
            R.id.tvOpenImageDesc, R.id.tvOpenVideoDesc,
            R.id.tvOpenAudioDesc, R.id.tvOpenTextDesc, R.id.tvOpenOtherDesc
    };

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
        refreshOpenWithDescs();
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

        // 打开方式
        for (int i = 0; i < OPEN_WITH_CATS.length; i++) {
            final String cat = OPEN_WITH_CATS[i];
            findViewById(OPEN_WITH_ITEM_IDS[i]).setOnClickListener(v -> showOpenWithDialog(cat));
        }

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

    // ─── 打开方式 ──────────────────────────────────────────────────────────────

    private void refreshOpenWithDescs() {
        for (int i = 0; i < OPEN_WITH_CATS.length; i++) {
            TextView tv = findViewById(OPEN_WITH_DESC_IDS[i]);
            if (tv != null) tv.setText(FileOpenPrefs.getDisplayName(this, OPEN_WITH_CATS[i]));
        }
    }

    /** 查询能处理该 MIME 类型的系统应用 */
    private List<ResolveInfo> queryAppsForMime(String mime) {
        Intent q = new Intent(Intent.ACTION_VIEW).setType(mime);
        return getPackageManager().queryIntentActivities(
                q, PackageManager.MATCH_DEFAULT_ONLY);
    }

    private void showOpenWithDialog(String category) {
        String mime = FileOpenPrefs.mimeForCategory(category);

        // 构建选项列表
        List<String>   labels = new ArrayList<>();
        List<String>   keys   = new ArrayList<>();
        List<Drawable> icons  = new ArrayList<>();

        // "每次询问" 始终第一项
        labels.add("每次询问");
        keys.add(FileOpenPrefs.ASK);
        icons.add(getDrawable(android.R.drawable.ic_menu_help));

        // 内置播放器（非"其他"类型才有）
        if (!FileOpenPrefs.CAT_OTHER.equals(category)) {
            labels.add("内置播放器");
            keys.add(FileOpenPrefs.BUILTIN);
            icons.add(getDrawable(R.mipmap.ic_launcher));
        }

        // 系统应用
        PackageManager pm = getPackageManager();
        for (ResolveInfo ri : queryAppsForMime(mime)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            labels.add(ri.loadLabel(pm).toString());
            keys.add(ri.activityInfo.packageName);
            icons.add(ri.loadIcon(pm));
        }

        // 当前选中项
        String current = FileOpenPrefs.get(this, category);
        int checkedIdx = 0;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(current)) { checkedIdx = i; break; }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.select_dialog_singlechoice, labels) {
            @Override
            public View getView(int pos, View cv, android.view.ViewGroup p) {
                android.widget.CheckedTextView ctv =
                        (android.widget.CheckedTextView) super.getView(pos, cv, p);
                Drawable ic = icons.get(pos);
                if (ic != null) {
                    ic.setBounds(0, 0, 72, 72);
                    ctv.setCompoundDrawables(ic, null, null, null);
                    ctv.setCompoundDrawablePadding(16);
                }
                return ctv;
            }
        };

        final int[] sel = {checkedIdx};
        new AlertDialog.Builder(this)
                .setTitle("设置默认打开方式：" + FileOpenPrefs.labelForCategory(category))
                .setSingleChoiceItems(adapter, checkedIdx, (d, which) -> sel[0] = which)
                .setPositiveButton("确定", (d, w) -> {
                    FileOpenPrefs.set(this, category, keys.get(sel[0]));
                    refreshOpenWithDescs();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
