package com.example.clock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class BatteryIconView extends View {

    private static final int LOW_BATTERY_COLOR = 0xFFFF3B30;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int batteryLevel;
    private int normalColor;
    private boolean charging;

    public BatteryIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        normalColor = context.getResources().getColor(R.color.sky_blue);
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = Math.max(0, Math.min(100, batteryLevel));
        invalidate();
    }

    public void setCharging(boolean charging) {
        this.charging = charging;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float scaleX = getWidth() / 48f;
        float scaleY = getHeight() / 18f;
        canvas.save();
        canvas.scale(scaleX, scaleY);

        int color = batteryLevel < 20 ? LOW_BATTERY_COLOR : normalColor;
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(42.5f, 5f, 45.5f, 13f), 1f, 1f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawRoundRect(new RectF(18f, 2f, 41.5f, 16f), 2f, 2f, paint);

        paint.setStyle(Paint.Style.FILL);
        float fillRight = 20f + 19f * batteryLevel / 100f;
        if (fillRight > 20f) {
            canvas.drawRoundRect(new RectF(20f, 4f, fillRight, 14f), 1f, 1f, paint);
        }

        if (charging) {
            Path bolt = new Path();
            bolt.moveTo(8.6f, 2.2f);
            bolt.lineTo(1f, 10f);
            bolt.lineTo(5.9f, 10f);
            bolt.lineTo(3.8f, 15.2f);
            bolt.lineTo(12.8f, 7f);
            bolt.lineTo(7.9f, 7f);
            bolt.close();
            paint.setColor(0xFFFFD600);
            canvas.drawPath(bolt, paint);
        }

        canvas.restore();
    }
}
