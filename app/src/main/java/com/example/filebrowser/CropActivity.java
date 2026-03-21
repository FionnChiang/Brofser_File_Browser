package com.example.filebrowser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CropActivity extends AppCompatActivity {

    public static final String EXTRA_URI        = "image_uri";
    public static final String EXTRA_ASPECT_W   = "aspect_w";
    public static final String EXTRA_ASPECT_H   = "aspect_h";
    public static final String RESULT_PATH      = "cropped_path";

    private CropImageView cropImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("裁切图片");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        cropImageView = findViewById(R.id.cropImageView);

        Button btnCancel  = findViewById(R.id.btnCancel);
        Button btnConfirm = findViewById(R.id.btnConfirm);

        btnCancel.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> confirmCrop());

        float aspectW = getIntent().getFloatExtra(EXTRA_ASPECT_W, 0f);
        float aspectH = getIntent().getFloatExtra(EXTRA_ASPECT_H, 0f);
        if (aspectW > 0 && aspectH > 0) {
            cropImageView.setAspectRatio(aspectW, aspectH);
        }

        String uriStr = getIntent().getStringExtra(EXTRA_URI);
        if (uriStr == null) { finish(); return; }

        loadImage(Uri.parse(uriStr));
    }

    private void loadImage(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) { Toast.makeText(this, "无法打开图片", Toast.LENGTH_SHORT).show(); finish(); return; }
            // 采样加载，避免 OOM
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            is.close();

            int maxSide = 2048;
            opts.inSampleSize = 1;
            while (opts.outWidth  / opts.inSampleSize > maxSide
                || opts.outHeight / opts.inSampleSize > maxSide) {
                opts.inSampleSize *= 2;
            }
            opts.inJustDecodeBounds = false;

            InputStream is2 = getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(is2, null, opts);
            if (is2 != null) is2.close();

            if (bm == null) { Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show(); finish(); return; }
            cropImageView.setBitmap(bm);
        } catch (IOException e) {
            Toast.makeText(this, "读取图片出错", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void confirmCrop() {
        Bitmap cropped = cropImageView.getCroppedBitmap();
        if (cropped == null) { Toast.makeText(this, "裁切失败", Toast.LENGTH_SHORT).show(); return; }

        try {
            File outFile = new File(getFilesDir(), "background.jpg");
            FileOutputStream fos = new FileOutputStream(outFile);
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            cropped.recycle();

            Intent result = new Intent();
            result.putExtra(RESULT_PATH, outFile.getAbsolutePath());
            setResult(RESULT_OK, result);
            finish();
        } catch (IOException e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }
}
