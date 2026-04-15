package com.example.filebrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DonutChartView extends View {

    public interface OnSegmentClickListener {
        void onSegmentClicked(int index, String label);
    }

    private static class Seg {
        float start, sweep;
        int color;
        String label;
    }

    private final List<Seg> segs = new ArrayList<>();
    private final Paint arcPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval      = new RectF();
    private int selected = -1;
    private String line1, line2;
    private OnSegmentClickListener listener;

    public DonutChartView(Context ctx) { super(ctx); init(); }
    public DonutChartView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        holePaint.setStyle(Paint.Style.FILL);
        holePaint.setColor(Color.WHITE);
        txtPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(String[] labels, long[] sizes, int[] colors) {
        long total = 0;
        for (long s : sizes) total += s;
        segs.clear();
        if (total == 0) { invalidate(); return; }
        float cur = -90f;
        for (int i = 0; i < labels.length; i++) {
            if (sizes[i] == 0) continue;
            Seg s = new Seg();
            s.start = cur;
            s.sweep = sizes[i] * 360f / total;
            s.color = colors[i];
            s.label = labels[i];
            segs.add(s);
            cur += s.sweep;
        }
        invalidate();
    }

    public void setCenterText(String l1, String l2) {
        line1 = l1; line2 = l2; invalidate();
    }

    public void setOnSegmentClickListener(OnSegmentClickListener l) { listener = l; }

    public void clearSelection() { selected = -1; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float outer = Math.min(w, h) * 0.46f;
        float stroke = outer * 0.36f;
        float inner  = outer - stroke;
        arcPaint.setStrokeWidth(stroke);

        float margin = stroke / 2f;
        oval.set(cx - outer + margin, cy - outer + margin,
                 cx + outer - margin, cy + outer - margin);

        for (int i = 0; i < segs.size(); i++) {
            Seg seg = segs.get(i);
            arcPaint.setColor(seg.color);
            if (i == selected) {
                float b = stroke * 0.13f;
                RectF big = new RectF(oval.left - b, oval.top - b,
                                      oval.right + b, oval.bottom + b);
                canvas.drawArc(big, seg.start, seg.sweep, false, arcPaint);
            } else {
                canvas.drawArc(oval, seg.start, seg.sweep, false, arcPaint);
            }
        }

        // Gap lines between segments (1dp white)
        Paint gap = new Paint(Paint.ANTI_ALIAS_FLAG);
        gap.setColor(Color.WHITE);
        gap.setStyle(Paint.Style.STROKE);
        gap.setStrokeWidth(2f);
        for (Seg seg : segs) {
            double rad = Math.toRadians(seg.start);
            float r1 = inner + 2, r2 = outer - 2;
            canvas.drawLine(cx + (float)(r1 * Math.cos(rad)), cy + (float)(r1 * Math.sin(rad)),
                            cx + (float)(r2 * Math.cos(rad)), cy + (float)(r2 * Math.sin(rad)), gap);
        }

        canvas.drawCircle(cx, cy, inner, holePaint);

        if (line1 != null) {
            txtPaint.setTextSize(inner * 0.28f);
            txtPaint.setColor(0xFF212121);
            txtPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(line1, cx, cy, txtPaint);
        }
        if (line2 != null) {
            txtPaint.setTextSize(inner * 0.20f);
            txtPaint.setColor(0xFF757575);
            txtPaint.setTypeface(Typeface.DEFAULT);
            canvas.drawText(line2, cx, cy + inner * 0.32f, txtPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float dx = e.getX() - cx, dy = e.getY() - cy;
        float outer = Math.min(getWidth(), getHeight()) * 0.46f;
        float inner = outer * 0.64f;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < inner || dist > outer) return true;

        double rad = Math.atan2(dy, dx);
        float angle = (float) Math.toDegrees(rad) + 90f;
        if (angle < 0) angle += 360f;

        for (int i = 0; i < segs.size(); i++) {
            Seg seg = segs.get(i);
            float s = seg.start + 90f;
            if (s < 0) s += 360f;
            float end = s + seg.sweep;
            boolean hit;
            if (end > 360f) {
                hit = angle >= s || angle <= end - 360f;
            } else {
                hit = angle >= s && angle <= end;
            }
            if (hit) {
                selected = i;
                invalidate();
                if (listener != null) listener.onSegmentClicked(i, seg.label);
                return true;
            }
        }
        return true;
    }
}
