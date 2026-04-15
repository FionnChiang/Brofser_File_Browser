package com.example.filebrowser;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageViewerActivity extends AppCompatActivity {

    private static final String[] IMAGE_EXT = {
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".heic", ".heif"
    };

    private ViewPager2 viewPager;
    private Toolbar toolbar;
    private final List<String> imageList = new ArrayList<>();
    private boolean uiVisible = true;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        viewPager = findViewById(R.id.viewPager);

        String filePath = getIntent().getStringExtra("file_path");
        if (filePath == null) { finish(); return; }

        int startIndex = buildImageList(new File(filePath));

        ImagePagerAdapter adapter = new ImagePagerAdapter(imageList);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(startIndex, false);
        viewPager.setOffscreenPageLimit(1);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTitle(position);
            }
        });

        updateTitle(startIndex);

        // 单击切换 UI 显示/隐藏，不影响翻页和缩放
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleUi();
                return true;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void toggleUi() {
        uiVisible = !uiVisible;
        if (uiVisible) {
            toolbar.setVisibility(View.VISIBLE);
            toolbar.animate().alpha(1f).translationY(0).setDuration(200).start();
        } else {
            toolbar.animate().alpha(0f).translationY(-toolbar.getHeight())
                    .setDuration(200)
                    .withEndAction(() -> toolbar.setVisibility(View.GONE))
                    .start();
        }
    }

    // ─── 构建同目录图片列表 ────────────────────────────────────────────────────

    private int buildImageList(File target) {
        int startIdx = 0;
        File parent = target.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            imageList.add(target.getAbsolutePath());
            return 0;
        }
        File[] files = parent.listFiles();
        if (files == null) { imageList.add(target.getAbsolutePath()); return 0; }

        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File f : sorted) {
            if (isImage(f)) {
                if (f.getAbsolutePath().equals(target.getAbsolutePath()))
                    startIdx = imageList.size();
                imageList.add(f.getAbsolutePath());
            }
        }
        if (imageList.isEmpty()) { imageList.add(target.getAbsolutePath()); }
        return startIdx;
    }

    private boolean isImage(File f) {
        if (!f.isFile()) return false;
        String name = f.getName().toLowerCase();
        for (String ext : IMAGE_EXT) if (name.endsWith(ext)) return true;
        return false;
    }

    // ─── 标题显示"文件名 (x/n)" ───────────────────────────────────────────────

    private void updateTitle(int index) {
        if (getSupportActionBar() == null || imageList.isEmpty()) return;
        String name  = new File(imageList.get(index)).getName();
        String title = imageList.size() > 1
                ? name + "  (" + (index + 1) + " / " + imageList.size() + ")"
                : name;
        getSupportActionBar().setTitle(title);
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
