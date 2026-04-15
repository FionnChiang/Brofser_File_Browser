package com.example.filebrowser;

import android.content.Context;
import android.content.pm.PackageManager;

public class FileOpenPrefs {

    /** 每次询问 */
    public static final String ASK     = "ask";
    /** 始终使用内置播放器 */
    public static final String BUILTIN = "builtin";

    public static final String CAT_IMAGE = "image";
    public static final String CAT_VIDEO = "video";
    public static final String CAT_AUDIO = "audio";
    public static final String CAT_TEXT  = "text";
    public static final String CAT_OTHER = "other";

    private static final String PREFS_NAME = "open_with";

    public static String get(Context ctx, String category) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                  .getString(category, ASK);
    }

    public static void set(Context ctx, String category, String value) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit().putString(category, value).apply();
    }

    public static String categoryForType(int fileType) {
        switch (fileType) {
            case FileItem.TYPE_IMAGE: return CAT_IMAGE;
            case FileItem.TYPE_VIDEO: return CAT_VIDEO;
            case FileItem.TYPE_AUDIO: return CAT_AUDIO;
            case FileItem.TYPE_TEXT:  return CAT_TEXT;
            default:                  return CAT_OTHER;
        }
    }

    /** 返回该分类对应的 MIME 类型（用于 PackageManager 查询） */
    public static String mimeForCategory(String category) {
        switch (category) {
            case CAT_IMAGE: return "image/*";
            case CAT_VIDEO: return "video/*";
            case CAT_AUDIO: return "audio/*";
            case CAT_TEXT:  return "text/plain";
            default:        return "*/*";
        }
    }

    public static String labelForCategory(String category) {
        switch (category) {
            case CAT_IMAGE: return "图片";
            case CAT_VIDEO: return "视频";
            case CAT_AUDIO: return "音频";
            case CAT_TEXT:  return "文本";
            default:        return "其他文件";
        }
    }

    /** 返回当前设置的可读名称（用于在设置界面展示） */
    public static String getDisplayName(Context ctx, String category) {
        String val = get(ctx, category);
        if (ASK.equals(val))     return "每次询问";
        if (BUILTIN.equals(val)) return "内置播放器";
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(
                    pm.getApplicationInfo(val, 0)).toString();
        } catch (Exception e) {
            return val;
        }
    }
}
