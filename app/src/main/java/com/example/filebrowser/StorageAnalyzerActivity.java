package com.example.filebrowser;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

public class StorageAnalyzerActivity extends AppCompatActivity {

    private static final int TAB_OVERVIEW   = 0;
    private static final int TAB_DUPLICATES = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Views
    private FrameLayout contentFrame;
    private View        loadingView;
    private ScrollView  overviewView;
    private View        duplicatesView;
    private TextView    tvLoadingStatus;
    private TextView    tvTotal, tvUsed, tvFree;
    private DonutChartView donutChart;
    private LinearLayout legendLayout;
    private LinearLayout recycleBinRow;
    private TextView    tvRecycleBinSize;
    private RecyclerView dupRecycler;

    // Tabs
    private TextView tabOverview, tabDuplicates;
    private int currentTab = TAB_OVERVIEW;

    private volatile boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildLayout();

        StorageStats stats = StorageStats.current;
        if (stats != null && stats.complete) {
            showDashboard(stats);
        } else if (stats != null && stats.analyzing) {
            showLoading();
            // Already running – just wait for broadcast
            pollForCompletion();
        } else {
            showLoading();
            startAnalysis();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    // ─── Layout ──────────────────────────────────────────────────────────────

    private void buildLayout() {
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F5F5);

        // Toolbar
        Toolbar toolbar = new Toolbar(this);
        toolbar.setBackgroundColor(0xFF3F51B5);
        toolbar.setTitleTextColor(0xFFFFFFFF);
        toolbar.setTitle("存储分析");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(56 * dp)));

        // Tab strip
        LinearLayout tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setBackgroundColor(0xFF3F51B5);

        tabOverview = makeTabText("文件分布", dp);
        tabDuplicates = makeTabText("重复文件", dp);
        tabStrip.addView(tabOverview, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabStrip.addView(tabDuplicates, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        tabOverview.setOnClickListener(v -> switchTab(TAB_OVERVIEW));
        tabDuplicates.setOnClickListener(v -> switchTab(TAB_DUPLICATES));
        root.addView(tabStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(44 * dp)));

        // Content frame
        contentFrame = new FrameLayout(this);
        root.addView(contentFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Loading view
        loadingView = buildLoadingView(dp);
        contentFrame.addView(loadingView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Overview view (built now, populated later)
        overviewView = buildOverviewView(dp);
        contentFrame.addView(overviewView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overviewView.setVisibility(View.GONE);

        // Duplicates view (built now, populated later)
        duplicatesView = buildDuplicatesView(dp);
        contentFrame.addView(duplicatesView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        duplicatesView.setVisibility(View.GONE);

        setContentView(root);
        updateTabIndicator();
    }

    private TextView makeTabText(String label, float dp) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, (int)(3 * dp));
        return tv;
    }

    private View buildLoadingView(float dp) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);
        ll.setBackgroundColor(0xFFF5F5F5);

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                (int)(48 * dp), (int)(48 * dp));
        pbLp.bottomMargin = (int)(16 * dp);
        ll.addView(pb, pbLp);

        tvLoadingStatus = new TextView(this);
        tvLoadingStatus.setText("正在分析存储空间…");
        tvLoadingStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvLoadingStatus.setTextColor(0xFF757575);
        tvLoadingStatus.setGravity(Gravity.CENTER);
        ll.addView(tvLoadingStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return ll;
    }

    private ScrollView buildOverviewView(float dp) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF5F5F5);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding((int)(16*dp), (int)(16*dp), (int)(16*dp), (int)(24*dp));

        // Storage card
        LinearLayout storageCard = makeCard(dp);
        tvTotal = makeInfoRow(storageCard, "总容量", "—", dp);
        addDivider(storageCard, dp);
        tvUsed  = makeInfoRow(storageCard, "已使用", "—", dp);
        addDivider(storageCard, dp);
        tvFree  = makeInfoRow(storageCard, "可用空间", "—", dp);
        ll.addView(storageCard, cardLp(dp));

        // Donut chart card
        LinearLayout chartCard = makeCard(dp);
        chartCard.setOrientation(LinearLayout.VERTICAL);
        chartCard.setGravity(Gravity.CENTER_HORIZONTAL);

        donutChart = new DonutChartView(this);
        int chartSize = (int)(240 * dp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(chartSize, chartSize);
        clp.topMargin = (int)(8 * dp);
        chartCard.addView(donutChart, clp);

        legendLayout = new LinearLayout(this);
        legendLayout.setOrientation(LinearLayout.VERTICAL);
        legendLayout.setPadding((int)(12*dp), (int)(8*dp), (int)(12*dp), (int)(8*dp));
        chartCard.addView(legendLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ll.addView(chartCard, cardLp(dp));

        // Recycle bin card
        recycleBinRow = makeCard(dp);
        recycleBinRow.setOrientation(LinearLayout.HORIZONTAL);
        recycleBinRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout rbInfo = new LinearLayout(this);
        rbInfo.setOrientation(LinearLayout.VERTICAL);
        rbInfo.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView rbTitle = new TextView(this);
        rbTitle.setText("回收站");
        rbTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rbTitle.setTextColor(0xFF212121);
        rbInfo.addView(rbTitle);

        tvRecycleBinSize = new TextView(this);
        tvRecycleBinSize.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvRecycleBinSize.setTextColor(0xFF757575);
        tvRecycleBinSize.setText("计算中…");
        rbInfo.addView(tvRecycleBinSize);

        recycleBinRow.addView(rbInfo);

        Button btnClear = new Button(this);
        btnClear.setText("清空回收站");
        btnClear.setTextColor(0xFFFFFFFF);
        btnClear.setBackgroundColor(0xFFEF5350);
        btnClear.setOnClickListener(v -> confirmClearRecycleBin());
        recycleBinRow.addView(btnClear);

        ll.addView(recycleBinRow, cardLp(dp));

        sv.addView(ll, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private View buildDuplicatesView(float dp) {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);

        dupRecycler = new RecyclerView(this);
        dupRecycler.setLayoutManager(new LinearLayoutManager(this));
        dupRecycler.setBackgroundColor(0xFFF5F5F5);
        ll.addView(dupRecycler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return ll;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private LinearLayout makeCard(float dp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding((int)(16*dp), (int)(12*dp), (int)(16*dp), (int)(12*dp));
        return card;
    }

    private LinearLayout.LayoutParams cardLp(float dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(12 * dp);
        return lp;
    }

    private TextView makeInfoRow(LinearLayout parent, String label, String value, float dp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int)(8*dp), 0, (int)(8*dp));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvLabel.setTextColor(0xFF212121);
        row.addView(tvLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvVal.setTextColor(0xFF757575);
        row.addView(tvVal);

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tvVal;
    }

    private void addDivider(LinearLayout parent, float dp) {
        View v = new View(this);
        v.setBackgroundColor(0xFFE0E0E0);
        parent.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    // ─── Tab ─────────────────────────────────────────────────────────────────

    private void switchTab(int tab) {
        currentTab = tab;
        updateTabIndicator();
        if (StorageStats.current == null || !StorageStats.current.complete) return;
        overviewView.setVisibility(tab == TAB_OVERVIEW ? View.VISIBLE : View.GONE);
        duplicatesView.setVisibility(tab == TAB_DUPLICATES ? View.VISIBLE : View.GONE);
    }

    private void updateTabIndicator() {
        float dp = getResources().getDisplayMetrics().density;
        tabOverview.setPadding(0, 0, 0,
                currentTab == TAB_OVERVIEW ? (int)(3*dp) : 0);
        tabOverview.setBackgroundColor(
                currentTab == TAB_OVERVIEW ? 0xFF5C6BC0 : Color.TRANSPARENT);
        tabDuplicates.setPadding(0, 0, 0,
                currentTab == TAB_DUPLICATES ? (int)(3*dp) : 0);
        tabDuplicates.setBackgroundColor(
                currentTab == TAB_DUPLICATES ? 0xFF5C6BC0 : Color.TRANSPARENT);
    }

    // ─── State transitions ────────────────────────────────────────────────────

    private void showLoading() {
        loadingView.setVisibility(View.VISIBLE);
        overviewView.setVisibility(View.GONE);
        duplicatesView.setVisibility(View.GONE);
    }

    private void showDashboard(StorageStats stats) {
        loadingView.setVisibility(View.GONE);
        populateOverview(stats);
        populateDuplicates(stats);
        overviewView.setVisibility(currentTab == TAB_OVERVIEW ? View.VISIBLE : View.GONE);
        duplicatesView.setVisibility(currentTab == TAB_DUPLICATES ? View.VISIBLE : View.GONE);
    }

    // ─── Populate ─────────────────────────────────────────────────────────────

    private void populateOverview(StorageStats stats) {
        tvTotal.setText(StorageStats.formatSize(stats.totalBytes));
        tvUsed.setText(StorageStats.formatSize(stats.usedBytes));
        tvFree.setText(StorageStats.formatSize(stats.freeBytes));

        // Donut chart
        int n = StorageStats.CATEGORIES.length;
        long[] sizes = new long[n];
        for (int i = 0; i < n; i++) {
            Long s = stats.catSizes.get(StorageStats.CATEGORIES[i]);
            sizes[i] = s != null ? s : 0;
        }
        donutChart.setData(StorageStats.CATEGORIES, sizes, StorageStats.CAT_COLORS);
        donutChart.setCenterText(StorageStats.formatSize(stats.usedBytes), "已使用");
        donutChart.setOnSegmentClickListener((idx, label) -> openCategoryList(label));

        // Legend
        legendLayout.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < n; i++) {
            if (sizes[i] == 0) continue;
            addLegendRow(legendLayout, StorageStats.CATEGORIES[i],
                    sizes[i], StorageStats.CAT_COLORS[i], dp);
        }

        // Recycle bin size
        new Thread(() -> {
            List<RecycleBin.Item> items = RecycleBin.getItems(this);
            long sz = 0;
            for (RecycleBin.Item it : items) {
                File f = new File(RecycleBin.getBinDir(this), it.id);
                sz += dirSize(f);
            }
            final long finalSz = sz;
            final int count = items.size();
            handler.post(() -> tvRecycleBinSize.setText(
                    count + " 项，占用 " + StorageStats.formatSize(finalSz)));
        }).start();
    }

    private void addLegendRow(LinearLayout parent, String label,
                               long size, int color, float dp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int)(4*dp), 0, (int)(4*dp));
        row.setBackground(getDrawable(android.R.drawable.list_selector_background));

        View dot = new View(this);
        dot.setBackgroundColor(color);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                (int)(12*dp), (int)(12*dp));
        dotLp.setMarginEnd((int)(8*dp));
        row.addView(dot, dotLp);

        TextView tvLbl = new TextView(this);
        tvLbl.setText(label);
        tvLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvLbl.setTextColor(0xFF212121);
        row.addView(tvLbl, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView tvSz = new TextView(this);
        tvSz.setText(StorageStats.formatSize(size));
        tvSz.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvSz.setTextColor(0xFF757575);
        row.addView(tvSz);

        row.setOnClickListener(v -> openCategoryList(label));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void openCategoryList(String category) {
        Intent intent = new Intent(this, TypeFileListActivity.class);
        intent.putExtra(TypeFileListActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
    }

    private void populateDuplicates(StorageStats stats) {
        dupRecycler.setAdapter(new DupAdapter(stats.duplicateGroups));
    }

    // ─── Recycle bin ─────────────────────────────────────────────────────────

    private void confirmClearRecycleBin() {
        List<RecycleBin.Item> items = RecycleBin.getItems(this);
        if (items.isEmpty()) {
            Toast.makeText(this, "回收站已是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空回收站")
                .setMessage("永久删除回收站中全部 " + items.size() + " 项？此操作不可恢复。")
                .setPositiveButton("清空", (d, w) -> doClearRecycleBin())
                .setNegativeButton("取消", null)
                .show();
    }

    private void doClearRecycleBin() {
        new Thread(() -> {
            List<RecycleBin.Item> items = RecycleBin.getItems(this);
            long freed = 0;
            for (RecycleBin.Item item : items) {
                File f = new File(RecycleBin.getBinDir(this), item.id);
                freed += dirSize(f);
                RecycleBin.deletePermanently(item, this);
            }
            final long finalFreed = freed;
            handler.post(() -> {
                tvRecycleBinSize.setText("0 项，占用 0 B");
                Toast.makeText(this,
                        "已释放 " + StorageStats.formatSize(finalFreed),
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // ─── Analysis ─────────────────────────────────────────────────────────────

    private void startAnalysis() {
        StorageStats stats = new StorageStats();
        stats.analyzing = true;
        StorageStats.current = stats;

        new Thread(() -> {
            try {
                runAnalysis(stats);
            } catch (Exception e) {
                stats.progressText = "分析出错：" + e.getMessage();
            }
            stats.analyzing = false;
            stats.complete  = true;
            handler.post(() -> {
                if (!destroyed) showDashboard(stats);
                Toast.makeText(getApplicationContext(),
                        "存储分析完成", Toast.LENGTH_SHORT).show();
            });
        }).start();

        // Periodically update loading text
        pollForCompletion();
    }

    private void pollForCompletion() {
        handler.postDelayed(() -> {
            if (destroyed) return;
            StorageStats s = StorageStats.current;
            if (s != null && s.analyzing) {
                if (tvLoadingStatus != null)
                    tvLoadingStatus.setText(s.progressText);
                pollForCompletion();
            }
        }, 300);
    }

    private void runAnalysis(StorageStats stats) {
        // Initialize category maps
        for (String cat : StorageStats.CATEGORIES) {
            stats.catSizes.put(cat, 0L);
            stats.catFiles.put(cat, new ArrayList<>());
        }

        // Storage space info
        File root = Environment.getExternalStorageDirectory();
        StatFs sf = new StatFs(root.getAbsolutePath());
        stats.totalBytes = sf.getTotalBytes();
        stats.freeBytes  = sf.getAvailableBytes();
        stats.usedBytes  = stats.totalBytes - stats.freeBytes;

        // Scan files
        Map<Long, List<File>> sizeMap = new HashMap<>(); // for duplicate detection
        scanDir(root, stats, sizeMap);

        // Duplicate detection: group files with same size, confirm by CRC32 of first 128KB
        findDuplicates(sizeMap, stats);
    }

    private void scanDir(File dir, StorageStats stats, Map<Long, List<File>> sizeMap) {
        if (dir == null || !dir.isDirectory() || !dir.canRead()) return;
        // Skip system dirs
        String path = dir.getAbsolutePath();
        if (path.equals("/proc") || path.equals("/sys") || path.equals("/dev")) return;

        File[] children = dir.listFiles();
        if (children == null) return;

        for (File f : children) {
            if (f.isDirectory()) {
                scanDir(f, stats, sizeMap);
            } else if (f.isFile() && f.canRead()) {
                String cat = StorageStats.categoryForFile(f);
                long size = f.length();
                stats.catSizes.put(cat, stats.catSizes.get(cat) + size);
                stats.catFiles.get(cat).add(f);

                // Collect for duplicate detection (only files >= 100KB)
                if (size >= 100 * 1024) {
                    if (!sizeMap.containsKey(size)) sizeMap.put(size, new ArrayList<>());
                    sizeMap.get(size).add(f);
                }
                stats.progressText = "扫描：" + f.getName();
            }
        }
    }

    private void findDuplicates(Map<Long, List<File>> sizeMap, StorageStats stats) {
        stats.progressText = "检测重复文件…";
        List<List<File>> groups = new ArrayList<>();

        for (Map.Entry<Long, List<File>> entry : sizeMap.entrySet()) {
            List<File> candidates = entry.getValue();
            if (candidates.size() < 2) continue;

            // Group by CRC32 of first 128KB
            Map<Long, List<File>> crcGroups = new HashMap<>();
            for (File f : candidates) {
                long crc = quickCrc(f);
                if (crc < 0) continue;
                if (!crcGroups.containsKey(crc)) crcGroups.put(crc, new ArrayList<>());
                crcGroups.get(crc).add(f);
            }
            for (List<File> g : crcGroups.values()) {
                if (g.size() >= 2) {
                    // Sort by size desc (all same size, so sort by name)
                    Collections.sort(g, (a, b) -> a.getName().compareTo(b.getName()));
                    groups.add(g);
                }
            }
        }

        // Sort groups by size * count (largest waste first)
        groups.sort((a, b) -> Long.compare(
                (long) b.get(0).length() * b.size(),
                (long) a.get(0).length() * a.size()));
        stats.duplicateGroups = groups;
    }

    private long quickCrc(File f) {
        try (FileInputStream fis = new FileInputStream(f);
             FileChannel ch = fis.getChannel()) {
            ByteBuffer buf = ByteBuffer.allocate(131072); // 128KB
            int read = ch.read(buf);
            if (read <= 0) return -1;
            CRC32 crc = new CRC32();
            crc.update(buf.array(), 0, read);
            return crc.getValue();
        } catch (Exception e) { return -1; }
    }

    private long dirSize(File f) {
        if (f == null) return 0;
        if (f.isFile()) return f.length();
        long total = 0;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) total += dirSize(c);
        return total;
    }

    // ─── Duplicate adapter ────────────────────────────────────────────────────

    private class DupAdapter extends RecyclerView.Adapter<DupAdapter.VH> {
        private final List<List<File>> groups;
        private final List<boolean[]>  selected = new ArrayList<>();
        private final Handler h = new Handler(Looper.getMainLooper());

        DupAdapter(List<List<File>> groups) {
            this.groups = groups;
            for (List<File> g : groups) {
                boolean[] sel = new boolean[g.size()];
                selected.add(sel);
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            float dp = parent.getContext().getResources().getDisplayMetrics().density;
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding((int)(12*dp), (int)(8*dp), (int)(12*dp), (int)(8*dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins((int)(8*dp), (int)(4*dp), (int)(8*dp), (int)(4*dp));
            card.setLayoutParams(lp);
            return new VH(card);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            List<File> group = groups.get(pos);
            boolean[] sel   = selected.get(pos);
            float dp = holder.itemView.getContext().getResources().getDisplayMetrics().density;

            LinearLayout card = (LinearLayout) holder.itemView;
            card.removeAllViews();

            // Group header
            TextView header = new TextView(card.getContext());
            header.setText("重复组（" + group.size() + " 个文件 · "
                    + StorageStats.formatSize(group.get(0).length()) + " 每个）");
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setTextColor(0xFF757575);
            header.setPadding(0, 0, 0, (int)(6*dp));
            card.addView(header);

            for (int i = 0; i < group.size(); i++) {
                final int idx = i;
                File f = group.get(i);
                LinearLayout row = new LinearLayout(card.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, (int)(4*dp), 0, (int)(4*dp));

                android.widget.CheckBox cb = new android.widget.CheckBox(card.getContext());
                cb.setChecked(sel[idx]);
                cb.setOnCheckedChangeListener((btn, checked) -> sel[idx] = checked);
                row.addView(cb, new LinearLayout.LayoutParams(
                        (int)(36*dp), (int)(36*dp)));

                ImageView thumb = new ImageView(card.getContext());
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setBackgroundColor(0xFFEEEEEE);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                        (int)(48*dp), (int)(48*dp));
                tlp.setMarginStart((int)(6*dp));
                tlp.setMarginEnd((int)(10*dp));
                row.addView(thumb, tlp);
                // Async thumbnail
                final String ext = f.getName().toLowerCase();
                final boolean isImg = ext.endsWith(".jpg") || ext.endsWith(".jpeg") ||
                        ext.endsWith(".png") || ext.endsWith(".webp");
                if (isImg) {
                    final File ff = f;
                    new Thread(() -> {
                        android.graphics.BitmapFactory.Options opts =
                                new android.graphics.BitmapFactory.Options();
                        opts.inSampleSize = 4;
                        android.graphics.Bitmap bmp =
                                android.graphics.BitmapFactory.decodeFile(ff.getAbsolutePath(), opts);
                        if (bmp != null) h.post(() -> thumb.setImageBitmap(bmp));
                    }).start();
                } else {
                    thumb.setImageResource(R.drawable.ic_file);
                }

                LinearLayout info = new LinearLayout(card.getContext());
                info.setOrientation(LinearLayout.VERTICAL);
                TextView tvName = new TextView(card.getContext());
                tvName.setText(f.getName());
                tvName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tvName.setTextColor(0xFF212121);
                tvName.setMaxLines(1);
                tvName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                info.addView(tvName);
                TextView tvPath = new TextView(card.getContext());
                tvPath.setText(f.getParent());
                tvPath.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvPath.setTextColor(0xFF9E9E9E);
                tvPath.setMaxLines(1);
                tvPath.setEllipsize(android.text.TextUtils.TruncateAt.START);
                info.addView(tvPath);
                row.addView(info, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

                card.addView(row);
            }

            // Delete button for this group
            Button btnDel = new Button(card.getContext());
            btnDel.setText("删除选中");
            btnDel.setTextColor(0xFFFFFFFF);
            btnDel.setBackgroundColor(0xFFEF5350);
            btnDel.setOnClickListener(v -> {
                List<File> toDelete = new ArrayList<>();
                for (int i2 = 0; i2 < group.size(); i2++) {
                    if (sel[i2]) toDelete.add(group.get(i2));
                }
                if (toDelete.isEmpty()) {
                    Toast.makeText(card.getContext(), "请先勾选要删除的文件",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(StorageAnalyzerActivity.this)
                        .setTitle("删除文件")
                        .setMessage("删除选中的 " + toDelete.size() + " 个文件？")
                        .setPositiveButton("删除", (d, w) -> {
                            new Thread(() -> {
                                long freed = 0;
                                for (File f2 : toDelete) {
                                    freed += f2.length();
                                    try { RecycleBin.moveToTrash(f2,
                                            StorageAnalyzerActivity.this); }
                                    catch (Exception e) { f2.delete(); }
                                }
                                final long fr = freed;
                                h.post(() -> {
                                    group.removeAll(toDelete);
                                    if (group.size() < 2) {
                                        groups.remove(pos);
                                        selected.remove(pos);
                                        notifyItemRemoved(pos);
                                    } else {
                                        notifyItemChanged(pos);
                                    }
                                    Toast.makeText(StorageAnalyzerActivity.this,
                                            "已释放 " + StorageStats.formatSize(fr),
                                            Toast.LENGTH_SHORT).show();
                                });
                            }).start();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnLp.gravity = Gravity.END;
            btnLp.topMargin = (int)(4 * dp);
            card.addView(btnDel, btnLp);
        }

        @Override public int getItemCount() { return groups.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
