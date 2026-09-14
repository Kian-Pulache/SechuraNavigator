// archivo: app/src/main/java/com/sechuranavigator/app/ui/NewWaypointSelectorActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.GnssListener;
import com.sechuranavigator.app.managers.GnssManager;
import com.sechuranavigator.app.models.GnssData;

import java.util.Locale;

public class NewWaypointSelectorActivity extends AppCompatActivity
        implements GnssListener {

    private TextView tvGpsAccuracy;
    private TextView tvGpsCoords;
    private TextView tvAccuracyBadge;

    private GnssManager gnssManager;
    private GnssData    lastGnssData;

    private static final int REQUEST_MAP_PICK = 3001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_waypoint_selector);

        // Vincular vistas
        tvGpsAccuracy  = findViewById(R.id.tvGpsAccuracy);
        tvGpsCoords    = findViewById(R.id.tvGpsCoords);
        tvAccuracyBadge = findViewById(R.id.tvAccuracyBadge);

        // Iniciar GPS para mostrar precisión actual en el badge
        gnssManager = new GnssManager(this);
        gnssManager.setListener(this);

        // Botón cancelar
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        // Opción 1 — Ubicación actual
        findViewById(R.id.optionCurrentLocation).setOnClickListener(v -> {
            Intent intent = new Intent(this, NewWaypointActivity.class);
            intent.putExtra(NewWaypointActivity.EXTRA_MODE,
                    NewWaypointActivity.MODE_CURRENT_LOCATION);
            // Pasar los datos GPS actuales para que no tenga que esperar
            if (lastGnssData != null) {
                intent.putExtra(NewWaypointActivity.EXTRA_LAT, lastGnssData.latitude);
                intent.putExtra(NewWaypointActivity.EXTRA_LON, lastGnssData.longitude);
                intent.putExtra(NewWaypointActivity.EXTRA_ALT, lastGnssData.altitude);
                intent.putExtra(NewWaypointActivity.EXTRA_ACC, lastGnssData.accuracy);
                intent.putExtra(NewWaypointActivity.EXTRA_SAT, lastGnssData.satelliteCount);
            }
            startActivity(intent);
            finish();
        });

        // Opción 2 — Marcar en el mapa
        findViewById(R.id.optionMap).setOnClickListener(v -> {
            Intent intent = new Intent(this, MapPickerActivity.class);
            startActivityForResult(intent, REQUEST_MAP_PICK);
        });

        // Opción 3 — Coordenadas manuales
        findViewById(R.id.optionManual).setOnClickListener(v -> {
            Intent intent = new Intent(this, NewWaypointActivity.class);
            intent.putExtra(NewWaypointActivity.EXTRA_MODE,
                    NewWaypointActivity.MODE_MANUAL);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        gnssManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gnssManager.stop();
    }

    // ── GnssListener — actualizar badge de precisión ───────────────────────

    @Override
    public void onGnssUpdate(GnssData data) {
        lastGnssData = data;

        // Actualizar barra de estado GPS
        tvGpsCoords.setText(String.format(Locale.US,
                "%.5f°S  %.5f°W",
                Math.abs(data.latitude), Math.abs(data.longitude)));

        // Badge de precisión con color dinámico
        String accuracyText = String.format(Locale.US,
                "Precisión: %.1f m · %d satélites",
                data.accuracy, data.satelliteCount);
        tvGpsAccuracy.setText(accuracyText);
        tvAccuracyBadge.setText(accuracyText);

        if (data.accuracy <= 5f) {
            tvGpsAccuracy.setTextColor(Color.parseColor("#10B981"));
            tvAccuracyBadge.setTextColor(Color.parseColor("#10B981"));
            tvAccuracyBadge.setBackgroundColor(
                    Color.parseColor("#0A1628"));
        } else if (data.accuracy <= 8f) {
            tvGpsAccuracy.setTextColor(Color.parseColor("#F59E0B"));
            tvAccuracyBadge.setTextColor(Color.parseColor("#F59E0B"));
            tvAccuracyBadge.setBackgroundColor(
                    Color.parseColor("#0A1628"));
        } else {
            tvGpsAccuracy.setTextColor(Color.parseColor("#EF4444"));
            tvAccuracyBadge.setTextColor(Color.parseColor("#EF4444"));
        }
    }

    @Override
    public void onGnssStatusChange(GnssListener.GnssStatus status) {
        if (status == GnssListener.GnssStatus.SEARCHING) {
            tvGpsAccuracy.setText("Buscando señal GPS...");
            tvGpsAccuracy.setTextColor(Color.parseColor("#6B7280"));
            tvAccuracyBadge.setText("Esperando GPS...");
            tvAccuracyBadge.setTextColor(Color.parseColor("#6B7280"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MAP_PICK &&
                resultCode == RESULT_OK && data != null) {

            double lat = data.getDoubleExtra("picked_lat", 0);
            double lon = data.getDoubleExtra("picked_lon", 0);

            // ← TOAST DE DIAGNÓSTICO
            android.widget.Toast.makeText(this,
                    "Coords recibidas: " + lat + ", " + lon,
                    android.widget.Toast.LENGTH_LONG).show();

            // Abrir el formulario con las coordenadas del punto marcado
            Intent intent = new Intent(this, NewWaypointActivity.class);
            intent.putExtra(NewWaypointActivity.EXTRA_MODE,
                    NewWaypointActivity.MODE_MANUAL);
            intent.putExtra(NewWaypointActivity.EXTRA_LAT, lat);
            intent.putExtra(NewWaypointActivity.EXTRA_LON, lon);
            intent.putExtra(NewWaypointActivity.EXTRA_ACC, 0f);
            intent.putExtra(NewWaypointActivity.EXTRA_SAT, 0);
            startActivity(intent);
            finish();
        }
    }
}