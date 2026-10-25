package com.example.clock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.os.SystemClock;

import java.util.Locale;

public class DayProgressView extends View {

    public static final int STYLE_CLASSIC = 0;
    public static final int STYLE_ARROW = 1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;
    private int sunriseMinute = 6 * 60;
    private int sunsetMinute = 18 * 60;
    private int style = STYLE_CLASSIC;
    private static final float TURTLE_SCALE = 3f;
    private static final int TURTLE_LEG_SWING_INTERVAL_MS = 1000;

    public DayProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        setContentDescription(String.format(Locale.CHINA,
                "今日时间进度 %.1f%%，日出 %s，日落 %s", this.progress * 100,
                formatTime(sunriseMinute), formatTime(sunsetMinute)));
        invalidate();
    }

    public void setSunTimes(int sunriseMinute, int sunsetMinute) {
        this.sunriseMinute = normalizeSunTime(sunriseMinute);
        this.sunsetMinute = normalizeSunTime(sunsetMinute);
        setProgress(progress);
    }

    public void setStyle(int style) {
        if (style != STYLE_CLASSIC && style != STYLE_ARROW) {
            throw new IllegalArgumentException("Unknown day progress style: " + style);
        }
        this.style = style;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        float inset = 18 * density;
        float trackHeight = (style == STYLE_ARROW ? 4 : 2) * density;
        float trackY = height - 5 * density - trackHeight / 2f;
        float startX = inset;
        float endX = Math.max(startX, width - inset);
        float progressX = startX + (endX - startX) * progress;
        float sunriseX = positionForTime(startX, endX, sunriseMinute);
        float sunsetX = positionForTime(startX, endX, sunsetMinute);
        int themeColor = getResources().getColor(R.color.sky_blue);

        paint.setColor(0xFF283747);
        canvas.drawRoundRect(startX, trackY, endX, trackY + trackHeight,
                density, density, paint);

        paint.setColor(themeColor);
        float fillEndX = style == STYLE_ARROW ? Math.max(startX, progressX - 8 * density) : progressX;
        canvas.drawRoundRect(startX, trackY, fillEndX, trackY + trackHeight,
                density, density, paint);

        if (sunriseX >= 0) {
            drawSunMarker(canvas, sunriseX, trackY, density, true);
        }
        if (sunsetX >= 0) {
            drawSunMarker(canvas, sunsetX, trackY, density, false);
        }
        if (style == STYLE_ARROW) {
            drawArrowHead(canvas, Math.min(endX, progressX + 5 * density), trackY + trackHeight / 2f,
                    3 * density, themeColor);
        }
        drawTurtle(canvas, progressX, trackY, density, themeColor);
        if (isShown()) {
            postInvalidateDelayed(TURTLE_LEG_SWING_INTERVAL_MS);
        }
    }

    private void drawSunMarker(Canvas canvas, float x, float trackY, float density, boolean sunrise) {
        float radius = 10 * density;
        float centerY = sunrise ? trackY : trackY - 12 * density;

        if (sunrise) {
            drawSunriseIcon(canvas, x, centerY, radius, density);
        } else {
            drawSunsetIcon(canvas, x, centerY, radius, density);
        }
    }

    private void drawSunriseIcon(Canvas canvas, float x, float centerY, float radius, float density) {
        paint.setColor(0xFFFFD54F);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        int[] rayAngles = {-150, -135, -120, -105, -90, -75, -60, -45, -30};
        float[] rayLengths = {2f, 2.8f, 3.6f, 4.4f, 5.2f, 4.4f, 3.6f, 2.8f, 2f};
        for (int i = 0; i < rayAngles.length; i++) {
            double radians = Math.toRadians(rayAngles[i]);
            float rayStartRadius = radius + 2 * density;
            float rayEndRadius = rayStartRadius + rayLengths[i] * density;
            float innerX = x + (float) Math.cos(radians) * rayStartRadius;
            float innerY = centerY + (float) Math.sin(radians) * rayStartRadius;
            float outerX = x + (float) Math.cos(radians) * rayEndRadius;
            float outerY = centerY + (float) Math.sin(radians) * rayEndRadius;
            canvas.drawLine(innerX, innerY, outerX, outerY, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(x, centerY - radius, x, centerY + radius,
                0xFFFFF5A5, 0xFFEF5350, Shader.TileMode.CLAMP));
        canvas.save();
        canvas.clipRect(x - radius, centerY - radius, x + radius, centerY);
        canvas.drawCircle(x, centerY, radius, paint);
        canvas.restore();
        paint.setShader(null);

        paint.setColor(0xFFFFD54F);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * density);
        canvas.drawLine(x - radius, centerY, x + radius, centerY, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSunsetIcon(Canvas canvas, float x, float centerY, float radius, float density) {
        paint.setShader(new LinearGradient(x, centerY - radius, x, centerY + radius,
                0xFFFFB6A5, 0xFFE53950, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, centerY, radius, paint);
        paint.setShader(null);

        Path farHill = new Path();
        farHill.moveTo(x - 1.15f * radius, centerY + 0.15f * radius);
        farHill.lineTo(x - 0.35f * radius, centerY - 0.3f * radius);
        farHill.lineTo(x + 0.05f * radius, centerY + 0.05f * radius);
        farHill.lineTo(x + 0.45f * radius, centerY - 0.2f * radius);
        farHill.lineTo(x + 1.15f * radius, centerY + 0.25f * radius);
        farHill.lineTo(x + 1.15f * radius, centerY + radius);
        farHill.lineTo(x - 1.15f * radius, centerY + radius);
        farHill.close();
        paint.setColor(0xFF81C784);
        canvas.drawPath(farHill, paint);

        Path nearHill = new Path();
        nearHill.moveTo(x - 1.15f * radius, centerY + 0.55f * radius);
        nearHill.lineTo(x - 0.35f * radius, centerY + 0.1f * radius);
        nearHill.lineTo(x + 0.15f * radius, centerY + 0.55f * radius);
        nearHill.lineTo(x + 0.55f * radius, centerY + 0.25f * radius);
        nearHill.lineTo(x + 1.15f * radius, centerY + 0.65f * radius);
        nearHill.lineTo(x + 1.15f * radius, centerY + radius);
        nearHill.lineTo(x - 1.15f * radius, centerY + radius);
        nearHill.close();
        paint.setColor(0xFF2E7D32);
        canvas.drawPath(nearHill, paint);
    }

    private void drawArrowHead(Canvas canvas, float tipX, float centerY, float size, int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, size * 0.6f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < 2; i++) {
            float x = tipX - i * size * 2;
            canvas.drawLine(x - size, centerY - size, x, centerY, paint);
            canvas.drawLine(x - size, centerY + size, x, centerY, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawTurtle(Canvas canvas, float x, float trackY, float density, int themeColor) {
        float turtleWidth = 14 * TURTLE_SCALE * density;
        float turtleHeight = 8 * TURTLE_SCALE * density;
        float minX = turtleWidth / 2f;
        float maxX = Math.max(minX, getWidth() - turtleWidth / 2f);
        canvas.save();
        canvas.translate(Math.max(minX, Math.min(maxX, x)), trackY - turtleHeight / 2f);
        canvas.scale(TURTLE_SCALE, TURTLE_SCALE);

        float legSwing = (SystemClock.uptimeMillis() / TURTLE_LEG_SWING_INTERVAL_MS) % 2 == 0 ? -24f : 24f;
        drawLeg(canvas, -4 * density, 1.5f * density, legSwing, density);
        drawLeg(canvas, 1.5f * density, 1.5f * density, -legSwing, density);

        paint.setColor(0xFF66BB6A);
        canvas.drawOval(new RectF(-6 * density, -4 * density, 3 * density, 3 * density), paint);
        canvas.drawCircle(5 * density, -2 * density, 2.5f * density, paint);

        paint.setColor(0xFF2E7D32);
        RectF shell = new RectF(-4.5f * density, -3.5f * density, 2 * density, 2.5f * density);
        canvas.drawOval(shell, paint);
        drawShellMesh(canvas, shell, density);

        paint.setColor(0xFF102A17);
        canvas.drawCircle(5.8f * density, -2.8f * density, 0.5f * density, paint);
        canvas.restore();
    }

    private void drawLeg(Canvas canvas, float x, float y, float angle, float density) {
        canvas.save();
        canvas.rotate(angle, x, y);
        paint.setColor(0xFF388E3C);
        canvas.drawRoundRect(new RectF(x - density, y, x + density, y + 2.5f * density),
                density, density, paint);
        canvas.restore();
    }

    private void drawShellMesh(Canvas canvas, RectF shell, float density) {
        canvas.save();
        Path shellPath = new Path();
        shellPath.addOval(shell, Path.Direction.CW);
        canvas.clipPath(shellPath);
        paint.setColor(0xFF81C784);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.45f * density);
        for (int i = -2; i <= 2; i++) {
            float offset = i * 2 * density;
            canvas.drawLine(shell.left + offset, shell.top, shell.right + offset, shell.bottom, paint);
            canvas.drawLine(shell.right - offset, shell.top, shell.left - offset, shell.bottom, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private float positionForTime(float startX, float endX, int minute) {
        if (minute < 0) {
            return -1;
        }
        return startX + (endX - startX) * minute / (24f * 60);
    }

    private int normalizeSunTime(int minute) {
        return minute >= 0 && minute < 24 * 60 ? minute : -1;
    }

    private String formatTime(int minute) {
        return minute < 0 ? "未知" : String.format(Locale.CHINA, "%02d:%02d", minute / 60, minute % 60);
    }
}
