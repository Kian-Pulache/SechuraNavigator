// archivo: app/src/main/java/com/sechuranavigator/app/ui/TidesActivity.java
package com.sechuranavigator.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.TideManager;
import com.sechuranavigator.app.models.TideData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TidesActivity extends AppCompatActivity
        implements TideManager.TideListener {

    private DataCell     cellCoefficient, cellTideType,
            cellMoon, cellCurrentStatus;
    private TextView     tvDate, tvLastUpdate;
    private TideChartView tideChart;
    private LinearLayout containerExtremes;

    private TideManager tideManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tides);

        cellCoefficient  = findViewById(R.id.cellCoefficient);
        cellTideType     = findViewById(R.id.cellTideType);
        cellMoon         = findViewById(R.id.cellMoon);
        cellCurrentStatus = findViewById(R.id.cellCurrentStatus);
        tvDate           = findViewById(R.id.tvDate);
        tvLastUpdate     = findViewById(R.id.tvLastUpdate);
        tideChart        = findViewById(R.id.tideChart);
        containerExtremes = findViewById(R.id.containerExtremes);

        // Configurar etiquetas de celdas
        cellCoefficient.setLabel("COEFICIENTE");
        cellTideType.setLabel("MAREA");
        cellMoon.setLabel("FASE LUNAR");
        cellCurrentStatus.setLabel("AHORA");

        // Fecha de hoy
        SimpleDateFormat sdfDate = new SimpleDateFormat(
                "EEEE, dd MMM yyyy", new Locale("es", "PE"));
        tvDate.setText(sdfDate.format(new Date()));

        tideManager = new TideManager();
        tideManager.setListener(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        tideManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        tideManager.stop();
    }

    // ── TideListener ───────────────────────────────────────────────────────

    @Override
    public void onTideUpdate(TideData data) {
        // Celdas de datos principales
        cellCoefficient.setValue(String.valueOf(data.coefficient));
        cellCoefficient.setValueColor(
                data.coefficient >= 80 ? "#EF4444" :
                        data.coefficient >= 50 ? "#F59E0B" : "#60A5FA");

        cellTideType.setValue(data.tideType);
        cellTideType.setValueColor(
                "Viva".equals(data.tideType) ? "#EF4444" : "#60A5FA");

        cellMoon.setValue(data.moonPhase);

        String status = data.isRising ? "↑ Subiendo" : "↓ Bajando";
        cellCurrentStatus.setValue(status);
        cellCurrentStatus.setValueColor(data.isRising ? "#10B981" : "#60A5FA");

        // Gráfica
        tideChart.setData(data.hourlyPoints);

        // Tabla de extremos
        buildExtremesTable(data.extremes3Days);

        // Timestamp de última actualización
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);
        tvLastUpdate.setText("Act. " + sdf.format(new Date()));
    }

    // ── Tabla de extremos ──────────────────────────────────────────────────

    private void buildExtremesTable(List<TideData.TideExtreme> extremes) {
        containerExtremes.removeAllViews();
        if (extremes == null) return;

        // Encabezado
        addTableRow(containerExtremes, "HORA", "ALTURA", "TIPO", true);

        SimpleDateFormat sdfTime = new SimpleDateFormat(
                "EEE HH:mm", new Locale("es", "PE"));

        String lastDay = "";
        for (TideData.TideExtreme e : extremes) {
            // Separador de día
            SimpleDateFormat sdfDay = new SimpleDateFormat(
                    "dd MMM", new Locale("es", "PE"));
            String day = sdfDay.format(new Date(e.timestamp));
            if (!day.equals(lastDay)) {
                addDaySeparator(containerExtremes, day);
                lastDay = day;
            }

            String time   = sdfTime.format(new Date(e.timestamp));
            String height = String.format(Locale.US, "%.2f m", e.height);
            String type   = e.isHigh ? "▲ Pleamar" : "▼ Bajamar";
            addTableRow(containerExtremes, time, height, type, false);
        }
    }

    private void addTableRow(LinearLayout container,
                             String col1, String col2, String col3,
                             boolean isHeader) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        if (!isHeader) {
            row.setBackgroundColor(Color.parseColor("#050D1A"));
        }

        TextView tv1 = makeCell(col1, isHeader ? "#4B5563" : "#9CA3AF", 2);
        TextView tv2 = makeCell(col2, isHeader ? "#4B5563" : "#FFFFFF", 1);
        TextView tv3 = makeCell(col3, isHeader ? "#4B5563" :
                (col3.startsWith("▲") ? "#10B981" : "#60A5FA"), 1);

        row.addView(tv1);
        row.addView(tv2);
        row.addView(tv3);

        // Separador
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#0F1E30"));

        container.addView(row);
        container.addView(divider);
    }

    private void addDaySeparator(LinearLayout container, String day) {
        TextView tv = new TextView(this);
        tv.setText(day.toUpperCase());
        tv.setTextColor(Color.parseColor("#374151"));
        tv.setTextSize(9f);
        tv.setPadding(dp(12), dp(8), dp(12), dp(4));
        container.addView(tv);
    }

    private TextView makeCell(String text, String color, int weight) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(11f);
        return tv;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}