// archivo: app/src/main/java/com/sechuranavigator/app/ui/CompassView.java
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

public class CompassView extends View {

    // ── Paints (pinceles de dibujo) ───────────────────────────────────────
    private final Paint circlePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint northPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint southPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardinalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ── Estado ─────────────────────────────────────────────────────────────
    private float currentBearing = 0f;   // ángulo actual de la brújula
    private float targetBearing  = 0f;   // ángulo objetivo (donde queremos llegar)
    private ValueAnimator rotationAnimator;

    // ── Constructores ──────────────────────────────────────────────────────
    public CompassView(Context context) {
        super(context);
        init();
    }

    public CompassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Fondo del círculo
        circlePaint.setColor(Color.parseColor("#0D1F3C"));
        circlePaint.setStyle(Paint.Style.FILL);

        // Anillo exterior
        ringPaint.setColor(Color.parseColor("#1E3A5F"));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);

        // Aguja Norte (roja)
        northPaint.setColor(Color.parseColor("#EF4444"));
        northPaint.setStyle(Paint.Style.FILL);

        // Aguja Sur (gris oscuro)
        southPaint.setColor(Color.parseColor("#4B5563"));
        southPaint.setStyle(Paint.Style.FILL);

        // Texto de cardinales
        cardinalPaint.setColor(Color.parseColor("#9CA3AF"));
        cardinalPaint.setTextSize(28f);
        cardinalPaint.setTextAlign(Paint.Align.CENTER);
        cardinalPaint.setFakeBoldText(true);

        // Marcas de los grados
        tickPaint.setColor(Color.parseColor("#1E3A5F"));
        tickPaint.setStrokeWidth(2f);

        // Círculo central — relleno
        centerPaint.setColor(Color.parseColor("#0D1F3C"));
        centerPaint.setStyle(Paint.Style.FILL);

        // Círculo central — borde azul (Paint separado)
        centerRingPaint.setColor(Color.parseColor("#60A5FA"));
        centerRingPaint.setStyle(Paint.Style.STROKE);
        centerRingPaint.setStrokeWidth(3f);
        centerRingPaint.setAntiAlias(true);
    }

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Actualiza el ángulo de la brújula con animación suave.
     * bearing: 0 = Norte, 90 = Este, 180 = Sur, 270 = Oeste
     */
    public void setBearing(float bearing) {
        targetBearing = bearing;

        // Calcular el camino más corto para rotar (evitar saltos de 350° a 10°)
        float delta = targetBearing - currentBearing;
        if (delta > 180)  delta -= 360;
        if (delta < -180) delta += 360;

        final float startBearing = currentBearing;
        final float endBearing   = currentBearing + delta;

        if (rotationAnimator != null) rotationAnimator.cancel();

        rotationAnimator = ValueAnimator.ofFloat(startBearing, endBearing);
        rotationAnimator.setDuration(300); // 300ms de animación suave
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.addUpdateListener(animation -> {
            currentBearing = (float) animation.getAnimatedValue();
            invalidate(); // solicitar redibujo
        });
        rotationAnimator.start();
    }

    // ── Dibujo ─────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(cx, cy) - 10f;

        // 1. Fondo del círculo
        canvas.drawCircle(cx, cy, radius, circlePaint);

        // 2. Anillo exterior
        canvas.drawCircle(cx, cy, radius, ringPaint);
        canvas.drawCircle(cx, cy, radius * 0.80f, ringPaint);

        // 3. Rotar el canvas según el bearing (la brújula gira, los cardinales quedan fijos)
        canvas.save();
        canvas.rotate(-currentBearing, cx, cy);

        // 4. Marcas de grados cada 10°
        for (int angle = 0; angle < 360; angle += 10) {
            float rad = (float) Math.toRadians(angle);
            float innerR = (angle % 30 == 0) ? radius * 0.78f : radius * 0.82f;
            float outerR = radius * 0.88f;
            canvas.drawLine(
                    cx + (float) Math.sin(rad) * innerR,
                    cy - (float) Math.cos(rad) * innerR,
                    cx + (float) Math.sin(rad) * outerR,
                    cy - (float) Math.cos(rad) * outerR,
                    tickPaint
            );
        }

        // 5. Letra N (roja)
        cardinalPaint.setColor(Color.parseColor("#EF4444"));
        canvas.drawText("N", cx, cy - radius * 0.60f + 10, cardinalPaint);

        // 6. Cardinales S, E, W (gris)
        cardinalPaint.setColor(Color.parseColor("#6B7280"));
        canvas.drawText("S", cx, cy + radius * 0.60f + 10, cardinalPaint);
        canvas.drawText("E", cx + radius * 0.60f, cy + 10, cardinalPaint);
        canvas.drawText("W", cx - radius * 0.60f, cy + 10, cardinalPaint);

        // 7. Agujas
        drawNeedle(canvas, cx, cy, radius);

        canvas.restore();

        // 8. Círculo central (encima de todo, no rota)
        canvas.drawCircle(cx, cy, radius * 0.10f, centerPaint);      // relleno oscuro
        canvas.drawCircle(cx, cy, radius * 0.10f, centerRingPaint);  // borde azul
    }

    private void drawNeedle(Canvas canvas, float cx, float cy, float radius) {
        float needleWidth = radius * 0.08f;
        float needleLengthN = radius * 0.55f; // hacia el Norte
        float needleLengthS = radius * 0.40f; // hacia el Sur

        // Aguja Norte (roja, apunta arriba)
        Path northNeedle = new Path();
        northNeedle.moveTo(cx, cy - needleLengthN);
        northNeedle.lineTo(cx - needleWidth, cy);
        northNeedle.lineTo(cx + needleWidth, cy);
        northNeedle.close();
        canvas.drawPath(northNeedle, northPaint);

        // Aguja Sur (gris, apunta abajo)
        Path southNeedle = new Path();
        southNeedle.moveTo(cx, cy + needleLengthS);
        southNeedle.lineTo(cx - needleWidth, cy);
        southNeedle.lineTo(cx + needleWidth, cy);
        southNeedle.close();
        canvas.drawPath(southNeedle, southPaint);
    }
}