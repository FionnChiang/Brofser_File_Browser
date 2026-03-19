package com.example.filebrowser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 文件剪贴板：存储待复制/剪切的文件列表 */
class FileClipboard {
    static final int NONE = 0;
    static final int COPY = 1;
    static final int CUT  = 2;

    static int mode = NONE;
    static final List<File> files = new ArrayList<>();

    static boolean hasContent() {
        return mode != NONE && !files.isEmpty();
    }

    static void set(int newMode, List<File> fileList) {
        mode = newMode;
        files.clear();
        files.addAll(fileList);
    }

    static void clear() {
        mode = NONE;
        files.clear();
    }
}
