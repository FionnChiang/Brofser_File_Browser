package com.example.filebrowser;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全局后台任务管理器（单例）。
 * - 所有监听回调均在主线程执行
 * - 支持取消操作与回滚回调
 * - 线程安全
 */
public class TaskManager {

    public interface Listener {
        void onTasksChanged();
    }

    public static class Task {
        public final String  id       = UUID.randomUUID().toString();
        public final String  title;
        public volatile String  detail    = "等待中…";
        public volatile boolean cancelled = false;
        public volatile boolean started   = false; // 是否已开始执行（非排队中）
        /** 0-100 表示百分比；-1 表示不定式（无法计算） */
        public volatile int     progress  = -1;

        private volatile Runnable cancelCallback; // 取消时的回滚动作

        Task(String title) {
            this.title = title;
        }

        /** 标记任务已开始并更新状态文字，可在子线程调用 */
        public void start(String initialDetail) {
            started = true;
            detail  = initialDetail;
            TaskManager.get().notifyListeners();
        }

        /** 更新当前处理的文件名，可在子线程调用 */
        public void update(String detail) {
            this.detail = detail;
            TaskManager.get().notifyListeners();
        }

        /** 更新百分比进度（0-100），可在子线程调用 */
        public void setProgress(int pct) {
            this.progress = Math.max(0, Math.min(100, pct));
            TaskManager.get().notifyListeners();
        }

        /** 注册取消回调（在 cancel() 时执行，通常包含清理逻辑） */
        public void setCancelAction(Runnable r) {
            this.cancelCallback = r;
        }

        /** 取消此任务；若任务正在运行，cancel flag 会让操作循环中断 */
        public void cancel() {
            cancelled = true;
            Runnable cb = cancelCallback;
            if (cb != null) cb.run();
            TaskManager.get().finishTask(this);
        }

        /** 操作完成时调用 */
        public void finish() {
            TaskManager.get().finishTask(this);
        }
    }

    // ─── 单例 ─────────────────────────────────────────────────────────────────

    private static final TaskManager INSTANCE = new TaskManager();

    public static TaskManager get() { return INSTANCE; }

    private TaskManager() {}

    // ─── 状态 ─────────────────────────────────────────────────────────────────

    private final List<Task>     tasks       = new ArrayList<>();
    private final List<Listener> listeners   = new ArrayList<>();
    private final Handler        mainHandler = new Handler(Looper.getMainLooper());

    // ─── 公共 API ─────────────────────────────────────────────────────────────

    public synchronized Task addTask(String title) {
        Task t = new Task(title);
        tasks.add(t);
        notifyListeners();
        return t;
    }

    synchronized void finishTask(Task t) {
        tasks.remove(t);
        notifyListeners();
    }

    public synchronized List<Task> getActiveTasks() {
        return new ArrayList<>(tasks);
    }

    public synchronized int getTaskCount() {
        return tasks.size();
    }

    public synchronized void addListener(Listener l)    { listeners.add(l); }
    public synchronized void removeListener(Listener l) { listeners.remove(l); }

    // ─── 内部 ─────────────────────────────────────────────────────────────────

    void notifyListeners() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatchListeners();
        } else {
            mainHandler.post(this::dispatchListeners);
        }
    }

    private synchronized void dispatchListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onTasksChanged();
    }
}
