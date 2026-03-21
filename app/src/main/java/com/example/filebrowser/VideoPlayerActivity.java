package com.example.filebrowser;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;

public class VideoPlayerActivity extends AppCompatActivity {

    private VideoView videoView;
    private ProgressBar progressBar;
    private int savedPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        videoView = findViewById(R.id.videoView);
        progressBar = findViewById(R.id.progressBar);

        String filePath = getIntent().getStringExtra("file_path");
        if (filePath == null) {
            Toast.makeText(this, "文件路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        File file = new File(filePath);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(file.getName());
        }

        if (savedInstanceState != null) {
            savedPosition = savedInstanceState.getInt("position", 0);
        }

        setupVideoPlayer(filePath);
    }

    private void setupVideoPlayer(String filePath) {
        // 设置媒体控制器
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        // 设置视频源
        videoView.setVideoURI(Uri.parse("file://" + filePath));

        progressBar.setVisibility(View.VISIBLE);

        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            mp.setLooping(false);
            if (savedPosition > 0) {
                videoView.seekTo(savedPosition);
            }
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(VideoPlayerActivity.this,
                    "视频播放失败，不支持此格式", Toast.LENGTH_LONG).show();
            finish();
            return true;
        });

        videoView.setOnCompletionListener(mp -> {
            // 播放完毕
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (videoView != null) {
            outState.putInt("position", videoView.getCurrentPosition());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            savedPosition = videoView.getCurrentPosition();
            videoView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && savedPosition > 0) {
            videoView.seekTo(savedPosition);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoView != null) {
            videoView.stopPlayback();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
