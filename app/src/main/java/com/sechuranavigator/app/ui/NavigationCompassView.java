// archivo: app/src/main/java/com/sechuranavigator/app/ui/NavigationCompassView.java
package com.sechuranavigator.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class NavigationCompassView extends View {

    // ── Paints ─────────────────────────────────────────────────────────────
    private final Paint bgPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint northPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint southPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint destPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardinalPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerRingPaint= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Estado ─────────────────────────────────────────────────────────────
    private float currentBearing        = 0f; // rumbo actual del bote
    private float bearingToDestination  = 0f; // rumbo hacia el destino
    private ValueAnimator compassAnimator;

    // ── Constructores ──────────────────────────────────────────────────────
    public NavigationCompassView(Context context) {
        super(context); init();
    }
    public NavigationCompassView(Context context, AttributeSet attrs) {
        super(context, attrs); init();
    }

    private void init() {
        bgPaint.setColor(Color.parseColor("#0D1F3C"));
        bgPaint.setStyle(Paint.Style.FILL);

        ringPaint.setColor(Color.parseColor("#1E3A5F"));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f);

        northPaint.setColor(Color.parseColor("#EF4444"));
        northPaint.setStyle(Paint.Style.FILL);

        southPaint.setColor(Color.parseColor("#4B5563"));
        southPaint.setStyle(Paint.Style.FILL);

        // Aguja ámbar = apunta al destino
        destPaint.setColor(Color.parseColor("#F59E0B"));
        destPaint.setStyle(Paint.Style.FILL);

        cardinalPaint.setColor(Color.parseColor("#9CA3AF"));
        cardinalPaint.setTextSize(26f);
        cardinalPaint.setTextAlign(Paint.Align.CENTER);
        cardinalPaint.setFakeBoldText(true);

        tickPaint.setColor(Color.parseColor("#1E3A5F"));
        tickPaint.setStrokeWidth(1.5f);

        centerPaint.setColor(Color.parseColor("#0D1F3C"));
        centerPaint.setStyle(Paint.Style.FILL);

        centerRingPaint.setColor(Color.parseColor("#60A5FA"));
        centerRingPaint.setStyle(Paint.Style.STROKE);
        centerRingPaint.setStrokeWidth(3f);

        labelPaint.setColor(Color.parseColor("#F59E0B"));
        labelPaint.setTextSize(20f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Actualiza el rumbo actual del bote (del sensor o GPS).
     * Anima la brújula suavemente.
     */
    public void setCurrentBearing(float bearing) {
        float delta = bearing - currentBearing;
        if (delta > 180)  delta -= 360;
        if (delta < -180) delta += 360;

        float target = currentBearing + delta;

        if (compassAnimator != null) compassAnimator.cancel();
        compassAnimator = ValueAnimator.ofFloat(currentBearing, target);
        compassAnimator.setDuration(300);
        compassAnimator.setInterpolator(new LinearInterpolator());
        compassAnimator.addUpdateListener(anim -> {
            currentBearing = (float) anim.getAnimatedValue();
            invalidate();
        });
        compassAnimator.start();
    }

    /**
     * Actualiza el rumbo hacia el destino.
     * No anima — se actualiza con cada nuevo dato GPS.
     */
    public void setBearingToDestination(float bearing) {
        bearingToDestination = bearing;
        invalidate();
    }

    // ── Dibujo ─────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float radius = Math.min(cx, cy) - 12f;

        // 1. Fondo
        canvas.drawCircle(cx, cy, radius, bgPaint);

        // 2. Anillos
        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawCircle(cx, cy, radius * 0.80f, ringPaint);

        // 3. Rotar canvas según el bearing del bote
        canvas.save();
        canvas.rotate(-currentBearing, cx, cy);

        // 4. Marcas de grado
        for (int angle = 0; angle < 360; angle += 10) {
            float rad   = (float) Math.toRadians(angle);
            float inner = (angle % 30 == 0) ? radius * 0.76f : radius * 0.82f;
            float outer = radius * 0.88f;
            canvas.drawLine(
                    cx + (float) Math.sin(rad) * inner,
                    cy - (float) Math.cos(rad) * inner,
                    cx + (float) Math.sin(rad) * outer,
                    cy - (float) Math.cos(rad) * outer,
                    tickPaint
            );
        }

        // 5. Cardinales
        cardinalPaint.setColor(Color.parseColor("#EF4444"));
        canvas.drawText("N", cx, cy - radius * 0.58f + 10, cardinalPaint);
        cardinalPaint.setColor(Color.parseColor("#6B7280"));
        canvas.drawText("S", cx, cy + radius * 0.58f + 10, cardinalPaint);
        canvas.drawText("E", cx + radius * 0.58f, cy + 10, cardinalPaint);
        canvas.drawText("W", cx - radius * 0.58f, cy + 10, cardinalPaint);

        // 6. Aguja Norte/Sur (roja/gris — indica orientación del bote)
        drawNeedle(canvas, cx, cy, radius, northPaint, southPaint, 0.55f, 0.38f);

        canvas.restore();

        // 7. Aguja de destino (ámbar — NO rota con la brújula)
        //    Apunta siempre en la dirección absoluta del destino
        canvas.save();
        canvas.rotate(bearingToDestination - currentBearing, cx, cy);
        drawDestinationArrow(canvas, cx, cy, radius);
        canvas.restore();

        // 8. Centro
        canvas.drawCircle(cx, cy, radius * 0.09f, centerPaint);
        canvas.drawCircle(cx, cy, radius * 0.09f, centerRingPaint);

        // 9. Etiqueta "DEST" cerca de la punta de la aguja ámbar
        float destRad = (float) Math.toRadians(bearingToDestination - currentBearing);
        float labelDist = radius * 0.45f;
        canvas.drawText("DEST",
                cx + (float) Math.sin(destRad) * labelDist,
                cy - (float) Math.cos(destRad) * labelDist + 8,
                labelPaint);
    }

    private void drawNeedle(Canvas canvas, float cx, float cy, float radius,
                            Paint northP, Paint southP,
                            float lenN, float lenS) {
        float w = radius * 0.07f;

        Path north = new Path();
        north.moveTo(cx, cy - radius * lenN);
        north.lineTo(cx - w, cy);
        north.lineTo(cx + w, cy);
        north.close();
        canvas.drawPath(north, northP);

        Path south = new Path();
        south.moveTo(cx, cy + radius * lenS);
        south.lineTo(cx - w, cy);
        south.lineTo(cx + w, cy);
        south.close();
        canvas.drawPath(south, southP);
    }

    private void drawDestinationArrow(Canvas canvas, float cx, float cy,
                                      float radius) {
        float len   = radius * 0.60f;
        float lenB  = radius * 0.30f;
        float w     = radius * 0.06f;

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2f);

        Path arrow = new Path();
        arrow.moveTo(cx, cy - len);
        arrow.lineTo(cx - w, cy);
        arrow.lineTo(cx, cy - lenB * 0.3f);
        arrow.lineTo(cx + w, cy);
        arrow.close();

        canvas.drawPath(arrow, destPaint);
        canvas.drawPath(arrow, border);
    }
}