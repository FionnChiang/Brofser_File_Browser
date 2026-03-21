package com.example.filebrowser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class CropImageView extends View {

    private static final int MODE_NONE     = 0;
    private static final int MODE_MOVE     = 1;
    private static final int MODE_TL       = 2;
    private static final int MODE_TR       = 3;
    private static final int MODE_BL       = 4;
    private static final int MODE_BR       = 5;

    private Bitmap   bitmap;
    private Matrix   displayMatrix  = new Matrix();   // bitmap → view
    private RectF    imageRect      = new RectF();    // displayed image rect in view coords
    private RectF    cropRect       = new RectF();    // crop rect in view coords

    private final Paint overlayPaint = new Paint();
    private final Paint borderPaint  = new Paint();
    private final Paint handlePaint  = new Paint();
    private final Paint gridPaint    = new Paint();

    private int   touchMode   = MODE_NONE;
    private float lastX, lastY;
    private float handleRadius;
    private float minCropSize;

    /** Fixed aspect ratio (width : height). 0 means free crop. */
    private float aspectW = 0, aspectH = 0;

    public void setAspectRatio(float w, float h) {
        this.aspectW = w;
        this.aspectH = h;
        if (bitmap != null && getWidth() > 0) calcDisplayMatrix();
    }

    private boolean hasFixedAspect() { return aspectW > 0 && aspectH > 0; }

    public CropImageView(Context context) { super(context); init(context); }
    public CropImageView(Context context, AttributeSet a) { super(context, a); init(context); }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        handleRadius = 28 * density;
        minCropSize  = 60 * density;

        overlayPaint.setColor(0xAA000000);
        overlayPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2 * density);
        borderPaint.setAntiAlias(true);

        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setAntiAlias(true);

        gridPaint.setColor(0x55FFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(density);
    }

    public void setBitmap(Bitmap bm) {
        this.bitmap = bm;
        if (getWidth() > 0 && getHeight() > 0) calcDisplayMatrix();
        else post(this::calcDisplayMatrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (bitmap != null) calcDisplayMatrix();
    }

    private void calcDisplayMatrix() {
        if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
        int bw = bitmap.getWidth(), bh = bitmap.getHeight();
        int vw = getWidth(),        vh = getHeight();

        float scale = Math.min((float) vw / bw, (float) vh / bh);
        float tx = (vw - bw * scale) / 2f;
        float ty = (vh - bh * scale) / 2f;

        displayMatrix.reset();
        displayMatrix.postScale(scale, scale);
        displayMatrix.postTranslate(tx, ty);

        imageRect.set(tx, ty, tx + bw * scale, ty + bh * scale);

        // 默认裁切框：固定比例居中，或自由内缩 15%
        if (hasFixedAspect()) {
            float imgW = imageRect.width(), imgH = imageRect.height();
            float targetW, targetH;
            if (imgW / imgH > aspectW / aspectH) {
                // 图片比目标宽 → 以高度为准
                targetH = imgH * 0.85f;
                targetW = targetH * aspectW / aspectH;
            } else {
                // 图片比目标高 → 以宽度为准
                targetW = imgW * 0.85f;
                targetH = targetW * aspectH / aspectW;
            }
            float cx = imageRect.centerX(), cy = imageRect.centerY();
            cropRect.set(cx - targetW / 2, cy - targetH / 2,
                         cx + targetW / 2, cy + targetH / 2);
        } else {
            float insetX = imageRect.width()  * 0.15f;
            float insetY = imageRect.height() * 0.15f;
            cropRect.set(imageRect.left  + insetX, imageRect.top    + insetY,
                         imageRect.right - insetX, imageRect.bottom - insetY);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null) return;

        // 1. 绘制图片
        canvas.drawBitmap(bitmap, displayMatrix, null);

        // 2. 四周暗色遮罩（排除裁切框）
        canvas.save();
        canvas.clipRect(cropRect, android.graphics.Region.Op.DIFFERENCE);
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlayPaint);
        canvas.restore();

        // 3. 网格线（三等分）
        float thirdW = cropRect.width()  / 3f;
        float thirdH = cropRect.height() / 3f;
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left + thirdW * 2, cropRect.top, cropRect.left + thirdW * 2, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH * 2, cropRect.right, cropRect.top + thirdH * 2, gridPaint);

        // 4. 裁切框边框
        canvas.drawRect(cropRect, borderPaint);

        // 5. 四角手柄
        float hs = handleRadius * 0.55f;
        drawCornerHandle(canvas, cropRect.left,  cropRect.top,    hs, -1, -1);
        drawCornerHandle(canvas, cropRect.right, cropRect.top,    hs,  1, -1);
        drawCornerHandle(canvas, cropRect.left,  cropRect.bottom, hs, -1,  1);
        drawCornerHandle(canvas, cropRect.right, cropRect.bottom, hs,  1,  1);
    }

    private void drawCornerHandle(Canvas canvas, float cx, float cy, float hs, int sx, int sy) {
        float thick = borderPaint.getStrokeWidth() * 2;
        Paint p = new Paint(handlePaint);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(thick * 2);
        p.setStrokeCap(Paint.Cap.ROUND);
        float len = hs * 1.6f;
        canvas.drawLine(cx, cy, cx + sx * len, cy, p);
        canvas.drawLine(cx, cy, cx, cy + sy * len, p);
    }

    // ─── Touch ───────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchMode = hitTest(x, y);
                lastX = x; lastY = y;
                return touchMode != MODE_NONE;

            case MotionEvent.ACTION_MOVE:
                if (touchMode == MODE_NONE) return false;
                float dx = x - lastX, dy = y - lastY;
                applyDelta(dx, dy);
                lastX = x; lastY = y;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchMode = MODE_NONE;
                return true;
        }
        return false;
    }

    private int hitTest(float x, float y) {
        if (dist(x, y, cropRect.left,  cropRect.top)    < handleRadius) return MODE_TL;
        if (dist(x, y, cropRect.right, cropRect.top)    < handleRadius) return MODE_TR;
        if (dist(x, y, cropRect.left,  cropRect.bottom) < handleRadius) return MODE_BL;
        if (dist(x, y, cropRect.right, cropRect.bottom) < handleRadius) return MODE_BR;
        if (cropRect.contains(x, y)) return MODE_MOVE;
        return MODE_NONE;
    }

    private void applyDelta(float dx, float dy) {
        switch (touchMode) {
            case MODE_MOVE: {
                float nl = cropRect.left  + dx, nr = cropRect.right  + dx;
                float nt = cropRect.top   + dy, nb = cropRect.bottom + dy;
                if (nl < imageRect.left)  { nr -= nl - imageRect.left;  nl = imageRect.left; }
                if (nr > imageRect.right) { nl -= nr - imageRect.right; nr = imageRect.right; }
                if (nt < imageRect.top)   { nb -= nt - imageRect.top;   nt = imageRect.top; }
                if (nb > imageRect.bottom){ nt -= nb - imageRect.bottom; nb = imageRect.bottom; }
                cropRect.set(nl, nt, nr, nb);
                break;
            }
            case MODE_TL: {
                if (hasFixedAspect()) {
                    // Choose axis with larger intended change to drive resize
                    float dxW = Math.abs(dx), dyW = Math.abs(dy) * aspectW / aspectH;
                    float newW, newH, newLeft, newTop;
                    if (dxW >= dyW) {
                        newLeft = clamp(cropRect.left + dx, imageRect.left, cropRect.right - minCropSize);
                        newW = cropRect.right - newLeft;
                        newH = newW * aspectH / aspectW;
                        newTop = cropRect.bottom - newH;
                        if (newTop < imageRect.top) { newH = cropRect.bottom - imageRect.top; newW = newH * aspectW / aspectH; newLeft = cropRect.right - newW; newTop = imageRect.top; }
                    } else {
                        newTop = clamp(cropRect.top + dy, imageRect.top, cropRect.bottom - minCropSize);
                        newH = cropRect.bottom - newTop;
                        newW = newH * aspectW / aspectH;
                        newLeft = cropRect.right - newW;
                        if (newLeft < imageRect.left) { newW = cropRect.right - imageRect.left; newH = newW * aspectH / aspectW; newLeft = imageRect.left; newTop = cropRect.bottom - newH; }
                    }
                    cropRect.left = newLeft; cropRect.top = newTop;
                } else {
                    cropRect.left = clamp(cropRect.left + dx, imageRect.left, cropRect.right - minCropSize);
                    cropRect.top  = clamp(cropRect.top  + dy, imageRect.top,  cropRect.bottom - minCropSize);
                }
                break;
            }
            case MODE_TR: {
                if (hasFixedAspect()) {
                    float dxW = Math.abs(dx), dyW = Math.abs(dy) * aspectW / aspectH;
                    float newW, newH, newRight, newTop;
                    if (dxW >= dyW) {
                        newRight = clamp(cropRect.right + dx, cropRect.left + minCropSize, imageRect.right);
                        newW = newRight - cropRect.left;
                        newH = newW * aspectH / aspectW;
                        newTop = cropRect.bottom - newH;
                        if (newTop < imageRect.top) { newH = cropRect.bottom - imageRect.top; newW = newH * aspectW / aspectH; newRight = cropRect.left + newW; newTop = imageRect.top; }
                    } else {
                        newTop = clamp(cropRect.top + dy, imageRect.top, cropRect.bottom - minCropSize);
                        newH = cropRect.bottom - newTop;
                        newW = newH * aspectW / aspectH;
                        newRight = cropRect.left + newW;
                        if (newRight > imageRect.right) { newW = imageRect.right - cropRect.left; newH = newW * aspectH / aspectW; newRight = imageRect.right; newTop = cropRect.bottom - newH; }
                    }
                    cropRect.right = newRight; cropRect.top = newTop;
                } else {
                    cropRect.right = clamp(cropRect.right + dx, cropRect.left + minCropSize, imageRect.right);
                    cropRect.top   = clamp(cropRect.top   + dy, imageRect.top,               cropRect.bottom - minCropSize);
                }
                break;
            }
            case MODE_BL: {
                if (hasFixedAspect()) {
                    float dxW = Math.abs(dx), dyW = Math.abs(dy) * aspectW / aspectH;
                    float newW, newH, newLeft, newBottom;
                    if (dxW >= dyW) {
                        newLeft = clamp(cropRect.left + dx, imageRect.left, cropRect.right - minCropSize);
                        newW = cropRect.right - newLeft;
                        newH = newW * aspectH / aspectW;
                        newBottom = cropRect.top + newH;
                        if (newBottom > imageRect.bottom) { newH = imageRect.bottom - cropRect.top; newW = newH * aspectW / aspectH; newLeft = cropRect.right - newW; newBottom = imageRect.bottom; }
                    } else {
                        newBottom = clamp(cropRect.bottom + dy, cropRect.top + minCropSize, imageRect.bottom);
                        newH = newBottom - cropRect.top;
                        newW = newH * aspectW / aspectH;
                        newLeft = cropRect.right - newW;
                        if (newLeft < imageRect.left) { newW = cropRect.right - imageRect.left; newH = newW * aspectH / aspectW; newLeft = imageRect.left; newBottom = cropRect.top + newH; }
                    }
                    cropRect.left = newLeft; cropRect.bottom = newBottom;
                } else {
                    cropRect.left   = clamp(cropRect.left   + dx, imageRect.left,  cropRect.right - minCropSize);
                    cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top + minCropSize, imageRect.bottom);
                }
                break;
            }
            case MODE_BR: {
                if (hasFixedAspect()) {
                    float dxW = Math.abs(dx), dyW = Math.abs(dy) * aspectW / aspectH;
                    float newW, newH, newRight, newBottom;
                    if (dxW >= dyW) {
                        newRight = clamp(cropRect.right + dx, cropRect.left + minCropSize, imageRect.right);
                        newW = newRight - cropRect.left;
                        newH = newW * aspectH / aspectW;
                        newBottom = cropRect.top + newH;
                        if (newBottom > imageRect.bottom) { newH = imageRect.bottom - cropRect.top; newW = newH * aspectW / aspectH; newRight = cropRect.left + newW; newBottom = imageRect.bottom; }
                    } else {
                        newBottom = clamp(cropRect.bottom + dy, cropRect.top + minCropSize, imageRect.bottom);
                        newH = newBottom - cropRect.top;
                        newW = newH * aspectW / aspectH;
                        newRight = cropRect.left + newW;
                        if (newRight > imageRect.right) { newW = imageRect.right - cropRect.left; newH = newW * aspectH / aspectW; newRight = imageRect.right; newBottom = cropRect.top + newH; }
                    }
                    cropRect.right = newRight; cropRect.bottom = newBottom;
                } else {
                    cropRect.right  = clamp(cropRect.right  + dx, cropRect.left + minCropSize, imageRect.right);
                    cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top  + minCropSize, imageRect.bottom);
                }
                break;
            }
        }
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ─── 获取裁切结果 ─────────────────────────────────────────────────────────

    public Bitmap getCroppedBitmap() {
        if (bitmap == null) return null;

        Matrix inverse = new Matrix();
        if (!displayMatrix.invert(inverse)) return null;

        RectF cropInBitmap = new RectF(cropRect);
        inverse.mapRect(cropInBitmap);

        int left   = Math.max(0, (int) cropInBitmap.left);
        int top    = Math.max(0, (int) cropInBitmap.top);
        int right  = Math.min(bitmap.getWidth(),  (int) cropInBitmap.right);
        int bottom = Math.min(bitmap.getHeight(), (int) cropInBitmap.bottom);
        int width  = right - left;
        int height = bottom - top;

        if (width <= 0 || height <= 0) return null;
        return Bitmap.createBitmap(bitmap, left, top, width, height);
    }
}
