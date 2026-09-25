package com.termux.app.web;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A lightweight circular progress ring that draws an animating stroke around
 * a center target when the user presses and holds.
 */
public class HoldProgressRingView extends View {

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBounds = new RectF();

    private float mProgress = 0f;
    private float mStrokeWidth;

    public HoldProgressRingView(Context context) {
        super(context);
        init();
    }

    public HoldProgressRingView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HoldProgressRingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mStrokeWidth = 2.5f * getResources().getDisplayMetrics().density;

        mTrackPaint.setStyle(Paint.Style.STROKE);
        mTrackPaint.setStrokeWidth(mStrokeWidth);
        mTrackPaint.setColor(Color.parseColor("#33FFFFFF")); // Faint white background track
        mTrackPaint.setStrokeCap(Paint.Cap.ROUND);

        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setStrokeWidth(mStrokeWidth);
        mProgressPaint.setColor(Color.parseColor("#00E5FF")); // Sleek cyan progress ring
        mProgressPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = mStrokeWidth / 2f + (1f * getResources().getDisplayMetrics().density);
        mBounds.set(pad, pad, w - pad, h - pad);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mProgress <= 0f) return;

        // Faint background ring guide
        canvas.drawOval(mBounds, mTrackPaint);

        // Animated forming ring arc
        float sweepAngle = 360f * Math.min(1f, Math.max(0f, mProgress));
        canvas.drawArc(mBounds, -90f, sweepAngle, false, mProgressPaint);
    }

    /**
     * Sets the current hold progress between 0.0f and 1.0f.
     */
    public void setProgress(float progress) {
        mProgress = progress;
        invalidate();
    }

    /**
     * Resets the ring to empty.
     */
    public void reset() {
        mProgress = 0f;
        setAlpha(1f);
        invalidate();
    }
}
