package com.example.filebrowser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.AttributeSet;
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

public class StorageAnalyzerView extends FrameLayout {

    private static final int SUB_OVERVIEW   = 0;
    private static final int SUB_DUPLICATES = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    // Sub-tab
    private TextView tabOverview, tabDup;
    private int currentSub = SUB_OVERVIEW;

    // Loading
    private View loadingView;
    private TextView tvStatus;

    // Overview
    private ScrollView overviewScroll;
    private TextView tvTotal, tvUsed, tvFree, tvBinSize;
    private DonutChartView donutChart;
    private LinearLayout legendLayout;

    // Duplicates
    private RecyclerView dupRecycler;

    public StorageAnalyzerView(Context ctx) { super(ctx); init(); }
    public StorageAnalyzerView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        setBackgroundColor(0xFFF5F5F5);
        float dp = getContext().getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);

        // Sub-tab strip
        LinearLayout subTabStrip = new LinearLayout(getContext());
        subTabStrip.setOrientation(LinearLayout.HORIZONTAL);
        subTabStrip.setBackgroundColor(0xFF3F51B5);

        tabOverview = makeSubTab("文件分布");
        tabDup      = makeSubTab("重复文件");
        subTabStrip.addView(tabOverview, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        subTabStrip.addView(tabDup, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        tabOverview.setOnClickListener(v -> switchSub(SUB_OVERVIEW));
        tabDup.setOnClickListener(v -> switchSub(SUB_DUPLICATES));
        root.addView(subTabStrip, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, (int)(40 * dp)));

        // Content frame
        FrameLayout content = new FrameLayout(getContext());

        // Loading view
        loadingView = buildLoadingView(dp);
        content.addView(loadingView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Overview
        overviewScroll = buildOverviewView(dp);
        content.addView(overviewScroll, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        overviewScroll.setVisibility(GONE);

        // Duplicates
        dupRecycler = new RecyclerView(getContext());
        dupRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        dupRecycler.setBackgroundColor(0xFFF5F5F5);
        content.addView(dupRecycler, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        dupRecycler.setVisibility(GONE);

        root.addView(content, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1));
        addView(root, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        updateSubTabIndicator();
    }

    private TextView makeSubTab(String label) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    // ─── Activation ──────────────────────────────────────────────────────────

    /** Called when this tab becomes active */
    public void activate() {
        StorageStats stats = StorageStats.current;
        if (stats != null && stats.complete) {
            showDashboard(stats);
        } else if (stats != null && stats.analyzing) {
            showLoading();
            startPolling();
        } else {
            showLoading();
            startAnalysis();
        }
    }

    /** Called when switching away from this tab */
    public void deactivate() {
        polling = false;
    }

    // ─── Sub-tab ─────────────────────────────────────────────────────────────

    private void switchSub(int sub) {
        currentSub = sub;
        updateSubTabIndicator();
        StorageStats stats = StorageStats.current;
        if (stats == null || !stats.complete) return;
        overviewScroll.setVisibility(sub == SUB_OVERVIEW   ? VISIBLE : GONE);
        dupRecycler  .setVisibility(sub == SUB_DUPLICATES ? VISIBLE : GONE);
    }

    private void updateSubTabIndicator() {
        tabOverview.setBackgroundColor(currentSub == SUB_OVERVIEW   ? 0xFF5C6BC0 : Color.TRANSPARENT);
        tabDup     .setBackgroundColor(currentSub == SUB_DUPLICATES ? 0xFF5C6BC0 : Color.TRANSPARENT);
        tabOverview.setTypeface(null, currentSub == SUB_OVERVIEW   ? Typeface.BOLD : Typeface.NORMAL);
        tabDup     .setTypeface(null, currentSub == SUB_DUPLICATES ? Typeface.BOLD : Typeface.NORMAL);
    }

    // ─── State ───────────────────────────────────────────────────────────────

    private void showLoading() {
        loadingView   .setVisibility(VISIBLE);
        overviewScroll.setVisibility(GONE);
        dupRecycler   .setVisibility(GONE);
    }

    private void showDashboard(StorageStats stats) {
        loadingView.setVisibility(GONE);
        populateOverview(stats);
        populateDuplicates(stats);
        overviewScroll.setVisibility(currentSub == SUB_OVERVIEW   ? VISIBLE : GONE);
        dupRecycler   .setVisibility(currentSub == SUB_DUPLICATES ? VISIBLE : GONE);
    }

    // ─── Layout builders ─────────────────────────────────────────────────────

    private View buildLoadingView(float dp) {
        LinearLayout ll = new LinearLayout(getContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);
        ll.setBackgroundColor(0xFFF5F5F5);

        ProgressBar pb = new ProgressBar(getContext());
        pb.setIndeterminate(true);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                (int)(48*dp), (int)(48*dp));
        pbLp.bottomMargin = (int)(16*dp);
        ll.addView(pb, pbLp);

        tvStatus = new TextView(getContext());
        tvStatus.setText("正在分析存储空间…");
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvStatus.setTextColor(0xFF757575);
        tvStatus.setGravity(Gravity.CENTER);
        ll.addView(tvStatus);
        return ll;
    }

    private ScrollView buildOverviewView(float dp) {
        ScrollView sv = new ScrollView(getContext());
        sv.setBackgroundColor(0xFFF5F5F5);

        LinearLayout ll = new LinearLayout(getContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding((int)(12*dp), (int)(12*dp), (int)(12*dp), (int)(24*dp));

        // Storage info card
        LinearLayout infoCard = makeCard(dp);
        tvTotal = makeInfoRow(infoCard, "总容量",   "—", dp);
        addDivider(infoCard);
        tvUsed  = makeInfoRow(infoCard, "已使用",   "—", dp);
        addDivider(infoCard);
        tvFree  = makeInfoRow(infoCard, "可用空间", "—", dp);
        ll.addView(infoCard, cardLp(dp));

        // Donut chart card
        LinearLayout chartCard = makeCard(dp);
        chartCard.setGravity(Gravity.CENTER_HORIZONTAL);

        donutChart = new DonutChartView(getContext());
        int cs = (int)(220*dp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cs, cs);
        clp.topMargin = (int)(4*dp);
        chartCard.addView(donutChart, clp);

        legendLayout = new LinearLayout(getContext());
        legendLayout.setOrientation(LinearLayout.VERTICAL);
        legendLayout.setPadding((int)(8*dp), (int)(6*dp), (int)(8*dp), (int)(6*dp));
        chartCard.addView(legendLayout, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        ll.addView(chartCard, cardLp(dp));

        // Recycle bin card
        LinearLayout binCard = makeCard(dp);
        binCard.setOrientation(LinearLayout.HORIZONTAL);
        binCard.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout binInfo = new LinearLayout(getContext());
        binInfo.setOrientation(LinearLayout.VERTICAL);
        TextView binTitle = new TextView(getContext());
        binTitle.setText("回收站");
        binTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        binTitle.setTextColor(0xFF212121);
        binInfo.addView(binTitle);
        tvBinSize = new TextView(getContext());
        tvBinSize.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBinSize.setTextColor(0xFF757575);
        tvBinSize.setText("计算中…");
        binInfo.addView(tvBinSize);
        binCard.addView(binInfo, new LinearLayout.LayoutParams(0,
                LayoutParams.WRAP_CONTENT, 1));

        Button btnClear = new Button(getContext());
        btnClear.setText("清空回收站");
        btnClear.setTextColor(0xFFFFFFFF);
        btnClear.setBackgroundColor(0xFFEF5350);
        btnClear.setOnClickListener(v -> confirmClearBin());
        binCard.addView(btnClear);
        ll.addView(binCard, cardLp(dp));

        sv.addView(ll, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return sv;
    }

    // ─── Card helpers ─────────────────────────────────────────────────────────

    private LinearLayout makeCard(float dp) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Color.WHITE);
        c.setPadding((int)(14*dp), (int)(10*dp), (int)(14*dp), (int)(10*dp));
        return c;
    }

    private LinearLayout.LayoutParams cardLp(float dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(10*dp);
        return lp;
    }

    private TextView makeInfoRow(LinearLayout parent, String label, String val, float dp) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int)(7*dp), 0, (int)(7*dp));
        TextView tvL = new TextView(getContext());
        tvL.setText(label);
        tvL.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvL.setTextColor(0xFF212121);
        row.addView(tvL, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        TextView tvV = new TextView(getContext());
        tvV.setText(val);
        tvV.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvV.setTextColor(0xFF757575);
        row.addView(tvV);
        parent.addView(row, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return tvV;
    }

    private void addDivider(LinearLayout parent) {
        View v = new View(getContext());
        v.setBackgroundColor(0xFFE0E0E0);
        parent.addView(v, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));
    }

    // ─── Populate ─────────────────────────────────────────────────────────────

    private void populateOverview(StorageStats stats) {
        tvTotal.setText(StorageStats.formatSize(stats.totalBytes));
        tvUsed .setText(StorageStats.formatSize(stats.usedBytes));
        tvFree .setText(StorageStats.formatSize(stats.freeBytes));

        int n = StorageStats.CATEGORIES.length;
        long[] sizes = new long[n];
        for (int i = 0; i < n; i++) {
            Long s = stats.catSizes.get(StorageStats.CATEGORIES[i]);
            sizes[i] = s != null ? s : 0;
        }
        donutChart.setData(StorageStats.CATEGORIES, sizes, StorageStats.CAT_COLORS);
        donutChart.setCenterText(StorageStats.formatSize(stats.usedBytes), "已使用");
        donutChart.setOnSegmentClickListener((idx, label) -> openCategory(label));

        float dp = getContext().getResources().getDisplayMetrics().density;
        legendLayout.removeAllViews();
        for (int i = 0; i < n; i++) {
            if (sizes[i] == 0) continue;
            addLegendRow(StorageStats.CATEGORIES[i], sizes[i], StorageStats.CAT_COLORS[i], dp);
        }

        // Recycle bin size async
        new Thread(() -> {
            List<RecycleBin.Item> items = RecycleBin.getItems(getContext());
            long sz = 0;
            for (RecycleBin.Item it : items) {
                sz += dirSize(new File(RecycleBin.getBinDir(getContext()), it.id));
            }
            final long finalSz = sz;
            final int cnt = items.size();
            handler.post(() -> tvBinSize.setText(cnt + " 项，占用 " + StorageStats.formatSize(finalSz)));
        }).start();
    }

    private void addLegendRow(String label, long size, int color, float dp) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int)(3*dp), 0, (int)(3*dp));
        row.setBackground(getContext().getDrawable(android.R.drawable.list_selector_background));

        View dot = new View(getContext());
        dot.setBackgroundColor(color);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int)(10*dp), (int)(10*dp));
        dlp.setMarginEnd((int)(8*dp));
        row.addView(dot, dlp);

        TextView tvL = new TextView(getContext());
        tvL.setText(label);
        tvL.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvL.setTextColor(0xFF212121);
        row.addView(tvL, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView tvS = new TextView(getContext());
        tvS.setText(StorageStats.formatSize(size));
        tvS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvS.setTextColor(0xFF757575);
        row.addView(tvS);

        row.setOnClickListener(v -> openCategory(label));
        legendLayout.addView(row, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void openCategory(String category) {
        Intent intent = new Intent(getContext(), TypeFileListActivity.class);
        intent.putExtra(TypeFileListActivity.EXTRA_CATEGORY, category);
        getContext().startActivity(intent);
    }

    private void populateDuplicates(StorageStats stats) {
        dupRecycler.setAdapter(new DupAdapter(stats.duplicateGroups));
    }

    // ─── Recycle bin ─────────────────────────────────────────────────────────

    private void confirmClearBin() {
        List<RecycleBin.Item> items = RecycleBin.getItems(getContext());
        if (items.isEmpty()) {
            Toast.makeText(getContext(), "回收站已是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle("清空回收站")
                .setMessage("永久删除回收站中全部 " + items.size() + " 项？")
                .setPositiveButton("清空", (d, w) -> doClearBin())
                .setNegativeButton("取消", null)
                .show();
    }

    private void doClearBin() {
        new Thread(() -> {
            List<RecycleBin.Item> items = RecycleBin.getItems(getContext());
            long freed = 0;
            for (RecycleBin.Item item : items) {
                freed += dirSize(new File(RecycleBin.getBinDir(getContext()), item.id));
                RecycleBin.deletePermanently(item, getContext());
            }
            final long f = freed;
            handler.post(() -> {
                tvBinSize.setText("0 项，占用 0 B");
                Toast.makeText(getContext(),
                        "已释放 " + StorageStats.formatSize(f), Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // ─── Analysis ─────────────────────────────────────────────────────────────

    private void startAnalysis() {
        StorageStats stats = new StorageStats();
        stats.analyzing = true;
        StorageStats.current = stats;
        new Thread(() -> {
            try { runAnalysis(stats); }
            catch (Exception e) { stats.progressText = "分析出错"; }
            stats.analyzing = false;
            stats.complete  = true;
            handler.post(() -> {
                showDashboard(stats);
                Toast.makeText(getContext(), "存储分析完成", Toast.LENGTH_SHORT).show();
            });
        }).start();
        startPolling();
    }

    private void startPolling() {
        polling = true;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!polling) return;
                StorageStats s = StorageStats.current;
                if (s != null && s.analyzing && tvStatus != null)
                    tvStatus.setText(s.progressText);
                if (s == null || s.analyzing) handler.postDelayed(this, 300);
            }
        }, 300);
    }

    private void runAnalysis(StorageStats stats) {
        for (String c : StorageStats.CATEGORIES) {
            stats.catSizes.put(c, 0L);
            stats.catFiles.put(c, new ArrayList<>());
        }
        File root = Environment.getExternalStorageDirectory();
        StatFs sf = new StatFs(root.getAbsolutePath());
        stats.totalBytes = sf.getTotalBytes();
        stats.freeBytes  = sf.getAvailableBytes();
        stats.usedBytes  = stats.totalBytes - stats.freeBytes;

        Map<Long, List<File>> sizeMap = new HashMap<>();
        scanDir(root, stats, sizeMap);
        findDuplicates(sizeMap, stats);
    }

    private void scanDir(File dir, StorageStats stats, Map<Long, List<File>> sizeMap) {
        if (dir == null || !dir.isDirectory() || !dir.canRead()) return;
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
                if (size >= 100 * 1024) {
                    sizeMap.computeIfAbsent(size, k -> new ArrayList<>()).add(f);
                }
                stats.progressText = "扫描：" + f.getName();
            }
        }
    }

    private void findDuplicates(Map<Long, List<File>> sizeMap, StorageStats stats) {
        stats.progressText = "检测重复文件…";
        List<List<File>> groups = new ArrayList<>();
        for (List<File> candidates : sizeMap.values()) {
            if (candidates.size() < 2) continue;
            Map<Long, List<File>> crcMap = new HashMap<>();
            for (File f : candidates) {
                long crc = quickCrc(f);
                if (crc < 0) continue;
                crcMap.computeIfAbsent(crc, k -> new ArrayList<>()).add(f);
            }
            for (List<File> g : crcMap.values()) {
                if (g.size() >= 2) {
                    Collections.sort(g, (a, b) -> a.getName().compareTo(b.getName()));
                    groups.add(g);
                }
            }
        }
        groups.sort((a, b) -> Long.compare(
                (long) b.get(0).length() * b.size(),
                (long) a.get(0).length() * a.size()));
        stats.duplicateGroups = groups;
    }

    private long quickCrc(File f) {
        try (FileInputStream fis = new FileInputStream(f);
             FileChannel ch = fis.getChannel()) {
            ByteBuffer buf = ByteBuffer.allocate(131072);
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
        long t = 0;
        File[] ch = f.listFiles();
        if (ch != null) for (File c : ch) t += dirSize(c);
        return t;
    }

    // ─── Duplicate adapter ────────────────────────────────────────────────────

    private class DupAdapter extends RecyclerView.Adapter<DupAdapter.VH> {
        private final List<List<File>> groups;
        private final List<boolean[]>  sel = new ArrayList<>();

        DupAdapter(List<List<File>> g) {
            groups = g;
            for (List<File> group : g) sel.add(new boolean[group.size()]);
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
        public void onBindViewHolder(@NonNull VH holder, int position) {
            List<File> group = groups.get(position);
            boolean[] s = sel.get(position);
            float dp = holder.itemView.getContext().getResources().getDisplayMetrics().density;
            LinearLayout card = (LinearLayout) holder.itemView;
            card.removeAllViews();

            TextView header = new TextView(card.getContext());
            header.setText("重复组（" + group.size() + " 个 · "
                    + StorageStats.formatSize(group.get(0).length()) + " 每个）");
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            header.setTextColor(0xFF757575);
            header.setPadding(0, 0, 0, (int)(4*dp));
            card.addView(header);

            for (int i = 0; i < group.size(); i++) {
                final int idx = i;
                File f = group.get(i);
                LinearLayout row = new LinearLayout(card.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, (int)(3*dp), 0, (int)(3*dp));

                android.widget.CheckBox cb = new android.widget.CheckBox(card.getContext());
                cb.setChecked(s[idx]);
                cb.setOnCheckedChangeListener((btn, checked) -> s[idx] = checked);
                row.addView(cb, new LinearLayout.LayoutParams((int)(32*dp), (int)(32*dp)));

                ImageView thumb = new ImageView(card.getContext());
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setBackgroundColor(0xFFEEEEEE);
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams((int)(44*dp), (int)(44*dp));
                tlp.setMarginStart((int)(4*dp)); tlp.setMarginEnd((int)(8*dp));
                row.addView(thumb, tlp);
                String ext = f.getName().toLowerCase();
                boolean isImg = ext.endsWith(".jpg") || ext.endsWith(".jpeg") ||
                        ext.endsWith(".png") || ext.endsWith(".webp");
                if (isImg) {
                    new Thread(() -> {
                        android.graphics.BitmapFactory.Options opts =
                                new android.graphics.BitmapFactory.Options();
                        opts.inSampleSize = 4;
                        android.graphics.Bitmap bmp =
                                android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                        if (bmp != null) handler.post(() -> thumb.setImageBitmap(bmp));
                    }).start();
                } else {
                    thumb.setImageResource(R.drawable.ic_file);
                }

                LinearLayout info = new LinearLayout(card.getContext());
                info.setOrientation(LinearLayout.VERTICAL);
                TextView tvN = new TextView(card.getContext());
                tvN.setText(f.getName());
                tvN.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tvN.setTextColor(0xFF212121);
                tvN.setMaxLines(1);
                tvN.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                info.addView(tvN);
                TextView tvP = new TextView(card.getContext());
                tvP.setText(f.getParent());
                tvP.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvP.setTextColor(0xFF9E9E9E);
                tvP.setMaxLines(1);
                tvP.setEllipsize(android.text.TextUtils.TruncateAt.START);
                info.addView(tvP);
                row.addView(info, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
                card.addView(row);
            }

            Button btnDel = new Button(card.getContext());
            btnDel.setText("删除选中");
            btnDel.setTextColor(0xFFFFFFFF);
            btnDel.setBackgroundColor(0xFFEF5350);
            btnDel.setOnClickListener(v -> {
                List<File> toDelete = new ArrayList<>();
                for (int i2 = 0; i2 < group.size(); i2++) if (s[i2]) toDelete.add(group.get(i2));
                if (toDelete.isEmpty()) {
                    Toast.makeText(card.getContext(), "请先勾选", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(card.getContext())
                        .setTitle("删除文件")
                        .setMessage("删除选中的 " + toDelete.size() + " 个文件？")
                        .setPositiveButton("删除", (d, w) -> {
                            new Thread(() -> {
                                long freed = 0;
                                for (File f2 : toDelete) {
                                    freed += f2.length();
                                    try { RecycleBin.moveToTrash(f2, getContext()); }
                                    catch (Exception e) { f2.delete(); }
                                }
                                final long fr = freed;
                                handler.post(() -> {
                                    group.removeAll(toDelete);
                                    if (group.size() < 2) {
                                        groups.remove(position);
                                        sel.remove(position);
                                        notifyItemRemoved(position);
                                    } else {
                                        notifyItemChanged(position);
                                    }
                                    Toast.makeText(getContext(),
                                            "已释放 " + StorageStats.formatSize(fr),
                                            Toast.LENGTH_SHORT).show();
                                });
                            }).start();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            blp.gravity = Gravity.END;
            blp.topMargin = (int)(4*dp);
            card.addView(btnDel, blp);
        }

        @Override public int getItemCount() { return groups.size(); }
        class VH extends RecyclerView.ViewHolder { VH(View v) { super(v); } }
    }
}
