package com.example.filebrowser;

import java.io.File;
import java.util.Locale;

public class FileItem {
    public static final int TYPE_FOLDER  = 0;
    public static final int TYPE_IMAGE   = 1;
    public static final int TYPE_VIDEO   = 2;
    public static final int TYPE_AUDIO   = 3;
    public static final int TYPE_TEXT    = 4;
    public static final int TYPE_OTHER   = 5;
    /** Sentinel type used for the "searching…" spinner row. */
    public static final int TYPE_LOADING = -1;

    private final String name;
    private final File   file;
    private final int    type;

    /** Path shown below the file name in search-result mode (parent directory). */
    private String displayPath;

    public FileItem(String name, File file, int type) {
        this.name = name;
        this.file = file;
        this.type = type;
    }

    public String getName()    { return name; }
    public File   getFile()    { return file; }
    public int    getType()    { return type; }

    public String getDisplayPath()              { return displayPath; }
    public void   setDisplayPath(String path)   { this.displayPath = path; }

    /** Creates the loading-spinner sentinel item. */
    public static FileItem createLoadingItem() {
        return new FileItem("", null, TYPE_LOADING);
    }

    public String getInfo() {
        if (type == TYPE_LOADING) return "";
        if (type == TYPE_FOLDER) {
            File[] children = file.listFiles();
            int count = children != null ? children.length : 0;
            return count + " 项";
        }
        return formatSize(file.length());
    }

    private static String formatSize(long size) {
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        else if (size < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024));
        else return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    public static FileItem fromFile(File file) {
        if (file.isDirectory()) {
            return new FileItem(file.getName(), file, TYPE_FOLDER);
        }
        String name = file.getName().toLowerCase(Locale.getDefault());
        int type;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")
                || name.endsWith(".heic") || name.endsWith(".heif")) {
            type = TYPE_IMAGE;
        } else if (name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv")
                || name.endsWith(".mov") || name.endsWith(".wmv") || name.endsWith(".flv")
                || name.endsWith(".3gp") || name.endsWith(".ts") || name.endsWith(".m4v")) {
            type = TYPE_VIDEO;
        } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac")
                || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".m4a")
                || name.endsWith(".wma") || name.endsWith(".opus")) {
            type = TYPE_AUDIO;
        } else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".md")
                || name.endsWith(".csv") || name.endsWith(".json") || name.endsWith(".xml")
                || name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".java")
                || name.endsWith(".kt") || name.endsWith(".py") || name.endsWith(".js")) {
            type = TYPE_TEXT;
        } else {
            type = TYPE_OTHER;
        }
        return new FileItem(file.getName(), file, type);
    }
}
