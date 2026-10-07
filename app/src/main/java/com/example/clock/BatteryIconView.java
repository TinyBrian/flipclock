package com.example.clock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class BatteryIconView extends View {

    private static final int LOW_BATTERY_COLOR = 0xFFFF3B30;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int batteryLevel;
    private int normalColor;

    public BatteryIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        normalColor = context.getResources().getColor(R.color.sky_blue);
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = Math.max(0, Math.min(100, batteryLevel));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float scaleX = getWidth() / 30f;
        float scaleY = getHeight() / 18f;
        canvas.save();
        canvas.scale(scaleX, scaleY);

        int color = batteryLevel < 20 ? LOW_BATTERY_COLOR : normalColor;
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(26f, 5f, 29f, 13f), 1f, 1f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawRoundRect(new RectF(1.5f, 2f, 25f, 16f), 2f, 2f, paint);

        paint.setStyle(Paint.Style.FILL);
        float fillRight = 3.5f + 19f * batteryLevel / 100f;
        if (fillRight > 3.5f) {
            canvas.drawRoundRect(new RectF(3.5f, 4f, fillRight, 14f), 1f, 1f, paint);
        }

        canvas.restore();
    }
}
