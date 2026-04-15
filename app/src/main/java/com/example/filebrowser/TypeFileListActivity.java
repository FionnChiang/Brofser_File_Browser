package com.example.filebrowser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TypeFileListActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY = "category";

    private RecyclerView recyclerView;
    private FileListAdapter adapter;
    private Toolbar toolbar;
    private List<File> files = new ArrayList<>();
    private List<Boolean> selected = new ArrayList<>();
    private boolean multiSelect = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        StorageStats stats = StorageStats.current;
        if (stats != null && stats.catFiles.containsKey(category)) {
            files = new ArrayList<>(stats.catFiles.get(category));
            // Sort by size descending
            files.sort((a, b) -> Long.compare(b.length(), a.length()));
        }
        for (int i = 0; i < files.size(); i++) selected.add(false);

        // Build layout programmatically
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        toolbar = new Toolbar(this);
        toolbar.setBackgroundColor(0xFF3F51B5);
        toolbar.setTitleTextColor(0xFFFFFFFF);
        toolbar.setTitle(category != null ? category : "文件列表");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int)(56 * getResources().getDisplayMetrics().density)));

        // Delete button bar (hidden initially)
        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setId(View.generateViewId());
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setBackgroundColor(0xFFF5F5F5);
        actionBar.setPadding(32, 0, 32, 0);
        actionBar.setVisibility(View.GONE);
        int abId = View.generateViewId();
        actionBar.setId(abId);

        TextView tvSelCount = new TextView(this);
        tvSelCount.setId(View.generateViewId());
        tvSelCount.setTextSize(14);
        tvSelCount.setTextColor(0xFF212121);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        actionBar.addView(tvSelCount, tvLp);

        android.widget.Button btnDel = new android.widget.Button(this);
        btnDel.setText("删除");
        btnDel.setTextColor(0xFFFFFFFF);
        btnDel.setBackgroundColor(0xFFEF5350);
        actionBar.addView(btnDel);
        root.addView(actionBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(52 * getResources().getDisplayMetrics().density)));

        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileListAdapter(tvSelCount, actionBar, btnDel);
        recyclerView.setAdapter(adapter);
        root.addView(recyclerView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.VH> {
        private final TextView tvSelCount;
        private final View actionBar;
        private final android.widget.Button btnDel;
        private final Handler handler = new Handler(Looper.getMainLooper());

        FileListAdapter(TextView tv, View ab, android.widget.Button bd) {
            tvSelCount = tv; actionBar = ab; btnDel = bd;
            btnDel.setOnClickListener(v -> confirmDelete());
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(16, 8, 16, 8);
            row.setBackground(getDrawable(android.R.drawable.list_selector_background));
            int dp = (int) parent.getContext().getResources().getDisplayMetrics().density;

            CheckBox cb = new CheckBox(parent.getContext());
            cb.setId(View.generateViewId());
            row.addView(cb, new LinearLayout.LayoutParams(48 * dp, 48 * dp));

            ImageView thumb = new ImageView(parent.getContext());
            thumb.setId(View.generateViewId());
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(0xFFEEEEEE);
            LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(56 * dp, 56 * dp);
            thumbLp.setMarginStart(8 * dp);
            thumbLp.setMarginEnd(12 * dp);
            row.addView(thumb, thumbLp);

            LinearLayout info = new LinearLayout(parent.getContext());
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView tvName = new TextView(parent.getContext());
            tvName.setId(View.generateViewId());
            tvName.setTextSize(14);
            tvName.setTextColor(0xFF212121);
            tvName.setMaxLines(1);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(tvName);

            TextView tvSize = new TextView(parent.getContext());
            tvSize.setId(View.generateViewId());
            tvSize.setTextSize(12);
            tvSize.setTextColor(0xFF757575);
            info.addView(tvSize);

            row.addView(info);
            return new VH(row, cb, thumb, tvName, tvSize);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            File f = files.get(pos);
            h.tvName.setText(f.getName());
            h.tvSize.setText(StorageStats.formatSize(f.length()));
            h.cb.setChecked(selected.get(pos));
            h.thumb.setImageDrawable(null);

            // Load thumbnail async
            String ext = f.getName().toLowerCase();
            boolean isImage = ext.endsWith(".jpg") || ext.endsWith(".jpeg") ||
                    ext.endsWith(".png") || ext.endsWith(".webp") || ext.endsWith(".bmp");
            if (isImage) {
                new Thread(() -> {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 4;
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                    if (bmp != null) handler.post(() -> h.thumb.setImageBitmap(bmp));
                }).start();
            } else {
                // Generic icon based on category
                h.thumb.setImageResource(R.drawable.ic_file);
            }

            h.itemView.setOnClickListener(v -> {
                selected.set(pos, !selected.get(pos));
                h.cb.setChecked(selected.get(pos));
                updateActionBar();
            });
            h.cb.setOnCheckedChangeListener((btn, checked) -> {
                if (selected.get(pos) != checked) {
                    selected.set(pos, checked);
                    updateActionBar();
                }
            });
        }

        @Override public int getItemCount() { return files.size(); }

        private void updateActionBar() {
            int count = 0;
            for (Boolean b : selected) if (b) count++;
            if (count > 0) {
                actionBar.setVisibility(View.VISIBLE);
                tvSelCount.setText("已选 " + count + " 项");
            } else {
                actionBar.setVisibility(View.GONE);
            }
        }

        private void confirmDelete() {
            List<File> toDelete = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                if (selected.get(i)) toDelete.add(files.get(i));
            }
            if (toDelete.isEmpty()) return;
            new AlertDialog.Builder(TypeFileListActivity.this)
                    .setTitle("删除文件")
                    .setMessage("删除选中的 " + toDelete.size() + " 个文件？\n（将移入回收站）")
                    .setPositiveButton("删除", (d, w) -> doDelete(toDelete))
                    .setNegativeButton("取消", null)
                    .show();
        }

        private void doDelete(List<File> toDelete) {
            new Thread(() -> {
                long freed = 0;
                for (File f : toDelete) {
                    freed += f.length();
                    try { RecycleBin.moveToTrash(f, TypeFileListActivity.this); }
                    catch (Exception e) { f.delete(); }
                }
                final long finalFreed = freed;
                handler.post(() -> {
                    // Remove from list
                    files.removeAll(toDelete);
                    selected.clear();
                    for (int i = 0; i < files.size(); i++) selected.add(false);
                    notifyDataSetChanged();
                    updateActionBar();
                    Toast.makeText(TypeFileListActivity.this,
                            "已释放 " + StorageStats.formatSize(finalFreed),
                            Toast.LENGTH_SHORT).show();
                });
            }).start();
        }

        class VH extends RecyclerView.ViewHolder {
            CheckBox cb; ImageView thumb; TextView tvName, tvSize;
            VH(View v, CheckBox cb, ImageView th, TextView n, TextView s) {
                super(v); this.cb = cb; this.thumb = th; tvName = n; tvSize = s;
            }
        }
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }
}
