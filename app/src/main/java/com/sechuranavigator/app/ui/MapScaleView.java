// archivo: app/src/main/java/com/sechuranavigator/app/ui/MapScaleView.java
package com.sechuranavigator.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class MapScaleView extends View {

    private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Distancia real que representa la escala (en metros)
    private double scaleMeters = 0;
    // Ancho en píxeles que ocupa esa distancia en pantalla
    private float  scaleWidthPx = 0;
    // Texto a mostrar ("50 m", "1 km", etc.)
    private String scaleLabel = "";

    public MapScaleView(Context context) {
        super(context); init();
    }
    public MapScaleView(Context context, AttributeSet attrs) {
        super(context, attrs); init();
    }

    private void init() {
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        shadowPaint.setColor(Color.BLACK);
        shadowPaint.setTextSize(28f);
        shadowPaint.setFakeBoldText(true);
        shadowPaint.setTextAlign(Paint.Align.CENTER);
        shadowPaint.setAlpha(150);
    }

    /**
     * Actualiza la escala según el nivel de zoom y latitud actual del mapa.
     *
     * @param zoomLevel  nivel de zoom actual de OsmDroid
     * @param latitude   latitud del centro del mapa (para corrección de Mercator)
     */
    public void updateScale(double zoomLevel, double latitude) {
        // Metros por píxel a este zoom y latitud
        // Fórmula: (circunferencia_tierra × cos(lat)) / (256 × 2^zoom)
        double earthCircumference = 40_075_016.686; // metros
        double metersPerPx = (earthCircumference
                * Math.cos(Math.toRadians(latitude)))
                / (256.0 * Math.pow(2, zoomLevel));

        // Ancho disponible para la escala (70% del ancho de la vista)
        float availablePx = getWidth() * 0.7f;
        double maxMeters  = availablePx * metersPerPx;

        // Elegir una distancia "redonda" que quepa en el espacio disponible
        double[] niceDistances = {
                1, 2, 5, 10, 20, 50, 100, 200, 500,
                1000, 2000, 5000, 10000, 20000, 50000
        };

        double chosen = niceDistances[0];
        for (double d : niceDistances) {
            if (d <= maxMeters) chosen = d;
            else break;
        }

        scaleMeters   = chosen;
        scaleWidthPx  = (float) (chosen / metersPerPx);

        // Etiqueta
        if (chosen >= 1000) {
            scaleLabel = String.format(java.util.Locale.US,
                    "%.0f km", chosen / 1000);
        } else {
            scaleLabel = String.format(java.util.Locale.US,
                    "%.0f m", chosen);
        }

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (scaleWidthPx <= 0 || scaleLabel.isEmpty()) return;

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        float left  = cx - scaleWidthPx / 2f;
        float right = cx + scaleWidthPx / 2f;
        float barY  = cy + 8f;

        // Sombra (para legibilidad sobre el mapa)
        shadowPaint.setStrokeWidth(5f);
        shadowPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(left, barY, right, barY, shadowPaint);
        canvas.drawLine(left,  barY - 10, left,  barY + 4, shadowPaint);
        canvas.drawLine(right, barY - 10, right, barY + 4, shadowPaint);
        canvas.drawText(scaleLabel, cx, barY - 14, shadowPaint);

        // Barra blanca
        canvas.drawLine(left, barY, right, barY, linePaint);
        canvas.drawLine(left,  barY - 10, left,  barY + 4, linePaint);
        canvas.drawLine(right, barY - 10, right, barY + 4, linePaint);

        // Texto
        canvas.drawText(scaleLabel, cx, barY - 14, textPaint);
    }
}