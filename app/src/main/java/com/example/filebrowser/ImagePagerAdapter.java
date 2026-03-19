package com.example.filebrowser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.PageHolder> {

    private final List<String> paths;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    ImagePagerAdapter(List<String> paths) {
        this.paths = paths;
    }

    @NonNull
    @Override
    public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();

        FrameLayout frame = new FrameLayout(ctx);
        frame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.setBackgroundColor(0xFF000000);

        ZoomImageView imageView = new ZoomImageView(ctx);
        imageView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ProgressBar progress = new ProgressBar(ctx);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        progress.setLayoutParams(lp);

        frame.addView(imageView);
        frame.addView(progress);

        return new PageHolder(frame, imageView, progress);
    }

    @Override
    public void onBindViewHolder(@NonNull PageHolder holder, int position) {
        holder.bind(paths.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull PageHolder holder) {
        holder.cancel();
        holder.imageView.setImageBitmap(null);
    }

    @Override
    public int getItemCount() {
        return paths.size();
    }

    class PageHolder extends RecyclerView.ViewHolder {
        final ZoomImageView imageView;
        final ProgressBar   progress;

        private volatile boolean cancelled = false;
        private Thread loadThread;

        PageHolder(View root, ZoomImageView iv, ProgressBar pb) {
            super(root);
            this.imageView = iv;
            this.progress  = pb;
        }

        void bind(String path) {
            cancel();
            cancelled = false;
            progress.setVisibility(View.VISIBLE);
            imageView.setImageBitmap(null);

            loadThread = new Thread(() -> {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, opts);
                opts.inSampleSize      = calcSampleSize(opts, 2048, 2048);
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig  = Bitmap.Config.RGB_565;
                Bitmap bmp = BitmapFactory.decodeFile(path, opts);

                if (!cancelled) {
                    mainHandler.post(() -> {
                        if (!cancelled) {
                            progress.setVisibility(View.GONE);
                            if (bmp != null) imageView.setImageBitmap(bmp);
                        }
                    });
                }
            });
            loadThread.start();
        }

        void cancel() {
            cancelled = true;
            if (loadThread != null) loadThread.interrupt();
        }

        private int calcSampleSize(BitmapFactory.Options opts, int maxW, int maxH) {
            int size = 1;
            while (opts.outHeight / size / 2 >= maxH || opts.outWidth / size / 2 >= maxW) size *= 2;
            return size;
        }
    }
}
