// archivo: app/src/main/java/com/sechuranavigator/app/ui/NewWaypointActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.GnssListener;
import com.sechuranavigator.app.managers.GnssManager;
import com.sechuranavigator.app.managers.WaypointManager;
import com.sechuranavigator.app.models.GnssData;

import java.util.Locale;

public class NewWaypointActivity extends AppCompatActivity implements GnssListener {

    // ── Constantes de modo ────────────────────────────────────────────────
    public static final String EXTRA_MODE = "waypoint_mode";
    public static final String EXTRA_LAT  = "lat";
    public static final String EXTRA_LON  = "lon";
    public static final String EXTRA_ALT  = "alt";
    public static final String EXTRA_ACC  = "acc";
    public static final String EXTRA_SAT  = "sat";

    public static final int MODE_CURRENT_LOCATION = 0;
    public static final int MODE_MAP              = 1;
    public static final int MODE_MANUAL           = 2;

    // ── Vistas ─────────────────────────────────────────────────────────────
    private TextView tvGpsCoords, tvGpsAccuracy;
    private EditText etName, etDescription;
    private TextView chipConcha, chipPulpo, chipPescado;
    private TextView chipLangosta, chipCaracol, chipOtro;

    // ── Estado ─────────────────────────────────────────────────────────────
    private GnssData  lastGnssData;     // último dato GPS recibido
    // Lista de especies seleccionadas (multi-selección)
    private final java.util.List<String> selectedSpecies = new java.util.ArrayList<>();

    // ── Managers ───────────────────────────────────────────────────────────
    private GnssManager     gnssManager;
    private WaypointManager waypointManager;
    private int currentMode = MODE_CURRENT_LOCATION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_waypoint);

        bindViews();
        setupChips();
        setupButtons();

        gnssManager     = new GnssManager(this);
        waypointManager = new WaypointManager(this);
        gnssManager.setListener(this);

        currentMode = getIntent().getIntExtra(EXTRA_MODE, MODE_CURRENT_LOCATION);
        applyMode();
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

    // ── Vinculación de vistas ──────────────────────────────────────────────

    private void bindViews() {
        tvGpsCoords   = findViewById(R.id.tvGpsCoords);
        tvGpsAccuracy = findViewById(R.id.tvGpsAccuracy);
        etName        = findViewById(R.id.etName);
        etDescription = findViewById(R.id.etDescription);
        chipConcha    = findViewById(R.id.chipConcha);
        chipPulpo     = findViewById(R.id.chipPulpo);
        chipPescado   = findViewById(R.id.chipPescado);
        chipLangosta  = findViewById(R.id.chipLangosta);
        chipCaracol   = findViewById(R.id.chipCaracol);
        chipOtro      = findViewById(R.id.chipOtro);
    }

    // ── Chips de especie ───────────────────────────────────────────────────

    private void setupChips() {
        TextView[] chips = {chipConcha, chipPulpo, chipPescado,
                chipLangosta, chipCaracol, chipOtro};
        String[]   names = {"Concha de abanico", "Pulpo", "Pescado",
                "Langosta", "Caracol rosado", null};

        for (int i = 0; i < chips.length; i++) {
            final String species = names[i];
            final TextView chip  = chips[i];
            chip.setOnClickListener(v ->
                handleSpeciesChipToggle(chip, species, chips, names));
        }
    }

    /**
     * Toggle de selección múltiple para chips de especie.
     * Si ya estaba seleccionada → la quita. Si no → la agrega.
     */
    private void handleSpeciesChipToggle(TextView chip, String species,
                                          TextView[] allChips, String[] allNames) {
        if (species == null) return; // chipOtro — sin acción

        if (selectedSpecies.contains(species)) {
            // Ya estaba seleccionada → deseleccionar
            selectedSpecies.remove(species);
            chip.setBackgroundColor(Color.parseColor("#0D1F3C"));
            chip.setTextColor(Color.parseColor("#6B7280"));
        } else {
            // No estaba → seleccionar
            selectedSpecies.add(species);
            String color = WaypointManager.getColorForSpecies(species);
            chip.setBackgroundColor(Color.parseColor(color + "40"));
            chip.setTextColor(Color.parseColor(color));
        }
    }

    // ── Botones ────────────────────────────────────────────────────────────

    private void setupButtons() {
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveWaypoint());
        findViewById(R.id.btnSaveBottom).setOnClickListener(v -> saveWaypoint());
    }

    private void saveWaypoint() {
        if (lastGnssData == null || !lastGnssData.hasValidFix) {
            Toast.makeText(this,
                    "Esperando señal GPS...", Toast.LENGTH_SHORT).show();
            return;
        }

        String name        = etName.getText().toString().trim();
        String description = etDescription != null
                ? etDescription.getText().toString().trim()
                : "";

        // Convertir la lista de especies a String separado por comas
        String speciesStr = selectedSpecies.isEmpty()
                ? null
                : android.text.TextUtils.join(",", selectedSpecies);

        waypointManager.saveFromCurrentLocation(
                lastGnssData,
                name,
                speciesStr,
                description,
                newId -> {
                    // Este callback llega desde un hilo de background
                    // Para tocar la UI debemos volver al hilo principal
                    runOnUiThread(() -> {
                        if (newId > 0) {
                            Toast.makeText(this,
                                    "✓ Waypoint guardado", Toast.LENGTH_SHORT).show();
                            finish(); // volver a HomeActivity
                        } else {
                            Toast.makeText(this,
                                    "Error al guardar — sin fix GPS",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
        );
    }

    // ── GnssListener ───────────────────────────────────────────────────────

    @Override
    public void onGnssUpdate(GnssData data) {
        lastGnssData = data;

        tvGpsCoords.setText(String.format(Locale.US,
                "%.6f° S  %.6f° W", Math.abs(data.latitude), Math.abs(data.longitude)));

        String accuracyText = String.format(Locale.US,
                "Precisión: %.1f m · %d satélites", data.accuracy, data.satelliteCount);
        tvGpsAccuracy.setText(accuracyText);

        // Color según calidad de señal
        if (data.accuracy <= 5f) {
            tvGpsCoords.setTextColor(Color.parseColor("#10B981"));
        } else if (data.accuracy <= 8f) {
            tvGpsCoords.setTextColor(Color.parseColor("#F59E0B"));
        } else {
            tvGpsCoords.setTextColor(Color.parseColor("#EF4444"));
        }
    }

    @Override
    public void onGnssStatusChange(GnssListener.GnssStatus status) {
        if (status == GnssListener.GnssStatus.SEARCHING) {
            tvGpsCoords.setText("Buscando señal GPS...");
            tvGpsCoords.setTextColor(Color.parseColor("#6B7280"));
        }
    }

    private void applyMode() {
        switch (currentMode) {

            case MODE_CURRENT_LOCATION:
                // Ya funciona como estaba — el GPS se inicia en onResume
                // Si nos pasaron datos GPS desde el Selector, mostrarlos ya
                if (getIntent().hasExtra(EXTRA_LAT)) {
                    GnssData preloaded = new GnssData();
                    preloaded.latitude       = getIntent().getDoubleExtra(EXTRA_LAT, 0);
                    preloaded.longitude      = getIntent().getDoubleExtra(EXTRA_LON, 0);
                    preloaded.altitude       = getIntent().getDoubleExtra(EXTRA_ALT, 0);
                    preloaded.accuracy       = getIntent().getFloatExtra(EXTRA_ACC, 0);
                    preloaded.satelliteCount = getIntent().getIntExtra(EXTRA_SAT, 0);
                    preloaded.hasValidFix    = true;
                    // Actualizar la UI inmediatamente con los datos pre-cargados
                    onGnssUpdate(preloaded);
                    lastGnssData = preloaded;
                }
                break;

            case MODE_MAP:
                // Cambiar el título y ocultar la card de GPS
                // Mostrar un mensaje explicativo
                tvGpsCoords.setText("Abre el mapa y toca el punto exacto");
                tvGpsAccuracy.setText(
                        "La precisión dependerá del punto que marques");
                tvGpsCoords.setTextColor(
                        android.graphics.Color.parseColor("#60A5FA"));

                // Cambiar el botón guardar para abrir el mapa primero
                // Cuando vuelva del mapa, el punto estará pre-cargado
                findViewById(R.id.btnSaveBottom).setOnClickListener(v ->
                        openMapPicker());
                break;

            case MODE_MANUAL:
                showManualCoordFields();
                break;
        }
    }

    private static final int REQUEST_MAP_PICK = 2001;

    private void openMapPicker() {
        Intent intent = new Intent(this, MapPickerActivity.class);
        startActivityForResult(intent, REQUEST_MAP_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MAP_PICK &&
                resultCode == RESULT_OK && data != null) {

            double lat = data.getDoubleExtra("picked_lat", 0);
            double lon = data.getDoubleExtra("picked_lon", 0);

            // Crear un GnssData falso con las coordenadas elegidas
            GnssData picked = new GnssData();
            picked.latitude    = lat;
            picked.longitude   = lon;
            picked.altitude    = 0;
            picked.accuracy    = 0;  // sin precisión para punto manual del mapa
            picked.hasValidFix = true;
            lastGnssData = picked;

            // Actualizar la UI con las coordenadas elegidas
            tvGpsCoords.setText(String.format(java.util.Locale.US,
                    "%.6f° S  %.6f° W",
                    Math.abs(lat), Math.abs(lon)));
            tvGpsAccuracy.setText("Punto marcado en el mapa");
            tvGpsCoords.setTextColor(
                    android.graphics.Color.parseColor("#10B981"));
        }
    }

    private void showManualCoordFields() {
        tvGpsCoords.setText("Ingresa las coordenadas manualmente");
        tvGpsAccuracy.setText("Formato: grados decimales (ej: -5.628431)");
        tvGpsCoords.setTextColor(
                android.graphics.Color.parseColor("#A78BFA"));

        android.widget.LinearLayout parent =
                (android.widget.LinearLayout) tvGpsCoords.getParent().getParent();

        // Etiqueta latitud
        android.widget.TextView lblLat = new android.widget.TextView(this);
        lblLat.setText("LATITUD");
        lblLat.setTextColor(android.graphics.Color.parseColor("#6B7280"));
        lblLat.setTextSize(9f);

        // Campo latitud
        android.widget.EditText etLat = new android.widget.EditText(this);
        etLat.setHint("Latitud (ej: -5.628431)");
        etLat.setTextColor(android.graphics.Color.WHITE);
        etLat.setHintTextColor(android.graphics.Color.parseColor("#4B5563"));
        etLat.setInputType(
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                        android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etLat.setBackgroundColor(
                android.graphics.Color.parseColor("#0D1F3C"));
        etLat.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.widget.LinearLayout.LayoutParams latParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        latParams.setMargins(0, dp(8), 0, dp(4));
        etLat.setLayoutParams(latParams);

        // ← PRE-LLENAR si vienen del mapa
        if (getIntent().hasExtra(EXTRA_LAT)) {
            etLat.setText(String.valueOf(
                    getIntent().getDoubleExtra(EXTRA_LAT, 0)));
        }

        // Etiqueta longitud
        android.widget.TextView lblLon = new android.widget.TextView(this);
        lblLon.setText("LONGITUD");
        lblLon.setTextColor(android.graphics.Color.parseColor("#6B7280"));
        lblLon.setTextSize(9f);

        // Campo longitud
        android.widget.EditText etLon = new android.widget.EditText(this);
        etLon.setHint("Longitud (ej: -80.851209)");
        etLon.setTextColor(android.graphics.Color.WHITE);
        etLon.setHintTextColor(android.graphics.Color.parseColor("#4B5563"));
        etLon.setInputType(
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL |
                        android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        etLon.setBackgroundColor(
                android.graphics.Color.parseColor("#0D1F3C"));
        etLon.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.widget.LinearLayout.LayoutParams lonParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lonParams.setMargins(0, 0, 0, dp(8));
        etLon.setLayoutParams(lonParams);

        // ← PRE-LLENAR si vienen del mapa
        if (getIntent().hasExtra(EXTRA_LON)) {
            etLon.setText(String.valueOf(
                    getIntent().getDoubleExtra(EXTRA_LON, 0)));
        }

        // Agregar al layout
        parent.addView(lblLat, 1);
        parent.addView(etLat, 2);
        parent.addView(lblLon, 3);
        parent.addView(etLon, 4);

        // Botón guardar lee los campos
        findViewById(R.id.btnSaveBottom).setOnClickListener(v -> {
            String latStr = etLat.getText().toString().trim();
            String lonStr = etLon.getText().toString().trim();

            if (latStr.isEmpty() || lonStr.isEmpty()) {
                android.widget.Toast.makeText(this,
                        "Ingresa latitud y longitud",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double lat = Double.parseDouble(latStr);
                double lon = Double.parseDouble(lonStr);

                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    android.widget.Toast.makeText(this,
                            "Coordenadas fuera de rango",
                            android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                String name    = etName.getText().toString().trim();
                String species = selectedSpecies.isEmpty()
                        ? null
                        : android.text.TextUtils.join(",", selectedSpecies);
                String desc = etDescription != null
                        ? etDescription.getText().toString().trim()
                        : "";

                waypointManager.saveManual(lat, lon, name, species, desc, newId ->
                        runOnUiThread(() -> {
                            if (newId > 0) {
                                android.widget.Toast.makeText(this,
                                        "✓ Waypoint guardado",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                android.widget.Toast.makeText(this,
                                        "Error al guardar",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        })
                );

            } catch (NumberFormatException e) {
                android.widget.Toast.makeText(this,
                        "Formato de coordenadas incorrecto",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value *
                getResources().getDisplayMetrics().density);
    }
}