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
import java.util.Random;

public class DayProgressView extends View {

    public static final int STYLE_CLASSIC = 0;
    public static final int STYLE_ARROW = 1;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;
    private int sunriseMinute = 6 * 60;
    private int sunsetMinute = 18 * 60;
    private int style = STYLE_CLASSIC;
    private float treeScale = 2f;
    private final long treeLayoutSeed = new Random().nextLong();
    private static final float TURTLE_SCALE = 3f;
    private static final int TURTLE_LEG_SWING_INTERVAL_MS = 1000;
    private static final int MAX_TREE_COUNT = 14;

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

    public void setTreeScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Tree scale must be a finite positive number");
        }
        treeScale = scale;
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

        drawTrees(canvas, startX, endX, trackY, density, sunriseX, sunsetX);

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

    private void drawTrees(Canvas canvas, float startX, float endX, float baseY, float density,
                           float sunriseX, float sunsetX) {
        float spacing = 46 * density;
        int candidateCount = Math.max(0, (int) ((endX - startX) / spacing));
        int treeCountLimit = Math.min(candidateCount, MAX_TREE_COUNT);
        Random random = new Random(treeLayoutSeed);
        int[] greens = {0xFF2E7D32, 0xFF388E3C, 0xFF336B3B, 0xFF427A3D};

        for (int i = 0; i < treeCountLimit; i++) {
            float x = startX + spacing * (i + 0.5f) + (random.nextFloat() - 0.5f) * spacing * 0.45f;
            if (random.nextFloat() > 0.62f
                    || (sunriseX >= 0 && Math.abs(x - sunriseX) < 88 * density)
                    || (sunsetX >= 0 && Math.abs(x - sunsetX) < 24 * density)) {
                continue;
            }

            int tiers = 1 + random.nextInt(3);
            float tierHeight = (6 + random.nextFloat()) * density * treeScale;
            int green = greens[random.nextInt(greens.length)];
            drawPineTree(canvas, x, baseY, tierHeight, tiers, green, density);
        }
    }

    private void drawPineTree(Canvas canvas, float centerX, float baseY, float tierHeight,
                              int tiers, int green, float density) {
        float treeHeight = tierHeight * tiers;
        float trunkWidth = 2 * density * treeScale;
        float trunkHeight = Math.min(3 * density * treeScale, treeHeight * 0.2f);
        float foliageBaseY = baseY - trunkHeight;

        paint.setColor(green);
        for (int tier = 0; tier < tiers; tier++) {
            float topY = foliageBaseY - treeHeight + tier * tierHeight;
            float bottomY = topY + tierHeight;
            float angle = (30 + tier * 20) * (float) Math.PI / 180f;
            float halfBase = tierHeight * (float) Math.tan(angle / 2f);

            Path foliage = new Path();
            foliage.moveTo(centerX, topY);
            foliage.lineTo(centerX + halfBase, bottomY);
            foliage.lineTo(centerX - halfBase, bottomY);
            foliage.close();
            canvas.drawPath(foliage, paint);
        }

        paint.setColor(0xFF8D5A35);
        canvas.drawRect(centerX - trunkWidth / 2f, foliageBaseY,
                centerX + trunkWidth / 2f, baseY + 2 * density, paint);
    }

    private void drawSunMarker(Canvas canvas, float x, float trackY, float density, boolean sunrise) {
        float radius = 10 * density;
        float centerY = sunrise ? trackY - 2 * density : trackY - 12 * density;

        if (sunrise) {
            drawSunriseIcon(canvas, x, centerY, radius, density);
            drawSleepingRabbit(canvas, x + radius + 14 * density, trackY, density);
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
    }

    private void drawSleepingRabbit(Canvas canvas, float left, float trackY, float density) {
        canvas.save();
        canvas.translate(left, trackY);
        canvas.save();
        float headCenterX = 31 * density;
        float headCenterY = -8 * density;
        canvas.scale(1.7f, 1.7f, headCenterX, headCenterY);

        paint.setColor(0xFFFFEBD8);
        canvas.drawCircle(31 * density, -8 * density, 5.5f * density, paint);
        canvas.drawOval(new RectF(28 * density, -20 * density, 31.5f * density, -10 * density), paint);
        canvas.drawOval(new RectF(32 * density, -19 * density, 35.5f * density, -10 * density), paint);

        paint.setColor(0xFFFFC5D2);
        canvas.drawOval(new RectF(29 * density, -19 * density, 30.5f * density, -11 * density), paint);
        canvas.drawOval(new RectF(33 * density, -18 * density, 34.5f * density, -11 * density), paint);

        paint.setColor(0xFFB87870);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawArc(new RectF(28.5f * density, -10 * density, 31 * density, -7.5f * density),
                10, 150, false, paint);
        canvas.drawArc(new RectF(32 * density, -10 * density, 34.5f * density, -7.5f * density),
                10, 150, false, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(0xFFF2A1A8);
        canvas.drawCircle(27.5f * density, -8 * density, 1.2f * density, paint);
        canvas.drawCircle(35 * density, -8 * density, 1.2f * density, paint);
        canvas.restore();

        paint.setColor(0xFF8EC6D8);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        paint.setTextSize(7 * density);
        canvas.drawText("Z", 38 * density, -16 * density, paint);
        paint.setTextSize(5 * density);
        canvas.drawText("Z", 43 * density, -22 * density, paint);
        canvas.restore();
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
