package com.example.clock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class WeatherIconView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int weatherCode = -1;
    private boolean isDay = true;

    public WeatherIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setWeatherCode(int weatherCode) {
        setWeatherCode(weatherCode, true);
    }

    public void setWeatherCode(int weatherCode, boolean isDay) {
        this.weatherCode = weatherCode;
        this.isDay = isDay;
        invalidate();
    }

    public int getTemperatureColor() {
        if (weatherCode == 0) return 0xFFFFF176;
        if (weatherCode == 1 || weatherCode == 2 || weatherCode == 3
                || weatherCode == 45 || weatherCode == 48) return 0xFFB0BEC5;
        if (isDrizzleOrRain()) return 0xFF81D4FA;
        if (isSnow()) return 0xFFE1F5FE;
        if (isThunderstorm()) return 0xFFD1C4E9;
        return 0xFF2AEF9B;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }

        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(size / 40f, size / 40f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(backgroundColor());
        canvas.drawRoundRect(new RectF(0, 0, 40, 40), 7, 7, paint);

        if (weatherCode < 0) {
            drawCloud(canvas);
        } else if (weatherCode == 0) {
            if (isDay) {
                drawSun(canvas, 20, 19, 8);
            } else {
                drawMoon(canvas, 20, 19, 8);
            }
        } else if (weatherCode == 1 || weatherCode == 2) {
            if (isDay) {
                drawSun(canvas, 14, 14, 6);
            } else {
                drawMoon(canvas, 14, 14, 6);
            }
            drawCloud(canvas);
        } else if (weatherCode == 3) {
            drawCloud(canvas);
        } else if (weatherCode == 45 || weatherCode == 48) {
            drawCloud(canvas);
            drawFog(canvas);
        } else if (isDrizzleOrRain()) {
            drawCloud(canvas);
            drawRain(canvas, rainDropCount());
        } else if (isSnow()) {
            drawCloud(canvas);
            drawSnow(canvas);
        } else if (isThunderstorm()) {
            drawCloud(canvas);
            drawLightning(canvas);
        } else {
            drawCloud(canvas);
        }
        canvas.restore();
    }

    private int backgroundColor() {
        if (!isDay) {
            if (weatherCode == 0) return 0xFF142850;
            if (weatherCode == 1 || weatherCode == 2) return 0xFF1B3558;
            if (weatherCode == 3 || weatherCode == 45 || weatherCode == 48) return 0xFF253858;
            if (isDrizzleOrRain()) return 0xFF163A63;
            if (isSnow()) return 0xFF1C4268;
            if (isThunderstorm()) return 0xFF24254B;
            return 0xFF20334F;
        }
        if (weatherCode == 0) return 0xFFFFA726;
        if (weatherCode == 1 || weatherCode == 2) return 0xFF42A5F5;
        if (weatherCode == 3) return 0xFF607D8B;
        if (weatherCode == 45 || weatherCode == 48) return 0xFF78909C;
        if (isDrizzleOrRain()) return 0xFF1976D2;
        if (isSnow()) return 0xFF4FC3F7;
        if (isThunderstorm()) return 0xFF5E35B1;
        return 0xFF455A64;
    }

    private boolean isDrizzleOrRain() {
        return weatherCode == 51 || weatherCode == 53 || weatherCode == 55
                || weatherCode == 56 || weatherCode == 57
                || weatherCode == 61 || weatherCode == 63 || weatherCode == 65
                || weatherCode == 66 || weatherCode == 67
                || weatherCode == 80 || weatherCode == 81 || weatherCode == 82;
    }

    private boolean isSnow() {
        return weatherCode == 71 || weatherCode == 73 || weatherCode == 75
                || weatherCode == 77 || weatherCode == 85 || weatherCode == 86;
    }

    private boolean isThunderstorm() {
        return weatherCode == 95 || weatherCode == 96 || weatherCode == 99;
    }

    private int rainDropCount() {
        if (weatherCode == 51 || weatherCode == 56 || weatherCode == 61
                || weatherCode == 66 || weatherCode == 80) return 2;
        if (weatherCode == 53 || weatherCode == 57 || weatherCode == 63
                || weatherCode == 81) return 3;
        return 4;
    }

    private void drawSun(Canvas canvas, float centerX, float centerY, float radius) {
        paint.setColor(0xFFFFF176);
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            float startX = centerX + (float) Math.cos(angle) * (radius + 2);
            float startY = centerY + (float) Math.sin(angle) * (radius + 2);
            float endX = centerX + (float) Math.cos(angle) * (radius + 5);
            float endY = centerY + (float) Math.sin(angle) * (radius + 5);
            paint.setStrokeWidth(1.7f);
            canvas.drawLine(startX, startY, endX, endY, paint);
        }
        canvas.drawCircle(centerX, centerY, radius, paint);
    }

    private void drawMoon(Canvas canvas, float centerX, float centerY, float radius) {
        paint.setColor(0xFFFFF1B8);
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setColor(backgroundColor());
        canvas.drawCircle(centerX + radius * 0.48f, centerY - radius * 0.42f, radius * 0.86f, paint);
        paint.setColor(0xFFFFF1B8);
        canvas.drawCircle(centerX - radius * 0.5f, centerY + radius * 0.5f, radius * 0.12f, paint);
    }

    private void drawCloud(Canvas canvas) {
        paint.setColor(isDay ? 0xFFF5F9FF : 0xFFD7E6F5);
        canvas.drawCircle(13, 23, 6, paint);
        canvas.drawCircle(20, 19, 8, paint);
        canvas.drawCircle(28, 23, 6, paint);
        canvas.drawRoundRect(new RectF(9, 22, 32, 28), 3, 3, paint);
    }

    private void drawRain(Canvas canvas, int count) {
        paint.setColor(0xFFB3E5FC);
        paint.setStrokeWidth(2);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < count; i++) {
            float x = 12 + i * (16f / Math.max(1, count - 1));
            canvas.drawLine(x, 30, x - 2, 34 + (i % 2), paint);
        }
    }

    private void drawFog(Canvas canvas) {
        paint.setColor(0xFFECEFF1);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(9, 31, 31, 31, paint);
        canvas.drawLine(12, 35, 28, 35, paint);
    }

    private void drawSnow(Canvas canvas) {
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(13, 33, 1.7f, paint);
        canvas.drawCircle(20, 35, 1.7f, paint);
        canvas.drawCircle(27, 32, 1.7f, paint);
    }

    private void drawLightning(Canvas canvas) {
        paint.setColor(0xFFFFEB3B);
        Path bolt = new Path();
        bolt.moveTo(22, 27);
        bolt.lineTo(16, 35);
        bolt.lineTo(20, 35);
        bolt.lineTo(18, 40);
        bolt.lineTo(26, 31);
        bolt.lineTo(22, 31);
        bolt.close();
        canvas.drawPath(bolt, paint);
    }
}
