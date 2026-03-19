package com.example.filebrowser;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * 支持捏合缩放、双击缩放、平移的 ImageView。
 * 滑动翻页由外层 ViewPager2 负责：
 *   - 未放大时允许父级（ViewPager2）拦截横向滑动
 *   - 已放大时阻止父级拦截，使用户可在图片内平移
 */
public class ZoomImageView extends AppCompatImageView {

    private static final float MAX_SCALE_FACTOR   = 5.0f;
    private static final float DOUBLE_TAP_SCALE   = 2.5f;
    private static final int   ANIM_DURATION      = 250;

    private final Matrix mMatrix = new Matrix();
    private float   mMinScale      = 1f;
    private float   mMaxScale      = 5f;
    private boolean mIsInitialized = false;
    private boolean mIsScaling     = false;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector      mGestureDetector;

    public ZoomImageView(Context context)                                    { super(context);       init(context); }
    public ZoomImageView(Context context, AttributeSet a)                   { super(context, a);    init(context); }
    public ZoomImageView(Context context, AttributeSet a, int d)            { super(context, a, d); init(context); }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        setImageMatrix(mMatrix);
        mScaleDetector  = new ScaleGestureDetector(context, new PinchListener());
        mGestureDetector = new GestureDetector(context, new GestureHandler());
    }

    // ─── 设置 Bitmap ──────────────────────────────────────────────────────────

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        mIsInitialized = false;
        mMatrix.reset();
        setImageMatrix(mMatrix);
        if (bm != null) {
            if (getWidth() > 0 && getHeight() > 0) initMatrix(getWidth(), getHeight());
            else post(() -> { if (getWidth() > 0) initMatrix(getWidth(), getHeight()); });
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!mIsInitialized && getDrawable() != null && w > 0 && h > 0)
            initMatrix(w, h);
    }

    // ─── 初始化矩阵（fit-center 适配屏幕）────────────────────────────────────

    private void initMatrix(int vw, int vh) {
        if (getDrawable() == null) return;
        int iw = getDrawable().getIntrinsicWidth();
        int ih = getDrawable().getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) return;

        mMinScale = Math.min((float) vw / iw, (float) vh / ih);
        mMaxScale = mMinScale * MAX_SCALE_FACTOR;

        float tx = (vw - iw * mMinScale) / 2f;
        float ty = (vh - ih * mMinScale) / 2f;

        mMatrix.reset();
        mMatrix.postScale(mMinScale, mMinScale);
        mMatrix.postTranslate(tx, ty);
        setImageMatrix(mMatrix);
        mIsInitialized = true;
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────────

    private float getCurrentScale() {
        float[] v = new float[9];
        mMatrix.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private RectF getImageRect() {
        if (getDrawable() == null) return new RectF();
        RectF r = new RectF(0, 0,
                getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        mMatrix.mapRect(r);
        return r;
    }

    /** 图片始终不超出边界（小于视图时居中，大于视图时贴边） */
    private void fixTranslation() {
        if (!mIsInitialized || getDrawable() == null) return;
        RectF r  = getImageRect();
        float vw = getWidth(), vh = getHeight();
        float dx = 0, dy = 0;

        if (r.width()  <= vw) dx = (vw - r.width())  / 2f - r.left;
        else { if (r.left > 0) dx = -r.left; else if (r.right  < vw) dx = vw - r.right; }

        if (r.height() <= vh) dy = (vh - r.height()) / 2f - r.top;
        else { if (r.top  > 0) dy = -r.top;  else if (r.bottom < vh) dy = vh - r.bottom; }

        if (dx != 0 || dy != 0) { mMatrix.postTranslate(dx, dy); setImageMatrix(mMatrix); }
    }

    /** 动画缩放到目标 scale */
    private void animateScaleTo(float targetScale, float pivotX, float pivotY) {
        float startScale = getCurrentScale();
        if (Math.abs(startScale - targetScale) < 0.01f) { fixTranslation(); return; }

        final Matrix startMatrix = new Matrix(mMatrix);
        ValueAnimator va = ValueAnimator.ofFloat(startScale, targetScale);
        va.setDuration(ANIM_DURATION);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(anim -> {
            float scale = (float) anim.getAnimatedValue();
            mMatrix.set(startMatrix);
            mMatrix.postScale(scale / startScale, scale / startScale, pivotX, pivotY);
            fixTranslation();
            setImageMatrix(mMatrix);
        });
        va.start();
    }

    // ─── Touch ────────────────────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mIsInitialized) return true;
        mScaleDetector.onTouchEvent(event);
        if (!mIsScaling) mGestureDetector.onTouchEvent(event);

        // 放大时阻止 ViewPager2 拦截（以便在图片内平移）
        // 未放大时开放拦截，让 ViewPager2 可以翻页
        if (getParent() != null) {
            boolean zoomed = getCurrentScale() > mMinScale * 1.05f;
            getParent().requestDisallowInterceptTouchEvent(zoomed);
        }
        return true;
    }

    // ─── 捏合缩放 ─────────────────────────────────────────────────────────────

    private class PinchListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScaleBegin(ScaleGestureDetector d) { mIsScaling = true; return true; }

        @Override
        public boolean onScale(ScaleGestureDetector d) {
            float current = getCurrentScale();
            // 橡皮筋效果：超过边界仍可继续拉，但有阻力
            float hardMin = mMinScale * 0.75f, hardMax = mMaxScale * 1.25f;
            float factor  = Math.max(hardMin / current, Math.min(hardMax / current, d.getScaleFactor()));
            mMatrix.postScale(factor, factor, d.getFocusX(), d.getFocusY());
            fixTranslation();
            setImageMatrix(mMatrix);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector d) {
            mIsScaling = false;
            float s = getCurrentScale();
            if      (s < mMinScale) animateScaleTo(mMinScale, getWidth() / 2f, getHeight() / 2f);
            else if (s > mMaxScale) animateScaleTo(mMaxScale, getWidth() / 2f, getHeight() / 2f);
        }
    }

    // ─── 拖动 / 双击 ──────────────────────────────────────────────────────────

    private class GestureHandler extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            if (mIsScaling) return false;
            mMatrix.postTranslate(-dx, -dy);
            fixTranslation();
            setImageMatrix(mMatrix);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            float s = getCurrentScale();
            if (s > mMinScale * 1.4f) {
                // 已放大 → 缩回 fit
                animateScaleTo(mMinScale, getWidth() / 2f, getHeight() / 2f);
            } else {
                // 以点击位置为中心放大
                animateScaleTo(mMinScale * DOUBLE_TAP_SCALE, e.getX(), e.getY());
            }
            return true;
        }
    }
}
