package com.example.filebrowser;

import android.content.Context;

import java.io.*;
import java.util.*;

/**
 * 回收站管理器。
 * 文件存储在 app 私有目录 .recycle_bin/ 下，以 UUID 命名。
 * 元数据保存在同目录的 meta.txt（每行：id|name|originalPath|deletedAt|isDir）。
 * 超过 7 天的文件在 cleanExpired() 时永久删除。
 */
public class RecycleBin {

    private static final String DIR_NAME  = ".recycle_bin";
    private static final String META_FILE = "meta.txt";
    private static final long   EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;

    public static class Item {
        public final String  id;
        public final String  name;
        public final String  originalPath;
        public final long    deletedAt;
        public final boolean isDirectory;

        Item(String id, String name, String originalPath, long deletedAt, boolean isDirectory) {
            this.id           = id;
            this.name         = name;
            this.originalPath = originalPath;
            this.deletedAt    = deletedAt;
            this.isDirectory  = isDirectory;
        }

        /** 剩余天数（0 = 今天到期） */
        public long daysLeft() {
            long remaining = EXPIRE_MS - (System.currentTimeMillis() - deletedAt);
            return Math.max(0, remaining / (24 * 60 * 60 * 1000));
        }
    }

    public static File getBinDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), DIR_NAME);
        dir.mkdirs();
        return dir;
    }

    /** 将文件/文件夹移入回收站（后台线程调用） */
    public static synchronized void moveToTrash(File file, Context ctx) throws IOException {
        File binDir = getBinDir(ctx);
        String id   = UUID.randomUUID().toString();
        File   dest = new File(binDir, id);
        boolean renamed = file.renameTo(dest);
        if (!renamed) {
            copyRecursive(file, dest);
            deleteRecursive(file);
        }
        appendMeta(ctx, id, file.getName(), file.getAbsolutePath(),
                System.currentTimeMillis(), file.isDirectory());
    }

    /** 恢复到原始路径（后台线程调用） */
    public static synchronized void restore(Item item, Context ctx) throws IOException {
        File src  = new File(getBinDir(ctx), item.id);
        File dest = new File(item.originalPath);
        if (!src.exists()) { removeMeta(ctx, item.id); return; }
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        // 目标已存在则追加编号
        if (dest.exists()) {
            String base = dest.getName();
            int    dot  = item.isDirectory ? -1 : base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            String ext  = dot > 0 ? base.substring(dot)    : "";
            int    n    = 1;
            while (dest.exists()) dest = new File(parent, stem + "(" + n++ + ")" + ext);
        }
        boolean renamed = src.renameTo(dest);
        if (!renamed) { copyRecursive(src, dest); deleteRecursive(src); }
        removeMeta(ctx, item.id);
    }

    /** 永久删除（后台线程调用） */
    public static synchronized void deletePermanently(Item item, Context ctx) {
        deleteRecursive(new File(getBinDir(ctx), item.id));
        removeMeta(ctx, item.id);
    }

    /** 清空整个回收站（后台线程调用） */
    public static synchronized void clearAll(Context ctx) {
        for (Item item : getItems(ctx)) {
            deleteRecursive(new File(getBinDir(ctx), item.id));
        }
        new File(getBinDir(ctx), META_FILE).delete();
    }

    /** 删除超过 7 天的条目（建议在 App 启动时调用） */
    public static synchronized void cleanExpired(Context ctx) {
        long now = System.currentTimeMillis();
        for (Item item : getItems(ctx)) {
            if (now - item.deletedAt > EXPIRE_MS) {
                deleteRecursive(new File(getBinDir(ctx), item.id));
                removeMeta(ctx, item.id);
            }
        }
    }

    /** 读取所有回收站条目 */
    public static synchronized List<Item> getItems(Context ctx) {
        List<Item> list = new ArrayList<>();
        File meta = new File(getBinDir(ctx), META_FILE);
        if (!meta.exists()) return list;
        try (BufferedReader br = new BufferedReader(new FileReader(meta))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|", 5);
                if (p.length == 5) {
                    list.add(new Item(p[0], p[1], p[2],
                            Long.parseLong(p[3]), "1".equals(p[4])));
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    // ─── 元数据读写 ────────────────────────────────────────────────────────────

    private static void appendMeta(Context ctx, String id, String name,
                                   String originalPath, long deletedAt, boolean isDir) throws IOException {
        File meta = new File(getBinDir(ctx), META_FILE);
        // 过滤 name/path 中的 | 和换行符
        name         = name.replace("|", "_").replace("\n", "");
        originalPath = originalPath.replace("|", "_").replace("\n", "");
        try (FileWriter fw = new FileWriter(meta, true)) {
            fw.write(id + "|" + name + "|" + originalPath + "|"
                    + deletedAt + "|" + (isDir ? "1" : "0") + "\n");
        }
    }

    private static void removeMeta(Context ctx, String id) {
        File meta = new File(getBinDir(ctx), META_FILE);
        if (!meta.exists()) return;
        try {
            List<String> kept = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(meta))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith(id + "|")) kept.add(line);
                }
            }
            try (FileWriter fw = new FileWriter(meta, false)) {
                for (String line : kept) fw.write(line + "\n");
            }
        } catch (Exception ignored) {}
    }

    // ─── 文件操作工具 ──────────────────────────────────────────────────────────

    private static void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null)
                for (File c : children) copyRecursive(c, new File(dst, c.getName()));
        } else {
            try (FileInputStream in  = new FileInputStream(src);
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }

    static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] ch = file.listFiles();
            if (ch != null) for (File c : ch) deleteRecursive(c);
        }
        file.delete();
    }
}
