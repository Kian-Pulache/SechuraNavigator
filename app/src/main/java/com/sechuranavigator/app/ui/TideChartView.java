// archivo: app/src/main/java/com/sechuranavigator/app/ui/TideChartView.java
package com.sechuranavigator.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.sechuranavigator.app.models.TideData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TideChartView extends View {

    private List<TideData.HourlyPoint> points;

    private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TideChartView(Context context) {
        super(context); init();
    }
    public TideChartView(Context context, AttributeSet attrs) {
        super(context, attrs); init();
    }

    private void init() {
        linePaint.setColor(Color.parseColor("#60A5FA"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(4f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint.setColor(Color.parseColor("#1560A5FA"));
        fillPaint.setStyle(Paint.Style.FILL);

        nowPaint.setColor(Color.parseColor("#F59E0B"));
        nowPaint.setStyle(Paint.Style.STROKE);
        nowPaint.setStrokeWidth(2f);
        nowPaint.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{8, 6}, 0));

        gridPaint.setColor(Color.parseColor("#1E3A5F"));
        gridPaint.setStrokeWidth(1f);

        textPaint.setColor(Color.parseColor("#6B7280"));
        textPaint.setTextSize(24f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        dotPaint.setColor(Color.parseColor("#F59E0B"));
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setData(List<TideData.HourlyPoint> points) {
        this.points = points;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (points == null || points.isEmpty()) return;

        int w = getWidth();
        int h = getHeight();
        float padL = 60f, padR = 20f, padT = 20f, padB = 35f;
        float chartW = w - padL - padR;
        float chartH = h - padT - padB;

        // Encontrar rango de alturas
        double minH = Double.MAX_VALUE, maxH = Double.MIN_VALUE;
        for (TideData.HourlyPoint p : points) {
            if (p.height < minH) minH = p.height;
            if (p.height > maxH) maxH = p.height;
        }
        double range = maxH - minH;
        if (range < 0.1) range = 0.1;
        // Dar margen vertical
        minH -= range * 0.1;
        maxH += range * 0.1;
        range = maxH - minH;

        int n = points.size();

        // Convertir puntos a coordenadas de pantalla
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = padL + (i / (float)(n - 1)) * chartW;
            ys[i] = padT + chartH - (float)((points.get(i).height - minH) / range) * chartH;
        }

        // Líneas de grid horizontales
        for (int g = 0; g <= 4; g++) {
            float gy = padT + g * (chartH / 4);
            canvas.drawLine(padL, gy, padL + chartW, gy, gridPaint);
            double labelH = maxH - g * (range / 4);
            canvas.drawText(String.format(Locale.US, "%.1f", labelH),
                    padL - 8, gy + 8, textPaint);
        }

        // Relleno bajo la curva
        Path fillPath = new Path();
        fillPath.moveTo(xs[0], padT + chartH);
        for (int i = 0; i < n; i++) {
            fillPath.lineTo(xs[i], ys[i]);
        }
        fillPath.lineTo(xs[n-1], padT + chartH);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);

        // Línea de la curva
        Path linePath = new Path();
        linePath.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            // Curva suavizada con control points
            float cx = (xs[i-1] + xs[i]) / 2;
            linePath.cubicTo(cx, ys[i-1], cx, ys[i], xs[i], ys[i]);
        }
        canvas.drawPath(linePath, linePaint);

        // Línea vertical "ahora"
        canvas.drawLine(xs[0], padT, xs[0], padT + chartH, nowPaint);
        canvas.drawCircle(xs[0], ys[0], 8f, dotPaint);

        // Etiquetas de hora cada 6 horas
        SimpleDateFormat sdf = new SimpleDateFormat("HH", Locale.US);
        for (int i = 0; i < n; i += 6) {
            String label = sdf.format(new Date(points.get(i).timestamp)) + "h";
            canvas.drawText(label, xs[i], h - 6, textPaint);
        }
    }
}