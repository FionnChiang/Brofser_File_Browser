package com.example.filebrowser;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StorageStats {

    public static final String CAT_IMAGE   = "图片";
    public static final String CAT_VIDEO   = "视频";
    public static final String CAT_AUDIO   = "音频";
    public static final String CAT_DOC     = "文档";
    public static final String CAT_APK     = "安装包";
    public static final String CAT_ARCHIVE = "压缩包";
    public static final String CAT_OTHER   = "其他";

    public static final String[] CATEGORIES = {
            CAT_IMAGE, CAT_VIDEO, CAT_AUDIO, CAT_DOC, CAT_APK, CAT_ARCHIVE, CAT_OTHER
    };

    public static final int[] CAT_COLORS = {
            0xFF2196F3, 0xFFE91E63, 0xFF9C27B0,
            0xFFFF9800, 0xFF009688, 0xFF795548, 0xFF9E9E9E
    };

    /** Globally accessible result; replaced on each new analysis */
    public static volatile StorageStats current = null;

    public volatile boolean analyzing = false;
    public volatile boolean complete  = false;
    public volatile String  progressText = "准备中…";

    public long totalBytes;
    public long usedBytes;
    public long freeBytes;

    public final Map<String, Long>       catSizes = new LinkedHashMap<>();
    public final Map<String, List<File>> catFiles = new LinkedHashMap<>();
    public List<List<File>> duplicateGroups = new ArrayList<>();

    public static String categoryForFile(File f) {
        String name = f.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "gif":
            case "bmp": case "webp": case "heic": case "heif": case "svg":
                return CAT_IMAGE;
            case "mp4": case "avi": case "mkv": case "mov": case "wmv":
            case "flv": case "ts": case "3gp": case "m4v": case "webm":
                return CAT_VIDEO;
            case "mp3": case "wav": case "flac": case "aac": case "ogg":
            case "m4a": case "wma": case "opus": case "amr":
                return CAT_AUDIO;
            case "pdf": case "doc": case "docx": case "xls": case "xlsx":
            case "ppt": case "pptx": case "txt": case "md": case "csv":
            case "json": case "html": case "htm":
                return CAT_DOC;
            case "apk":
                return CAT_APK;
            case "zip": case "rar": case "7z": case "tar": case "gz":
            case "bz2": case "xz": case "tgz":
                return CAT_ARCHIVE;
            default:
                return CAT_OTHER;
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
