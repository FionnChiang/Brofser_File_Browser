package com.example.filebrowser;

import android.os.Bundle;

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
    private final List<String> imageList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        viewPager = findViewById(R.id.viewPager);

        String filePath = getIntent().getStringExtra("file_path");
        if (filePath == null) { finish(); return; }

        int startIndex = buildImageList(new File(filePath));

        ImagePagerAdapter adapter = new ImagePagerAdapter(imageList);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(startIndex, false);
        // 预加载左右各一页，滑动时无需等待
        viewPager.setOffscreenPageLimit(1);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateTitle(position);
            }
        });

        updateTitle(startIndex);
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
