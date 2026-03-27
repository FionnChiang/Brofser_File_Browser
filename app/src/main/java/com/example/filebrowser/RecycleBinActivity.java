package com.example.filebrowser;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecycleBinActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private TextView     tvEmpty;
    private List<RecycleBin.Item> items = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ─── 根布局 ───────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // Toolbar
        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle("回收站");
        toolbar.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
        toolbar.setTitleTextColor(0xFFFFFFFF);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        // "清空"菜单项
        toolbar.getMenu().add(0, 1, 0, "清空回收站")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == 1) confirmClearAll();
            return true;
        });
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int)(56 * getResources().getDisplayMetrics().density)));

        // 空提示
        tvEmpty = new TextView(this);
        tvEmpty.setText("回收站为空");
        tvEmpty.setTextSize(16);
        tvEmpty.setTextColor(0xFF999999);
        tvEmpty.setGravity(android.view.Gravity.CENTER);
        tvEmpty.setVisibility(View.GONE);

        // 列表
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(this);
        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.addView(listContainer);
        scrollContent.addView(tvEmpty);
        sv.addView(scrollContent);

        FrameLayout frame = new FrameLayout(this);
        frame.addView(sv);
        tvEmpty.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // re-add to frame instead
        scrollContent.removeView(tvEmpty);
        frame.addView(tvEmpty, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(frame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        loadItems();
    }

    private void loadItems() {
        executor.submit(() -> {
            items = RecycleBin.getItems(this);
            runOnUiThread(this::renderList);
        });
    }

    private void renderList() {
        listContainer.removeAllViews();
        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        float d  = getResources().getDisplayMetrics().density;
        int   dp4 = (int)(4 * d), dp8 = (int)(8 * d), dp12 = (int)(12 * d), dp16 = (int)(16 * d);
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

        for (RecycleBin.Item item : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp16, dp12, dp8, dp12);
            row.setBackgroundResource(android.R.drawable.list_selector_background);

            // 图标
            ImageView icon = new ImageView(this);
            icon.setImageResource(item.isDirectory ? R.drawable.ic_folder : R.drawable.ic_file);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams((int)(36 * d), (int)(36 * d));
            iconLp.gravity = android.view.Gravity.CENTER_VERTICAL;
            icon.setLayoutParams(iconLp);
            row.addView(icon);

            // 文字块
            LinearLayout textBlock = new LinearLayout(this);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textLp.setMarginStart(dp12);
            textBlock.setLayoutParams(textLp);

            TextView tvName = new TextView(this);
            tvName.setText(item.name);
            tvName.setTextSize(15);
            tvName.setTextColor(0xFF212121);
            textBlock.addView(tvName);

            TextView tvPath = new TextView(this);
            tvPath.setText(item.originalPath);
            tvPath.setTextSize(11);
            tvPath.setTextColor(0xFF757575);
            tvPath.setSingleLine(true);
            tvPath.setEllipsize(android.text.TextUtils.TruncateAt.START);
            textBlock.addView(tvPath);

            long daysLeft = item.daysLeft();
            TextView tvDate = new TextView(this);
            tvDate.setText("删除于 " + sdf.format(new Date(item.deletedAt))
                    + "  · 还剩 " + daysLeft + " 天");
            tvDate.setTextSize(11);
            tvDate.setTextColor(daysLeft <= 1 ? 0xFFE53935 : 0xFF9E9E9E);
            textBlock.addView(tvDate);

            row.addView(textBlock);

            // 按钮区
            LinearLayout btnGroup = new LinearLayout(this);
            btnGroup.setOrientation(LinearLayout.VERTICAL);
            btnGroup.setGravity(android.view.Gravity.CENTER);
            btnGroup.setPadding(dp4, 0, dp8, 0);

            Button btnRestore = new Button(this);
            btnRestore.setText("恢复");
            btnRestore.setTextSize(12);
            btnRestore.setTextColor(0xFF1976D2);
            btnRestore.setBackground(getResources().getDrawable(R.drawable.btn_outline_primary, getTheme()));
            btnRestore.setPadding(dp12, dp4, dp12, dp4);
            btnRestore.setMinHeight(0);
            btnRestore.setMinimumHeight(0);
            LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(
                    (int)(64 * d), LinearLayout.LayoutParams.WRAP_CONTENT);
            restoreLp.bottomMargin = dp4;
            btnRestore.setLayoutParams(restoreLp);
            btnRestore.setOnClickListener(v -> confirmRestore(item));
            btnGroup.addView(btnRestore);

            Button btnDel = new Button(this);
            btnDel.setText("删除");
            btnDel.setTextSize(12);
            btnDel.setTextColor(0xFFE53935);
            btnDel.setBackground(getResources().getDrawable(R.drawable.btn_outline_danger, getTheme()));
            btnDel.setPadding(dp12, dp4, dp12, dp4);
            btnDel.setMinHeight(0);
            btnDel.setMinimumHeight(0);
            btnDel.setLayoutParams(new LinearLayout.LayoutParams(
                    (int)(64 * d), LinearLayout.LayoutParams.WRAP_CONTENT));
            btnDel.setOnClickListener(v -> confirmDeletePermanently(item));
            btnGroup.addView(btnDel);

            row.addView(btnGroup);

            // 分隔线
            View divider = new View(this);
            divider.setBackgroundColor(0xFFEEEEEE);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            divider.setLayoutParams(divLp);

            listContainer.addView(row);
            listContainer.addView(divider);
        }
    }

    private void confirmRestore(RecycleBin.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("恢复文件")
                .setMessage("恢复 \"" + item.name + "\" 到原始位置？")
                .setPositiveButton("恢复", (d, w) -> {
                    executor.submit(() -> {
                        try {
                            RecycleBin.restore(item, this);
                            runOnUiThread(() -> { toast("已恢复"); loadItems(); });
                        } catch (Exception e) {
                            runOnUiThread(() -> toast("恢复失败：" + e.getMessage()));
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeletePermanently(RecycleBin.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("永久删除")
                .setMessage("永久删除 \"" + item.name + "\"?此操作不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    executor.submit(() -> {
                        RecycleBin.deletePermanently(item, this);
                        runOnUiThread(() -> { toast("已永久删除"); loadItems(); });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClearAll() {
        if (items.isEmpty()) { toast("回收站已是空的"); return; }
        new AlertDialog.Builder(this)
                .setTitle("清空回收站")
                .setMessage("永久删除回收站中全部 " + items.size() + " 项？")
                .setPositiveButton("清空", (d, w) -> {
                    executor.submit(() -> {
                        RecycleBin.clearAll(this);
                        runOnUiThread(() -> { toast("已清空"); loadItems(); });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
