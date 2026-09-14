// archivo: app/src/main/java/com/sechuranavigator/app/ui/HomeActivity.java
package com.sechuranavigator.app.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.GnssListener;
import com.sechuranavigator.app.managers.GnssManager;
import com.sechuranavigator.app.managers.SettingsManager;
import com.sechuranavigator.app.managers.TideManager;
import com.sechuranavigator.app.models.GnssData;

import java.util.Locale;

public class HomeActivity extends AppCompatActivity implements GnssListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1001;

    // ── Vistas ─────────────────────────────────────────────────────────────
    private DataCell    cellLatitude, cellLongitude, cellAccuracy;
    private DataCell    cellSatellites, cellAltitude, cellBearing;
    private DataCell    cellTideStatus, cellTideHeight, cellTideNext;
    private CompassView compassView;
    private NavButton   btnMap, btnNewWaypoint, btnMyPoints;
    private NavButton   btnRoutes, btnTides, btnSettings;

    // ── Managers ───────────────────────────────────────────────────────────
    private GnssManager gnssManager;

    // Mareas
    private TideManager tideManager;

    // ── Ciclo de vida ──────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Verificar autenticación O modo local
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

        // Leer si el usuario eligió entrar sin cuenta
        boolean choseLocalMode = getSharedPreferences("sechura_settings", MODE_PRIVATE)
                .getBoolean("chose_local_mode", false);

        // Solo redirigir al login si NO tiene sesión Y NO eligió modo local
        if (user == null && !choseLocalMode) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }
        // Si llegamos aquí: o tiene cuenta, o eligió modo local → continuar normal

        // Si tiene cuenta activa, limpiar el flag de modo local
        // (por si anteriormente usó la app sin cuenta y luego se registró)
        if (user != null) {
            getSharedPreferences("sechura_settings", MODE_PRIVATE)
                    .edit()
                    .putBoolean("chose_local_mode", false)
                    .apply();
        }

        setContentView(R.layout.activity_main);
        applyScreenSettings();

        bindViews();
        configureViews();
        setupButtonListeners();

        gnssManager = new GnssManager(this);
        gnssManager.setListener(this);

        tideManager = new TideManager();
        tideManager.setListener(data -> {
            // Actualizar las 3 celdas de marea
            String status = data.isRising ? "↑ Sub" : "↓ Baja";
            cellTideStatus.setValue(status);
            cellTideStatus.setValueColor(data.isRising ? "#10B981" : "#60A5FA");

            cellTideHeight.setValue(
                    String.format(java.util.Locale.US, "%.2f m", data.currentHeight));

            if (data.nextHigh != null) {
                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
                cellTideNext.setValue(sdf.format(new java.util.Date(data.nextHigh.timestamp)));
            }
        });

        requestLocationPermissionIfNeeded();
        // Mostrar banner si aplica
        checkAndShowAccountBanner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasLocationPermission()) {
            gnssManager.start();
        }
        applyScreenSettings();
        tideManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        gnssManager.stop(); // detener GPS para ahorrar batería
        tideManager.stop();
    }

    // ── Vinculación de vistas ──────────────────────────────────────────────

    private void bindViews() {
        cellLatitude   = findViewById(R.id.cellLatitude);
        cellLongitude  = findViewById(R.id.cellLongitude);
        cellAccuracy   = findViewById(R.id.cellAccuracy);
        cellSatellites = findViewById(R.id.cellSatellites);
        cellAltitude   = findViewById(R.id.cellAltitude);
        cellBearing    = findViewById(R.id.cellBearing);

        cellTideStatus = findViewById(R.id.cellTideStatus);
        cellTideHeight = findViewById(R.id.cellTideHeight);
        cellTideNext   = findViewById(R.id.cellTideNext);

        compassView    = findViewById(R.id.compassView);

        btnMap          = findViewById(R.id.btnMap);
        btnNewWaypoint  = findViewById(R.id.btnNewWaypoint);
        btnMyPoints     = findViewById(R.id.btnMyPoints);
        btnRoutes       = findViewById(R.id.btnRoutes);
        btnTides        = findViewById(R.id.btnTides);
        btnSettings     = findViewById(R.id.btnSettings);
    }

    private void configureViews() {
        // Etiquetas de los datos GPS
        cellLatitude.setLabel("LATITUD");
        cellLatitude.setValue("—");

        cellLongitude.setLabel("LONGITUD");
        cellLongitude.setValue("—");

        cellAccuracy.setLabel("PRECISIÓN");
        cellAccuracy.setValue("—");

        cellSatellites.setLabel("SATÉLITES");
        cellSatellites.setValue("—");
        cellSatellites.setValueColor("#60A5FA"); // azul

        cellAltitude.setLabel("ALTITUD");
        cellAltitude.setValue("—");
        cellAltitude.setValueColor("#A78BFA"); // violeta

        cellBearing.setLabel("DIRECCIÓN");
        cellBearing.setValue("—");
        cellBearing.setValueColor("#FB923C"); // naranja

        // Datos de marea (estáticos por ahora, se integran en Módulo 7)
        cellTideStatus.setLabel("MAREA");
        cellTideStatus.setValue("↑ Sub");
        cellTideStatus.setValueColor("#10B981");

        cellTideHeight.setLabel("ALTURA");
        cellTideHeight.setValue("— m");
        cellTideHeight.setValueColor("#60A5FA");

        cellTideNext.setLabel("PRÓX. ALTA");
        cellTideNext.setValue("—:—");
        cellTideNext.setValueColor("#F59E0B");

        // Íconos y etiquetas de los botones
        btnMap.setIcon("🗺");
        btnMap.setLabel("Mapa");

        btnNewWaypoint.setIcon("📍");
        btnNewWaypoint.setLabel("Nuevo punto");
        btnNewWaypoint.setPrimary(true); // destacado en ámbar

        btnMyPoints.setIcon("🧭");
        btnMyPoints.setLabel("Mis puntos");

        btnRoutes.setIcon("🛤");
        btnRoutes.setLabel("Mis rutas");

        btnTides.setIcon("🌊");
        btnTides.setLabel("Mareas");

        btnSettings.setIcon("⚙");
        btnSettings.setLabel("Config");
    }

    private void setupButtonListeners() {
        // Por ahora muestran un Toast informativo.
        // En los módulos siguientes abrirán las Activities reales.
        btnMap.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MapActivity.class);
            startActivity(intent);
        });

        btnNewWaypoint.setOnClickListener(v ->
                startActivity(new Intent(this, NewWaypointSelectorActivity.class)));

        btnMyPoints.setOnClickListener(v ->
                startActivity(new Intent(this, WaypointListActivity.class)));

        btnRoutes.setOnClickListener(v ->
                startActivity(new Intent(this, RoutesActivity.class)));

        btnTides.setOnClickListener(v ->
                startActivity(new Intent(this, TidesActivity.class)));

        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    // ── GnssListener — recibir datos del GPS ──────────────────────────────

    @Override
    public void onGnssUpdate(GnssData data) {
        // Coordenadas — siempre mostrar, incluso aproximadas
        SettingsManager settings = new SettingsManager(this);
        String[] coords = settings.formatCoordinate(
                data.latitude, data.longitude).split(",");
        cellLatitude.setValue(coords.length > 0 ? coords[0] : "—");
        cellLongitude.setValue(coords.length > 1 ? coords[1] :
                String.format(Locale.US, "%.6f°", data.longitude));

        cellAltitude.setValue(
                String.format(Locale.US, "%.0f m", data.altitude));
        cellSatellites.setValue(
                String.valueOf(data.satelliteCount));

        // Precisión con color e indicador de aproximado
        String accuracyStr;
        if (data.isApproximate) {
            accuracyStr = String.format(Locale.US, "~%.0f m", data.accuracy);
            cellAccuracy.setValueColor("#F59E0B"); // ámbar = aproximado
        } else if (data.accuracy <= 5f) {
            accuracyStr = String.format(Locale.US, "%.1f m", data.accuracy);
            cellAccuracy.setValueColor("#10B981"); // verde = excelente
        } else {
            accuracyStr = String.format(Locale.US, "%.1f m", data.accuracy);
            cellAccuracy.setValueColor("#F59E0B"); // ámbar = aceptable
        }
        cellAccuracy.setValue(accuracyStr);

        // Dirección
        cellBearing.setValue(
                String.format(Locale.US, "%.0f° %s",
                        data.bearing, bearingToCardinal(data.bearing)));

        // Brújula — siempre rotar
        compassView.setBearing(data.bearing);
    }

    @Override
    public void onGnssStatusChange(GnssListener.GnssStatus status) {
        switch (status) {
            case SEARCHING:
                cellAccuracy.setValue("Buscando...");
                cellAccuracy.setValueColor("#6B7280");
                break;
            case READY:
                // Los datos ya se actualizan en onGnssUpdate
                break;
            case LOW_ACCURACY:
                cellAccuracy.setValueColor("#EF4444");
                break;
            case NO_PERMISSION:
                Toast.makeText(this,
                        "Se necesita permiso de ubicación", Toast.LENGTH_LONG).show();
                break;
        }
    }

    // ── Permisos ───────────────────────────────────────────────────────────

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissionIfNeeded() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    REQUEST_LOCATION_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                gnssManager.start();
            } else {
                onGnssStatusChange(GnssListener.GnssStatus.NO_PERMISSION);
            }
        }
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    /** Convierte grados a punto cardinal (N, NE, E, SE, S, SW, W, NW) */
    private String bearingToCardinal(float bearing) {
        String[] cardinals = {"N","NE","E","SE","S","SW","W","NW"};
        int index = (int) ((bearing + 22.5f) / 45f) % 8;
        return cardinals[index];
    }

    private void applyScreenSettings() {
        SettingsManager settings = new SettingsManager(this);
        if (settings.keepScreenOn()) {
            getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * Muestra el banner de "crea tu cuenta" si:
     * 1. El usuario está en modo local (sin cuenta)
     * 2. Hay conexión a internet
     * 3. No se mostró en las últimas 24 horas
     */
    private void checkAndShowAccountBanner() {
        // Condición 1: solo para usuarios sin cuenta
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) return; // tiene cuenta → no mostrar

        // Condición 2: verificar si hay internet
        android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkInfo net = cm.getActiveNetworkInfo();
        if (net == null || !net.isConnected()) return; // sin internet → no mostrar

        // Condición 3: no mostrar más de 1 vez por día
        android.content.SharedPreferences prefs =
                getSharedPreferences("sechura_settings", MODE_PRIVATE);
        long lastShown = prefs.getLong("banner_last_shown", 0);
        long oneDayMs  = 24 * 60 * 60 * 1000L;
        if (System.currentTimeMillis() - lastShown < oneDayMs) return;

        // Las 3 condiciones se cumplen → mostrar el banner
        android.view.View banner = findViewById(R.id.bannerCreateAccount);
        if (banner == null) return;
        banner.setVisibility(android.view.View.VISIBLE);

        // Guardar que ya se mostró hoy para no repetir en 24h
        prefs.edit()
                .putLong("banner_last_shown", System.currentTimeMillis())
                .apply();

        // Botón "Crear": lleva al login y borra el modo local
        // para que pueda crear su cuenta
        android.view.View btnCrear = findViewById(R.id.btnBannerCreateAccount);
        if (btnCrear != null) {
            btnCrear.setOnClickListener(v -> {
                // Limpiar modo local para que AuthActivity muestre el registro
                prefs.edit()
                        .putBoolean("chose_local_mode", false)
                        .apply();
                startActivity(new Intent(this, AuthActivity.class));
                // No llamar finish() → el usuario puede volver con "atrás"
            });
        }

        // Botón "✕": cerrar el banner sin hacer nada
        android.view.View btnCerrar = findViewById(R.id.btnBannerDismiss);
        if (btnCerrar != null) {
            btnCerrar.setOnClickListener(v ->
                    banner.setVisibility(android.view.View.GONE));
        }
    }
}