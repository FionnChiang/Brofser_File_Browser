package com.example.filebrowser;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity implements FileAdapter.OnFileClickListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 101;
    private static final int SHORTCUT_ID_BASE = 10000;

    // 多标签状态：每个标签存储当前所在目录
    private final List<File> tabDirectories = new ArrayList<>();
    private int activeTabIndex = 0;

    private DrawerLayout drawerLayout;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private LinearLayout breadcrumbLayout;
    private HorizontalScrollView breadcrumbScroll;
    private LinearLayout tabLayout;
    private HorizontalScrollView tabScroll;
    private TextView tvEmpty;

    // 多选操作栏
    private LinearLayout selectionBar;
    private LinearLayout bottomActionBar;
    private TextView tvSelectionCount;
    private Button btnSelectAll;

    // 剪贴板模式
    private LinearLayout clipboardActionBar;
    private boolean isClipboardMode = false;

    private ActionBarDrawerToggle drawerToggle;
    private Toolbar mainToolbar;

    // 书签快捷方式
    private final List<String> shortcutPaths = new ArrayList<>();

    // 视图模式
    private int viewMode = FileAdapter.VIEW_LIST;
    private DividerItemDecoration dividerDecoration;
    private boolean dividerAdded = true;

    // ── 搜索 ──────────────────────────────────────────────────────────────────
    private boolean isSearchMode = false;
    private LinearLayout searchBarRow;
    private EditText etSearch;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread searchThread;
    private volatile boolean cancelSearch = false;

    // 搜索过滤条件（对应 popup_search_filter 中的选项）
    private static final int STYPE_ALL = 0, STYPE_FOLDER = 1, STYPE_IMAGE = 2,
            STYPE_VIDEO = 3, STYPE_AUDIO = 4, STYPE_TEXT = 5, STYPE_OTHER = 6;
    private static final int SSIZE_ALL = 0, SSIZE_SMALL = 1, SSIZE_MEDIUM = 2, SSIZE_LARGE = 3;
    private static final int SDATE_ALL = 0, SDATE_TODAY = 1, SDATE_WEEK = 2,
            SDATE_MONTH = 3, SDATE_YEAR = 4;

    private int searchFileType   = STYPE_ALL;
    private int searchSizeFilter = SSIZE_ALL;
    private int searchDateFilter = SDATE_ALL;
    private String searchSortOrder = "name_asc";
    private boolean searchGlobal = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mainToolbar);

        // ── 侧边抽屉 ──────────────────────────────────────────────────────────
        drawerLayout = findViewById(R.id.drawerLayout);
        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, mainToolbar, R.string.nav_open, R.string.nav_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();
        // 多选模式下点击返回箭头退出编辑
        drawerToggle.setToolbarNavigationClickListener(v -> {
            if (adapter != null && adapter.isMultiSelectMode()) {
                adapter.exitMultiSelectMode();
            }
        });

        NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawers();
            int id = item.getItemId();
            if (id >= SHORTCUT_ID_BASE && id < SHORTCUT_ID_BASE + shortcutPaths.size()) {
                loadFiles(new File(shortcutPaths.get(id - SHORTCUT_ID_BASE)));
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_storage) {
                loadFiles(Environment.getExternalStorageDirectory());
            } else if (id == R.id.nav_downloads) {
                loadFiles(new File(Environment.getExternalStorageDirectory(), "Download"));
            } else if (id == R.id.nav_pictures) {
                loadFiles(new File(Environment.getExternalStorageDirectory(), "Pictures"));
            } else if (id == R.id.nav_music) {
                loadFiles(new File(Environment.getExternalStorageDirectory(), "Music"));
            } else if (id == R.id.nav_videos) {
                loadFiles(new File(Environment.getExternalStorageDirectory(), "Movies"));
            }
            return true;
        });

        recyclerView = findViewById(R.id.recyclerView);
        breadcrumbLayout = findViewById(R.id.breadcrumbLayout);
        breadcrumbScroll = findViewById(R.id.breadcrumbScroll);
        tabLayout = findViewById(R.id.tabLayout);
        tabScroll = findViewById(R.id.tabScroll);
        tvEmpty = findViewById(R.id.tvEmpty);
        selectionBar = findViewById(R.id.selectionBar);
        bottomActionBar = findViewById(R.id.bottomActionBar);
        clipboardActionBar = findViewById(R.id.clipboardActionBar);
        tvSelectionCount = findViewById(R.id.tvSelectionCount);

        adapter = new FileAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        dividerDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        recyclerView.addItemDecoration(dividerDecoration);
        dividerAdded = true;
        recyclerView.setAdapter(adapter);

        // 搜索栏
        searchBarRow = findViewById(R.id.searchBarRow);
        etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                startSearch(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            startSearch(etSearch.getText().toString());
            return true;
        });
        findViewById(R.id.btnSearchFilter).setOnClickListener(v -> showSearchFilterPopup(v));

        setupMultiSelect();
        setupSwipeGesture();
        checkAndRequestPermissions();
    }

    // ─── 多选设置 ──────────────────────────────────────────────────────────────

    private void setupMultiSelect() {
        adapter.setMultiSelectListener(new FileAdapter.OnMultiSelectListener() {
            @Override
            public void onEnterMultiSelect() {
                selectionBar.setVisibility(View.VISIBLE);
                bottomActionBar.setVisibility(View.VISIBLE);
                btnSelectAll.setText("全选");
                // 禁用 toggle 自身图标，再直接设置返回箭头（避免 mHomeAsUpIndicator 为 null）
                drawerToggle.setDrawerIndicatorEnabled(false);
                mainToolbar.setNavigationIcon(R.drawable.ic_arrow_back);
                // 隐藏骰子/更多按钮，禁用抽屉滑动
                supportInvalidateOptionsMenu();
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }

            @Override
            public void onSelectionChanged(int selectedCount, int totalCount) {
                tvSelectionCount.setText("已选 " + selectedCount + " 项");
                // 全部可选项均已选中时，按钮变为"全不选"
                boolean allSelected = selectedCount > 0
                        && selectedCount >= adapter.getSelectableCount();
                btnSelectAll.setText(allSelected ? "全不选" : "全选");
            }

            @Override
            public void onExitMultiSelect() {
                selectionBar.setVisibility(View.GONE);
                bottomActionBar.setVisibility(View.GONE);
                if (isClipboardMode) {
                    // 退出多选后立即进入剪贴板模式，工具栏由 enterClipboardMode 接管
                    applyClipboardModeToolbar();
                } else if (isSearchMode) {
                    drawerToggle.setDrawerIndicatorEnabled(false);
                    mainToolbar.setNavigationIcon(R.drawable.ic_arrow_back);
                    drawerToggle.setToolbarNavigationClickListener(v -> exitSearchMode());
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                    supportInvalidateOptionsMenu();
                } else {
                    drawerToggle.setDrawerIndicatorEnabled(true);
                    drawerToggle.syncState();
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                    restoreToolbarTitle();
                }
            }
        });

        // 全选 / 全不选 按钮
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnSelectAll.setOnClickListener(v -> {
            if (adapter.getSelectedItems().size() >= adapter.getSelectableCount()) {
                adapter.deselectAll();
            } else {
                adapter.selectAll();
            }
        });

        // 底部操作按钮
        findViewById(R.id.actionCut).setOnClickListener(v -> {
            List<FileItem> sel = adapter.getSelectedItems();
            if (sel.isEmpty()) { toast("请先选择文件"); return; }
            doCut(sel);
        });
        findViewById(R.id.actionCopy).setOnClickListener(v -> {
            List<FileItem> sel = adapter.getSelectedItems();
            if (sel.isEmpty()) { toast("请先选择文件"); return; }
            doCopy(sel);
        });
        findViewById(R.id.actionDelete).setOnClickListener(v -> {
            List<FileItem> sel = adapter.getSelectedItems();
            if (sel.isEmpty()) { toast("请先选择文件"); return; }
            doDelete(sel);
        });
        findViewById(R.id.actionShare).setOnClickListener(v -> {
            List<FileItem> sel = adapter.getSelectedItems();
            if (sel.isEmpty()) { toast("请先选择文件"); return; }
            doShare(sel);
        });
        findViewById(R.id.actionMore).setOnClickListener(v -> {
            List<FileItem> sel = adapter.getSelectedItems();
            if (sel.isEmpty()) { toast("请先选择文件"); return; }
            showMoreOptions(sel);
        });

        // 剪贴板模式底部栏
        findViewById(R.id.actionClipboardPaste).setOnClickListener(v -> {
            doPaste();
            exitClipboardMode();
        });
        findViewById(R.id.actionClipboardNewFolder).setOnClickListener(v -> showNewFolderDialog());
        findViewById(R.id.actionClipboardView).setOnClickListener(v -> showClipboardFiles());
    }

    private void restoreToolbarTitle() {
        if (!tabDirectories.isEmpty()) {
            File dir = tabDirectories.get(activeTabIndex);
            File root = Environment.getExternalStorageDirectory();
            String title = dir.getAbsolutePath().equals(root.getAbsolutePath())
                    ? "存储" : dir.getName();
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(title);
        }
        supportInvalidateOptionsMenu();
    }

    // ─── 剪贴板模式 ────────────────────────────────────────────────────────────

    /** 仅切换工具栏图标为 X，不改变 clipboardActionBar 可见性（由 doCut/doCopy 控制） */
    private void applyClipboardModeToolbar() {
        drawerToggle.setDrawerIndicatorEnabled(false);
        mainToolbar.setNavigationIcon(R.drawable.ic_close);
        drawerToggle.setToolbarNavigationClickListener(v -> exitClipboardMode());
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        String modeLabel = FileClipboard.mode == FileClipboard.CUT ? "剪切" : "复制";
        if (getSupportActionBar() != null)
            getSupportActionBar().setTitle(modeLabel + " " + FileClipboard.files.size() + " 项");
        supportInvalidateOptionsMenu();
    }

    private void exitClipboardMode() {
        isClipboardMode = false;
        FileClipboard.clear();
        clipboardActionBar.setVisibility(View.GONE);
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerToggle.syncState();
        drawerToggle.setToolbarNavigationClickListener(v -> {
            if (adapter != null && adapter.isMultiSelectMode()) adapter.exitMultiSelectMode();
        });
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        restoreToolbarTitle();
    }

    private void showClipboardFiles() {
        if (!FileClipboard.hasContent()) return;
        String modeStr = FileClipboard.mode == FileClipboard.CUT ? "剪切" : "复制";
        StringBuilder sb = new StringBuilder();
        for (File f : FileClipboard.files) {
            sb.append("• ").append(f.getName()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle(modeStr + "中的文件（共 " + FileClipboard.files.size() + " 项）")
                .setMessage(sb.toString().trim())
                .setPositiveButton("确定", null)
                .show();
    }

    private void showNewFolderDialog() {
        if (tabDirectories.isEmpty()) return;
        EditText editText = new EditText(this);
        editText.setHint("文件夹名称");
        editText.setSingleLine(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(pad, pad / 2, pad, pad / 2);
        new AlertDialog.Builder(this)
                .setTitle("新建文件夹")
                .setView(editText)
                .setPositiveButton("创建", (d, w) -> {
                    String name = editText.getText().toString().trim();
                    if (name.isEmpty()) { toast("文件夹名称不能为空"); return; }
                    File newDir = new File(tabDirectories.get(activeTabIndex), name);
                    if (newDir.exists()) { toast("已存在同名文件夹"); return; }
                    if (newDir.mkdir()) {
                        loadFiles(tabDirectories.get(activeTabIndex));
                        toast("文件夹已创建");
                    } else {
                        toast("创建失败");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ─── 文件操作 ──────────────────────────────────────────────────────────────

    private void doCut(List<FileItem> items) {
        List<File> files = itemsToFiles(items);
        FileClipboard.set(FileClipboard.CUT, files);
        isClipboardMode = true;
        adapter.exitMultiSelectMode(); // onExitMultiSelect 会调用 applyClipboardModeToolbar
        clipboardActionBar.setVisibility(View.VISIBLE);
        TextView label = clipboardActionBar.findViewById(R.id.tvClipboardPasteLabel);
        if (label != null) label.setText("粘贴 " + files.size() + " 个剪切项");
    }

    private void doCopy(List<FileItem> items) {
        List<File> files = itemsToFiles(items);
        FileClipboard.set(FileClipboard.COPY, files);
        isClipboardMode = true;
        adapter.exitMultiSelectMode(); // onExitMultiSelect 会调用 applyClipboardModeToolbar
        clipboardActionBar.setVisibility(View.VISIBLE);
        TextView label = clipboardActionBar.findViewById(R.id.tvClipboardPasteLabel);
        if (label != null) label.setText("粘贴 " + files.size() + " 个复制项");
    }

    private void doPaste() {
        File destDir = tabDirectories.get(activeTabIndex);
        List<File> sources = new ArrayList<>(FileClipboard.files);
        int mode = FileClipboard.mode;
        FileClipboard.clear();

        new Thread(() -> {
            int success = 0, fail = 0;
            for (File src : sources) {
                if (!src.exists()) continue;
                File dest = new File(destDir, src.getName());
                // Avoid overwriting itself (cut to same dir)
                if (dest.getAbsolutePath().equals(src.getAbsolutePath())) continue;
                try {
                    copyFileOrDir(src, dest);
                    if (mode == FileClipboard.CUT) deleteRecursive(src);
                    success++;
                } catch (IOException e) {
                    fail++;
                }
            }
            int finalSuccess = success, finalFail = fail;
            String verb = mode == FileClipboard.CUT ? "移动" : "复制";
            runOnUiThread(() -> {
                loadFiles(destDir);
                String msg = finalSuccess + " 项已" + verb;
                if (finalFail > 0) msg += "，" + finalFail + " 项失败";
                toast(msg);
            });
        }).start();
    }

    private void doDelete(List<FileItem> items) {
        int count = items.size();
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("将永久删除选中的 " + count + " 项，此操作不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    for (FileItem item : items) deleteRecursive(item.getFile());
                    adapter.exitMultiSelectMode();
                    loadFiles(tabDirectories.get(activeTabIndex));
                    toast(count + " 项已删除");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doShare(List<FileItem> items) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (FileItem item : items) {
            if (!item.getFile().isDirectory()) {
                Uri uri = FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", item.getFile());
                uris.add(uri);
            }
        }
        if (uris.isEmpty()) { toast("无法分享文件夹"); return; }

        Intent intent;
        if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        adapter.exitMultiSelectMode();
        startActivity(Intent.createChooser(intent, "分享"));
    }

    private void showMoreOptions(List<FileItem> items) {
        View anchor = findViewById(R.id.actionMore);
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu m = popup.getMenu();
        m.add(0, 0, 0, "重命名");
        m.add(0, 1, 0, "压缩为 ZIP");
        m.add(0, 2, 0, "隐藏");
        m.add(0, 3, 0, "转移（剪切）");
        m.add(0, 4, 0, "创建快捷方式");
        m.add(0, 5, 0, "属性");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: doRename(items);        return true;
                case 1: doCompress(items);      return true;
                case 2: doHide(items);          return true;
                case 3: doCut(items);           return true;
                case 4: doCreateShortcut(items); return true;
                case 5: doProperties(items);    return true;
            }
            return false;
        });
        popup.show();
    }

    private void doRename(List<FileItem> items) {
        if (items.size() != 1) { toast("一次只能重命名一个文件"); return; }
        FileItem item = items.get(0);

        EditText editText = new EditText(this);
        editText.setText(item.getName());
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        editText.setPadding(pad, pad / 2, pad, pad / 2);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(editText)
                .setPositiveButton("确定", (d, w) -> {
                    String newName = editText.getText().toString().trim();
                    if (newName.isEmpty()) { toast("文件名不能为空"); return; }
                    File dest = new File(item.getFile().getParentFile(), newName);
                    if (dest.exists()) { toast("已存在同名文件"); return; }
                    if (item.getFile().renameTo(dest)) {
                        adapter.exitMultiSelectMode();
                        loadFiles(tabDirectories.get(activeTabIndex));
                    } else {
                        toast("重命名失败");
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();

        // 默认选中文件名（不含扩展名）以方便编辑
        editText.post(() -> {
            String name = item.getName();
            int dotIndex = !item.getFile().isDirectory() ? name.lastIndexOf('.') : -1;
            if (dotIndex > 0) editText.setSelection(0, dotIndex);
            else editText.selectAll();
        });
    }

    private void doCompress(List<FileItem> items) {
        File currentDir = tabDirectories.get(activeTabIndex);
        String baseName = items.size() == 1 ? items.get(0).getName() : "archive";
        File zipFile = new File(currentDir, baseName + ".zip");
        int counter = 1;
        while (zipFile.exists()) {
            zipFile = new File(currentDir, baseName + "_" + counter + ".zip");
            counter++;
        }
        final File finalZipFile = zipFile;
        adapter.exitMultiSelectMode();

        new Thread(() -> {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(finalZipFile))) {
                for (FileItem item : items) addToZip(zos, item.getFile(), item.getName());
                runOnUiThread(() -> {
                    loadFiles(currentDir);
                    toast("已压缩为 " + finalZipFile.getName());
                });
            } catch (IOException e) {
                finalZipFile.delete();
                runOnUiThread(() -> toast("压缩失败：" + e.getMessage()));
            }
        }).start();
    }

    private void doHide(List<FileItem> items) {
        int success = 0;
        for (FileItem item : items) {
            if (!item.getName().startsWith(".")) {
                File dest = new File(item.getFile().getParentFile(), "." + item.getName());
                if (item.getFile().renameTo(dest)) success++;
            }
        }
        adapter.exitMultiSelectMode();
        loadFiles(tabDirectories.get(activeTabIndex));
        toast(success + " 项已隐藏");
    }

    private void doCreateShortcut(List<FileItem> items) {
        SharedPreferences prefs = getSharedPreferences("shortcuts", MODE_PRIVATE);
        String existing = prefs.getString("shortcut_paths", "");
        Set<String> pathSet = new HashSet<>();
        if (!existing.isEmpty()) pathSet.addAll(Arrays.asList(existing.split("\\|")));

        int added = 0;
        for (FileItem item : items) {
            String path = item.getFile().getAbsolutePath();
            if (pathSet.add(path)) added++;
        }

        StringBuilder sb = new StringBuilder();
        for (String p : pathSet) {
            if (sb.length() > 0) sb.append("|");
            sb.append(p);
        }
        prefs.edit().putString("shortcut_paths", sb.toString()).apply();
        refreshNavShortcuts();
        adapter.exitMultiSelectMode();
        toast(added + " 项已添加到书签快捷方式");
    }

    private void doProperties(List<FileItem> items) {
        if (items.size() != 1) { toast("请选择单个文件查看属性"); return; }
        File file = items.get(0).getFile();

        long size = getFileSize(file);
        String sizeStr = formatFileSize(size);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String dateStr = sdf.format(new Date(file.lastModified()));
        String typeStr = file.isDirectory() ? "文件夹" : getExtensionLabel(file.getName());

        String msg = "名称：" + file.getName() + "\n\n"
                + "路径：" + file.getParent() + "\n\n"
                + "类型：" + typeStr + "\n\n"
                + "大小：" + sizeStr + "\n\n"
                + "修改时间：" + dateStr + "\n\n"
                + "可读：" + (file.canRead() ? "是" : "否")
                + "  可写：" + (file.canWrite() ? "是" : "否");

        new AlertDialog.Builder(this)
                .setTitle("文件属性")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show();
    }

    // ─── 菜单 ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean multiSelect = adapter != null && adapter.isMultiSelectMode();
        boolean hideAll = multiSelect || isSearchMode || isClipboardMode;
        MenuItem diceItem = menu.findItem(R.id.action_random_folder);
        if (diceItem != null) diceItem.setVisible(!hideAll);
        MenuItem moreItem = menu.findItem(R.id.action_more_main);
        if (moreItem != null) moreItem.setVisible(!hideAll);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) searchItem.setVisible(!hideAll);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            enterSearchMode();
            return true;
        } else if (id == R.id.action_random_folder) {
            navigateToRandomFolder();
            return true;
        } else if (id == R.id.action_more_main) {
            View anchor = findViewById(R.id.action_more_main);
            if (anchor == null) anchor = findViewById(R.id.toolbar);
            showMainMoreMenu(anchor);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ─── 主"更多"菜单 ────────────────────────────────────────────────────────

    private void showMainMoreMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu m = popup.getMenu();
        m.add(0, 0, 0, "新建文件夹");
        m.add(0, 1, 0, "全选");
        m.add(0, 2, 0, "属性");
        m.add(0, 3, 0, "视图切换");
        m.add(0, 4, 0, "排序方式");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: showNewFolderDialog();   return true;
                case 1: doSelectAllFromMenu();   return true;
                case 2: doFolderProperties();    return true;
                case 3: showViewModeMenu(anchor); return true;
                case 4: showSortMenu(anchor);    return true;
            }
            return false;
        });
        popup.show();
    }

    private void doSelectAllFromMenu() {
        if (!adapter.isMultiSelectMode()) {
            adapter.enterMultiSelectModeEmpty();
        }
        adapter.selectAll();
    }

    private void doFolderProperties() {
        if (tabDirectories.isEmpty()) return;
        File folder = tabDirectories.get(activeTabIndex);
        File[] children = folder.listFiles();
        int fileCount = 0, folderCount = 0;
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory()) folderCount++;
                else fileCount++;
            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String msg = "名称：" + folder.getName() + "\n\n"
                + "路径：" + folder.getAbsolutePath() + "\n\n"
                + "文件：" + fileCount + " 个  文件夹：" + folderCount + " 个\n\n"
                + "修改时间：" + sdf.format(new Date(folder.lastModified())) + "\n\n"
                + "可读：" + (folder.canRead() ? "是" : "否")
                + "  可写：" + (folder.canWrite() ? "是" : "否");
        new AlertDialog.Builder(this)
                .setTitle("文件夹属性")
                .setMessage(msg)
                .setPositiveButton("确定", null)
                .show();
    }

    private void showViewModeMenu(View anchor) {
        // 视图模式标签与对应常量
        final int[]    modes  = { FileAdapter.VIEW_LIST, FileAdapter.VIEW_GRID,
                                   FileAdapter.VIEW_GALLERY, FileAdapter.VIEW_LIST_NOICON };
        final String[] labels = { "列表", "网格", "画廊", "无图标列表（完整文件名）" };

        PopupMenu popup = new PopupMenu(this, anchor);
        Menu m = popup.getMenu();
        for (int i = 0; i < labels.length; i++) {
            String prefix = (viewMode == modes[i]) ? "● " : "    ";
            m.add(0, i, i, prefix + labels[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            setViewMode(modes[item.getItemId()]);
            return true;
        });
        popup.show();
    }

    private void showSortMenu(View anchor) {
        final String[] fields = { "name", "date", "size", "type" };
        final String[] labels = { "名称", "修改日期", "文件大小", "文件类型" };

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String sortOrder = prefs.getString("sort_order", "name_asc");

        PopupMenu popup = new PopupMenu(this, anchor);
        Menu m = popup.getMenu();
        for (int i = 0; i < fields.length; i++) {
            boolean isActive = sortOrder.startsWith(fields[i]);
            boolean isAsc    = isActive && sortOrder.endsWith("_asc");
            String indicator = isActive ? (isAsc ? "  ↑" : "  ↓") : "";
            String prefix    = isActive ? "● " : "    ";
            m.add(0, i, i, prefix + labels[i] + indicator);
        }

        popup.setOnMenuItemClickListener(item -> {
            String field   = fields[item.getItemId()];
            String current = prefs.getString("sort_order", "name_asc");
            String newOrder;
            if (current.startsWith(field)) {
                newOrder = current.endsWith("_asc") ? field + "_desc" : field + "_asc";
            } else {
                newOrder = field + "_asc";
            }
            prefs.edit().putString("sort_order", newOrder).apply();
            loadFiles(tabDirectories.get(activeTabIndex));
            return true;
        });
        popup.show();
    }

    private void setViewMode(int mode) {
        viewMode = mode;
        adapter.setViewMode(mode);
        if (mode == FileAdapter.VIEW_GRID || mode == FileAdapter.VIEW_GALLERY) {
            int cols = 3;
            GridLayoutManager glm = new GridLayoutManager(this, cols);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return adapter.isParentDir(position) ? cols : 1;
                }
            });
            recyclerView.setLayoutManager(glm);
            if (dividerAdded) {
                recyclerView.removeItemDecoration(dividerDecoration);
                dividerAdded = false;
            }
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            if (!dividerAdded) {
                recyclerView.addItemDecoration(dividerDecoration);
                dividerAdded = true;
            }
        }
    }

    private void navigateToRandomFolder() {
        File currentDir = tabDirectories.get(activeTabIndex);
        File[] files = currentDir.listFiles();
        if (files == null) { toast("当前目录下没有子文件夹"); return; }
        List<File> folders = new ArrayList<>();
        for (File f : files) {
            if (f.isDirectory() && !f.isHidden()) folders.add(f);
        }
        if (folders.isEmpty()) { toast("当前目录下没有子文件夹"); return; }
        File target = folders.get(new Random().nextInt(folders.size()));
        loadFiles(target);
        toast("随机进入：" + target.getName());
    }

    // ─── 权限处理 ────────────────────────────────────────────────────────────

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog();
            } else {
                initFirstTab();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else {
                initFirstTab();
            }
        } else {
            initFirstTab();
        }
    }

    private void showManageStorageDialog() {
        new AlertDialog.Builder(this)
                .setTitle("需要文件访问权限")
                .setMessage("此应用需要访问所有文件权限才能浏览您的文件。请在设置中允许该权限。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                })
                .setNegativeButton("取消", (dialog, which) -> initFirstTab())
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) initFirstTab();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initFirstTab();
            } else {
                toast("需要存储权限才能浏览文件");
                tvEmpty.setVisibility(View.VISIBLE);
                tvEmpty.setText("无存储访问权限\n请在设置中授予权限");
            }
        }
    }

    // ─── 标签管理 ─────────────────────────────────────────────────────────────

    private void initFirstTab() {
        tabDirectories.clear();
        tabDirectories.add(Environment.getExternalStorageDirectory());
        activeTabIndex = 0;
        renderTabBar();
        loadFiles(tabDirectories.get(activeTabIndex));
    }

    private void addNewTab() {
        tabDirectories.add(Environment.getExternalStorageDirectory());
        activeTabIndex = tabDirectories.size() - 1;
        renderTabBar();
        loadFiles(tabDirectories.get(activeTabIndex));
        tabScroll.post(() -> tabScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabDirectories.size()) return;
        activeTabIndex = index;
        renderTabBar();
        loadFiles(tabDirectories.get(activeTabIndex));
    }

    private void closeTab(int index) {
        if (tabDirectories.size() == 1) { finish(); return; }
        tabDirectories.remove(index);
        if (activeTabIndex >= tabDirectories.size()) activeTabIndex = tabDirectories.size() - 1;
        else if (activeTabIndex > index) activeTabIndex--;
        renderTabBar();
        loadFiles(tabDirectories.get(activeTabIndex));
    }

    private void renderTabBar() {
        tabLayout.removeAllViews();

        int activeColor   = 0xFFFFFFFF;
        int inactiveColor = 0xAAFFFFFF;
        int activeBg      = 0x33FFFFFF;

        for (int i = 0; i < tabDirectories.size(); i++) {
            final int tabIndex = i;
            File dir = tabDirectories.get(i);
            boolean isActive = (i == activeTabIndex);

            View tabView = LayoutInflater.from(this).inflate(R.layout.item_tab, tabLayout, false);
            TextView tvName = tabView.findViewById(R.id.tvTabName);
            ImageButton btnClose = tabView.findViewById(R.id.btnCloseTab);

            File root = Environment.getExternalStorageDirectory();
            String tabName = dir.getAbsolutePath().equals(root.getAbsolutePath())
                    ? "存储" : dir.getName();
            tvName.setText(tabName);

            if (isActive) {
                tvName.setTextColor(activeColor);
                tvName.setTypeface(null, Typeface.BOLD);
                tabView.setBackgroundColor(activeBg);
                btnClose.setColorFilter(activeColor);
            } else {
                tvName.setTextColor(inactiveColor);
                tvName.setTypeface(null, Typeface.NORMAL);
                tabView.setBackgroundColor(0x00000000);
                btnClose.setColorFilter(inactiveColor);
            }

            tabView.setOnClickListener(v -> switchToTab(tabIndex));
            btnClose.setOnClickListener(v -> closeTab(tabIndex));
            tabLayout.addView(tabView);

            if (i < tabDirectories.size() - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(1,
                        (int) (28 * getResources().getDisplayMetrics().density));
                lp.setMargins(0, 7, 0, 7);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(0x44FFFFFF);
                tabLayout.addView(divider);
            }
        }

        // "+" 按钮
        ImageButton btnAdd = new ImageButton(this);
        int size = (int) (42 * getResources().getDisplayMetrics().density);
        int pad  = (int) (10 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(size, LinearLayout.LayoutParams.MATCH_PARENT);
        btnAdd.setLayoutParams(addLp);
        btnAdd.setImageResource(R.drawable.ic_add_tab);
        btnAdd.setColorFilter(0xAAFFFFFF);
        btnAdd.setBackgroundResource(android.R.drawable.list_selector_background);
        btnAdd.setPadding(pad, pad, pad, pad);
        btnAdd.setContentDescription("新建标签");
        btnAdd.setOnClickListener(v -> addNewTab());
        tabLayout.addView(btnAdd);

        tabScroll.post(() -> {
            View activeTab = tabLayout.getChildAt(activeTabIndex * 2);
            if (activeTab != null) tabScroll.smoothScrollTo(activeTab.getLeft(), 0);
        });
    }

    // ─── 文件加载 ─────────────────────────────────────────────────────────────

    private void loadFiles(File directory) {
        if (directory == null || !directory.exists()) {
            toast("无法访问该目录");
            return;
        }

        // 退出多选模式
        if (adapter != null && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
        }

        if (activeTabIndex < tabDirectories.size()) {
            tabDirectories.set(activeTabIndex, directory);
        }

        renderTabBar();

        File root = Environment.getExternalStorageDirectory();
        String title = directory.getAbsolutePath().equals(root.getAbsolutePath())
                ? "存储" : directory.getName();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(title);

        updateBreadcrumb(directory);

        File[] files = directory.listFiles();
        List<FileItem> fileItems = new ArrayList<>();

        if (!directory.getAbsolutePath().equals(root.getAbsolutePath())) {
            fileItems.add(new FileItem("..", directory.getParentFile(), FileItem.TYPE_FOLDER));
        }

        if (files != null) {
            SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
            boolean showHidden   = prefs.getBoolean("show_hidden_files", false);
            boolean foldersFirst = prefs.getBoolean("folders_first", true);
            String  sortOrder    = prefs.getString("sort_order", "name_asc");

            List<File> fileList = new ArrayList<>(Arrays.asList(files));
            Collections.sort(fileList, (f1, f2) -> {
                if (foldersFirst) {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                }
                switch (sortOrder) {
                    case "name_desc": return f2.getName().compareToIgnoreCase(f1.getName());
                    case "date_asc":  return Long.compare(f1.lastModified(), f2.lastModified());
                    case "date_desc": return Long.compare(f2.lastModified(), f1.lastModified());
                    case "size_asc":  return Long.compare(f1.length(), f2.length());
                    case "size_desc": return Long.compare(f2.length(), f1.length());
                    case "type_asc":  return fileExtension(f1.getName()).compareToIgnoreCase(fileExtension(f2.getName()));
                    case "type_desc": return fileExtension(f2.getName()).compareToIgnoreCase(fileExtension(f1.getName()));
                    default:          return f1.getName().compareToIgnoreCase(f2.getName());
                }
            });
            for (File file : fileList) {
                if (showHidden || !file.isHidden()) fileItems.add(FileItem.fromFile(file));
            }
        }

        adapter.setFiles(fileItems);

        if (fileItems.isEmpty() || (fileItems.size() == 1 && fileItems.get(0).getName().equals(".."))) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("此文件夹为空");
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // ─── 面包屑导航 ───────────────────────────────────────────────────────────

    private void updateBreadcrumb(File directory) {
        breadcrumbLayout.removeAllViews();

        File rootDir = Environment.getExternalStorageDirectory();
        String rootPath = rootDir.getAbsolutePath();

        List<File> segments = new ArrayList<>();
        File cursor = directory;
        while (cursor != null) {
            segments.add(0, cursor);
            if (cursor.getAbsolutePath().equals(rootPath)) break;
            cursor = cursor.getParentFile();
        }

        int primaryColor   = getResources().getColor(R.color.colorPrimary, null);
        int currentColor   = 0xFF212121;
        int separatorColor = 0xFF9E9E9E;

        for (int i = 0; i < segments.size(); i++) {
            final File segmentFile = segments.get(i);
            boolean isLast = (i == segments.size() - 1);

            String segName = segmentFile.getAbsolutePath().equals(rootPath)
                    ? "存储" : segmentFile.getName();

            TextView tvSegment = new TextView(this);
            tvSegment.setText(segName);
            tvSegment.setTextSize(15f);
            tvSegment.setPadding(4, 0, 4, 0);

            if (isLast) {
                tvSegment.setTextColor(currentColor);
                tvSegment.setTypeface(null, Typeface.BOLD);
            } else {
                tvSegment.setTextColor(primaryColor);
                tvSegment.setBackground(getDrawable(android.R.drawable.list_selector_background));
                tvSegment.setClickable(true);
                tvSegment.setOnClickListener(v -> loadFiles(segmentFile));
            }
            breadcrumbLayout.addView(tvSegment);

            if (!isLast) {
                TextView tvSep = new TextView(this);
                tvSep.setText("  ›  ");
                tvSep.setTextSize(15f);
                tvSep.setTextColor(separatorColor);
                breadcrumbLayout.addView(tvSep);
            }
        }

        breadcrumbScroll.post(() -> breadcrumbScroll.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }

    // ─── 文件点击 & 打开 ──────────────────────────────────────────────────────

    @Override
    public void onFileClick(FileItem fileItem) {
        if (isSearchMode) {
            // 搜索结果单击：退出搜索并导航到文件所在的文件夹
            File parent = fileItem.getFile().getParentFile();
            if (parent == null) parent = Environment.getExternalStorageDirectory();
            final File target = parent;
            exitSearchMode();
            loadFiles(target);
            return;
        }
        if (fileItem.getType() == FileItem.TYPE_FOLDER) {
            loadFiles(fileItem.getFile());
        } else {
            openFile(fileItem);
        }
    }

    private void openFile(FileItem fileItem) {
        File file = fileItem.getFile();
        if (!file.exists() || !file.canRead()) { toast("无法读取文件"); return; }
        Intent intent;
        switch (fileItem.getType()) {
            case FileItem.TYPE_IMAGE:
                intent = new Intent(this, ImageViewerActivity.class);
                intent.putExtra("file_path", file.getAbsolutePath());
                startActivity(intent);
                break;
            case FileItem.TYPE_VIDEO:
                intent = new Intent(this, VideoPlayerActivity.class);
                intent.putExtra("file_path", file.getAbsolutePath());
                startActivity(intent);
                break;
            case FileItem.TYPE_AUDIO:
                intent = new Intent(this, AudioPlayerActivity.class);
                intent.putExtra("file_path", file.getAbsolutePath());
                startActivity(intent);
                break;
            case FileItem.TYPE_TEXT:
                intent = new Intent(this, TextViewerActivity.class);
                intent.putExtra("file_path", file.getAbsolutePath());
                startActivity(intent);
                break;
            default:
                toast("不支持打开此类型文件");
                break;
        }
    }

    // ─── 生命周期 ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        applyBackground();
        saveContentFrameDimensions();
        refreshNavShortcuts();
        if (!isSearchMode && !tabDirectories.isEmpty()) loadFiles(tabDirectories.get(activeTabIndex));
    }

    private void applyBackground() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String bgPath = prefs.getString("background_image_path", null);
        View contentFrame = findViewById(R.id.contentFrame);
        if (bgPath == null || !new File(bgPath).exists()) {
            contentFrame.setBackground(null);
            return;
        }
        // Defer until the view is laid out so we have real dimensions
        if (contentFrame.getWidth() == 0) {
            contentFrame.post(this::applyBackground);
            return;
        }
        final int w = contentFrame.getWidth();
        final int h = contentFrame.getHeight();
        final int opacity = prefs.getInt("background_opacity", 80);
        new Thread(() -> {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 2;
            Bitmap bmp = BitmapFactory.decodeFile(bgPath, opts);
            if (bmp == null) return;
            // Scale exactly to the content frame so there is no offset/crop
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, h, true);
            if (scaled != bmp) bmp.recycle();
            final Bitmap finalBmp = scaled;
            runOnUiThread(() -> {
                BitmapDrawable bd = new BitmapDrawable(getResources(), finalBmp);
                bd.setAlpha(opacity * 255 / 100);   // 0% = 全透明，100% = 完全不透明
                contentFrame.setBackground(bd);
            });
        }).start();
    }

    /** Save the content-frame pixel dimensions so CropActivity can use the correct ratio. */
    private void saveContentFrameDimensions() {
        View contentFrame = findViewById(R.id.contentFrame);
        if (contentFrame == null) return;
        contentFrame.post(() -> {
            int w = contentFrame.getWidth();
            int h = contentFrame.getHeight();
            if (w > 0 && h > 0) {
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                        .putInt("content_width", w)
                        .putInt("content_height", h)
                        .apply();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawers();
            return;
        }
        if (adapter != null && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
            return;
        }
        if (isClipboardMode) {
            exitClipboardMode();
            return;
        }
        if (isSearchMode) {
            exitSearchMode();
            return;
        }
        File currentDir = tabDirectories.get(activeTabIndex);
        File rootDir = Environment.getExternalStorageDirectory();
        if (!currentDir.getAbsolutePath().equals(rootDir.getAbsolutePath())) {
            loadFiles(currentDir.getParentFile());
        } else {
            super.onBackPressed();
        }
    }

    // ─── 左右滑动切换标签 ─────────────────────────────────────────────────────

    private void setupSwipeGesture() {
        final int SWIPE_THRESHOLD = 80;
        final int SWIPE_VELOCITY_THRESHOLD = 200;

        GestureDetector gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                          float velocityX, float velocityY) {
                        // 多选模式下不切换标签
                        if (adapter != null && adapter.isMultiSelectMode()) return false;
                        if (e1 == null || e2 == null) return false;
                        float diffX = e2.getX() - e1.getX();
                        float diffY = e2.getY() - e1.getY();
                        if (Math.abs(diffX) > Math.abs(diffY)
                                && Math.abs(diffX) > SWIPE_THRESHOLD
                                && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX < 0) {
                                if (activeTabIndex < tabDirectories.size() - 1) {
                                    switchToTab(activeTabIndex + 1);
                                } else {
                                    toast("已是最后一个标签");
                                }
                            } else {
                                if (activeTabIndex > 0) {
                                    switchToTab(activeTabIndex - 1);
                                } else {
                                    toast("已是第一个标签");
                                }
                            }
                            return true;
                        }
                        return false;
                    }
                });

        recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                gestureDetector.onTouchEvent(e);
                return false;
            }
        });
    }

    // ─── 导航书签快捷方式 ──────────────────────────────────────────────────────

    private void refreshNavShortcuts() {
        NavigationView nav = findViewById(R.id.navigationView);
        if (nav == null) return;
        Menu menu = nav.getMenu();
        MenuItem bookmarksItem = menu.findItem(R.id.nav_bookmarks_group);
        if (bookmarksItem == null) return;

        android.view.SubMenu sub = bookmarksItem.getSubMenu();
        // 移除旧的动态项
        for (int i = sub.size() - 1; i >= 0; i--) {
            MenuItem m = sub.getItem(i);
            if (m.getItemId() >= SHORTCUT_ID_BASE) sub.removeItem(m.getItemId());
        }
        shortcutPaths.clear();

        SharedPreferences prefs = getSharedPreferences("shortcuts", MODE_PRIVATE);
        String paths = prefs.getString("shortcut_paths", "");
        if (!paths.isEmpty()) {
            for (String path : paths.split("\\|")) {
                if (path.isEmpty()) continue;
                File f = new File(path);
                if (f.exists()) {
                    shortcutPaths.add(path);
                    int id = SHORTCUT_ID_BASE + shortcutPaths.size() - 1;
                    sub.add(Menu.NONE, id, Menu.NONE, f.getName())
                            .setIcon(f.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
                }
            }
        }
        bookmarksItem.setVisible(!shortcutPaths.isEmpty());
    }

    // ─── 搜索 ──────────────────────────────────────────────────────────────────

    private void enterSearchMode() {
        isSearchMode = true;
        searchBarRow.setVisibility(View.VISIBLE);
        tabScroll.setVisibility(View.GONE);
        breadcrumbScroll.setVisibility(View.GONE);
        // 汉堡 → 退出箭头
        drawerToggle.setDrawerIndicatorEnabled(false);
        mainToolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        drawerToggle.setToolbarNavigationClickListener(v -> exitSearchMode());
        supportInvalidateOptionsMenu();
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        // 切换到搜索结果视图模式（列表+路径行）
        adapter.setViewMode(FileAdapter.VIEW_SEARCH_RESULT);
        adapter.setFiles(new ArrayList<>());
        tvEmpty.setText("请输入搜索关键词");
        tvEmpty.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        etSearch.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
    }

    private void exitSearchMode() {
        isSearchMode = false;
        cancelSearch = true;
        searchBarRow.setVisibility(View.GONE);
        tabScroll.setVisibility(View.VISIBLE);
        breadcrumbScroll.setVisibility(View.VISIBLE);
        etSearch.setText("");
        // 收起键盘
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        // 恢复汉堡
        drawerToggle.setDrawerIndicatorEnabled(true);
        drawerToggle.syncState();
        // 恢复多选退出监听
        drawerToggle.setToolbarNavigationClickListener(v -> {
            if (adapter != null && adapter.isMultiSelectMode()) adapter.exitMultiSelectMode();
        });
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        // 恢复视图模式 & 文件列表
        adapter.setViewMode(viewMode);
        supportInvalidateOptionsMenu();
        if (!tabDirectories.isEmpty()) loadFiles(tabDirectories.get(activeTabIndex));
    }

    private void startSearch(String query) {
        // 取消上次搜索
        cancelSearch = true;

        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            adapter.setFiles(new ArrayList<>());
            tvEmpty.setText("请输入搜索关键词");
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            return;
        }

        // 初始化结果列表（含 loading 尾行）
        List<FileItem> initial = new ArrayList<>();
        initial.add(FileItem.createLoadingItem());
        adapter.setFiles(initial);
        recyclerView.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        cancelSearch = false;
        String queryLower = trimmed.toLowerCase(Locale.getDefault());
        File root = searchGlobal
                ? Environment.getExternalStorageDirectory()
                : (tabDirectories.isEmpty() ? Environment.getExternalStorageDirectory()
                                            : tabDirectories.get(activeTabIndex));

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean showHidden = prefs.getBoolean("show_hidden_files", false);

        searchThread = new Thread(() -> {
            searchRecursive(root, queryLower, showHidden);
            if (!cancelSearch) {
                mainHandler.post(() -> {
                    adapter.removeLoadingItem();
                    if (adapter.getItemCount() == 0) {
                        tvEmpty.setText("未找到匹配文件");
                        tvEmpty.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    }
                });
            }
        });
        searchThread.start();
    }

    private void searchRecursive(File dir, String queryLower, boolean showHidden) {
        if (cancelSearch) return;
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File file : children) {
            if (cancelSearch) return;
            if (!showHidden && file.isHidden()) continue;

            if (file.getName().toLowerCase(Locale.getDefault()).contains(queryLower)
                    && matchesSearchFilters(file)) {
                FileItem item = FileItem.fromFile(file);
                item.setDisplayPath(file.getParent());
                // 将此结果插入到 loading 行之前
                mainHandler.post(() -> {
                    if (!cancelSearch) {
                        adapter.addSearchResult(item);
                        if (tvEmpty.getVisibility() == View.VISIBLE) {
                            tvEmpty.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }

            if (file.isDirectory()) {
                searchRecursive(file, queryLower, showHidden);
            }
        }
    }

    private boolean matchesSearchFilters(File file) {
        // 文件类型
        if (searchFileType != STYPE_ALL) {
            FileItem item = FileItem.fromFile(file);
            switch (searchFileType) {
                case STYPE_FOLDER: if (item.getType() != FileItem.TYPE_FOLDER) return false; break;
                case STYPE_IMAGE:  if (item.getType() != FileItem.TYPE_IMAGE)  return false; break;
                case STYPE_VIDEO:  if (item.getType() != FileItem.TYPE_VIDEO)  return false; break;
                case STYPE_AUDIO:  if (item.getType() != FileItem.TYPE_AUDIO)  return false; break;
                case STYPE_TEXT:   if (item.getType() != FileItem.TYPE_TEXT)   return false; break;
                case STYPE_OTHER:  if (item.getType() != FileItem.TYPE_OTHER)  return false; break;
            }
        }
        // 文件大小（文件夹不参与大小过滤）
        if (searchSizeFilter != SSIZE_ALL && !file.isDirectory()) {
            long size = file.length();
            if (searchSizeFilter == SSIZE_SMALL  && size >= 1024L * 1024) return false;
            if (searchSizeFilter == SSIZE_MEDIUM && (size < 1024L * 1024 || size >= 100L * 1024 * 1024)) return false;
            if (searchSizeFilter == SSIZE_LARGE  && size < 100L * 1024 * 1024) return false;
        }
        // 修改日期
        if (searchDateFilter != SDATE_ALL) {
            long modified = file.lastModified();
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfDay = cal.getTimeInMillis();
            switch (searchDateFilter) {
                case SDATE_TODAY:
                    if (modified < startOfDay) return false;
                    break;
                case SDATE_WEEK:
                    cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                    if (modified < cal.getTimeInMillis()) return false;
                    break;
                case SDATE_MONTH:
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    if (modified < cal.getTimeInMillis()) return false;
                    break;
                case SDATE_YEAR:
                    cal.set(Calendar.DAY_OF_YEAR, 1);
                    if (modified < cal.getTimeInMillis()) return false;
                    break;
            }
        }
        return true;
    }

    private void showSearchFilterPopup(View anchor) {
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_search_filter, null);

        // 文件类型
        RadioGroup rgType = popupView.findViewById(R.id.rgFileType);
        int[] typeIds = {R.id.rb_type_all, R.id.rb_type_folder, R.id.rb_type_image,
                R.id.rb_type_video, R.id.rb_type_audio, R.id.rb_type_text, R.id.rb_type_other};
        ((RadioButton) popupView.findViewById(typeIds[searchFileType])).setChecked(true);

        // 文件大小
        RadioGroup rgSize = popupView.findViewById(R.id.rgFileSize);
        int[] sizeIds = {R.id.rb_size_all, R.id.rb_size_small, R.id.rb_size_medium, R.id.rb_size_large};
        ((RadioButton) popupView.findViewById(sizeIds[searchSizeFilter])).setChecked(true);

        // 修改日期
        RadioGroup rgDate = popupView.findViewById(R.id.rgDate);
        int[] dateIds = {R.id.rb_date_all, R.id.rb_date_today, R.id.rb_date_week,
                R.id.rb_date_month, R.id.rb_date_year};
        ((RadioButton) popupView.findViewById(dateIds[searchDateFilter])).setChecked(true);

        // 排序方式
        RadioGroup rgSortField = popupView.findViewById(R.id.rgSortField);
        RadioGroup rgSortDir   = popupView.findViewById(R.id.rgSortDir);
        String[] sortFields = {"name", "date", "size", "type"};
        int[] sortFieldIds = {R.id.rb_sort_name, R.id.rb_sort_date, R.id.rb_sort_size, R.id.rb_sort_type};
        for (int i = 0; i < sortFields.length; i++) {
            if (searchSortOrder.startsWith(sortFields[i])) {
                ((RadioButton) popupView.findViewById(sortFieldIds[i])).setChecked(true);
                break;
            }
        }
        if (searchSortOrder.endsWith("_desc")) {
            ((RadioButton) popupView.findViewById(R.id.rb_sort_desc)).setChecked(true);
        } else {
            ((RadioButton) popupView.findViewById(R.id.rb_sort_asc)).setChecked(true);
        }

        // 搜索范围
        CheckBox cbGlobal = popupView.findViewById(R.id.cbSearchGlobal);
        cbGlobal.setChecked(searchGlobal);

        // PopupWindow
        PopupWindow pw = new PopupWindow(popupView,
                (int) (280 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        pw.setElevation(8f * getResources().getDisplayMetrics().density);
        pw.setBackgroundDrawable(new ColorDrawable(Color.WHITE));

        // 点击"选择全部结果"
        popupView.findViewById(R.id.tvSelectAllResults).setOnClickListener(v -> {
            pw.dismiss();
            if (!adapter.isMultiSelectMode()) adapter.enterMultiSelectModeEmpty();
            adapter.selectAll();
        });

        // 确认后重新搜索
        pw.setOnDismissListener(() -> {
            // 读取类型
            int checkedTypeId = rgType.getCheckedRadioButtonId();
            for (int i = 0; i < typeIds.length; i++) {
                if (typeIds[i] == checkedTypeId) { searchFileType = i; break; }
            }
            // 读取大小
            int checkedSizeId = rgSize.getCheckedRadioButtonId();
            for (int i = 0; i < sizeIds.length; i++) {
                if (sizeIds[i] == checkedSizeId) { searchSizeFilter = i; break; }
            }
            // 读取日期
            int checkedDateId = rgDate.getCheckedRadioButtonId();
            for (int i = 0; i < dateIds.length; i++) {
                if (dateIds[i] == checkedDateId) { searchDateFilter = i; break; }
            }
            // 读取排序
            int checkedFieldId = rgSortField.getCheckedRadioButtonId();
            String field = "name";
            for (int i = 0; i < sortFieldIds.length; i++) {
                if (sortFieldIds[i] == checkedFieldId) { field = sortFields[i]; break; }
            }
            String dir = (rgSortDir.getCheckedRadioButtonId() == R.id.rb_sort_desc) ? "_desc" : "_asc";
            searchSortOrder = field + dir;
            // 读取搜索范围
            searchGlobal = cbGlobal.isChecked();
            // 重新执行搜索
            startSearch(etSearch.getText().toString());
        });

        pw.showAsDropDown(anchor, 0, 0, Gravity.END);
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private List<File> itemsToFiles(List<FileItem> items) {
        List<File> files = new ArrayList<>();
        for (FileItem item : items) files.add(item.getFile());
        return files;
    }

    private void copyFileOrDir(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) copyFileOrDir(child, new File(dst, child.getName()));
            }
        } else {
            try (FileInputStream in = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        file.delete();
    }

    private void addToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        if (file.isDirectory()) {
            zos.putNextEntry(new ZipEntry(entryName + "/"));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) addToZip(zos, child, entryName + "/" + child.getName());
            }
        } else {
            try (FileInputStream fis = new FileInputStream(file)) {
                zos.putNextEntry(new ZipEntry(entryName));
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) > 0) zos.write(buf, 0, len);
                zos.closeEntry();
            }
        }
    }

    private long getFileSize(File file) {
        if (!file.isDirectory()) return file.length();
        long total = 0;
        File[] children = file.listFiles();
        if (children != null) for (File c : children) total += getFileSize(c);
        return total;
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        if (size < 1024L * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024));
        return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    private String getExtensionLabel(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toUpperCase(Locale.getDefault()) + " 文件" : "文件";
    }

    private static String fileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
