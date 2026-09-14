// archivo: app/src/main/java/com/sechuranavigator/app/ui/SettingsActivity.java
package com.sechuranavigator.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.data.WaypointRepository;
import com.sechuranavigator.app.data.local.AppDatabase;
import com.sechuranavigator.app.managers.SettingsManager;
import com.sechuranavigator.app.managers.WaypointManager;
import com.sechuranavigator.app.utils.ExportManager;
import com.sechuranavigator.app.utils.ImportManager;

import java.io.File;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_IMPORT = 1001;

    private SettingsManager settings;
    private ExportManager   exportManager;
    private ImportManager   importManager;
    private WaypointManager waypointManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings        = new SettingsManager(this);
        exportManager   = new ExportManager(this);
        importManager   = new ImportManager(this);
        waypointManager = new WaypointManager(this);

        setupAllItems();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // ── Configuración de cada item ─────────────────────────────────────────

    private void setupAllItems() {
        // Botón Pro
        setupAction(R.id.itemPro, "⭐ Actualizar a Pro", () ->
                startActivity(new Intent(this,
                        com.sechuranavigator.app.ui.PremiumActivity.class)));
        // GPS
        setupSpinner(R.id.itemAccuracy,
                "Precisión mínima aceptable",
                "Solo guardar si precisión ≤ X metros",
                String.format("%.0f m", settings.getMinAccuracy()),
                new String[]{"5 m","8 m","12 m","15 m"},
                new float[]{5,8,12,15},
                idx -> settings.setMinAccuracy(new float[]{5,8,12,15}[idx])
        );

        setupSpinner(R.id.itemReadings,
                "Lecturas para promediar",
                "Muestras GPS antes de guardar",
                settings.getReadingsAvg() + " lecturas",
                new String[]{"5","10","15","20"},
                new float[]{5,10,15,20},
                idx -> settings.setReadingsAvg(new int[]{5,10,15,20}[idx])
        );

        // Motor de visitas
        setupSpinner(R.id.itemArrivalRadius,
                "Radio de llegada",
                "Distancia para detectar arribo",
                String.format("%.0f m", settings.getArrivalRadius()),
                new String[]{"15 m","25 m","40 m","50 m"},
                new float[]{15,25,40,50},
                idx -> settings.setArrivalRadius(new float[]{15,25,40,50}[idx])
        );

        setupSpinner(R.id.itemMinStay,
                "Permanencia mínima",
                "Tiempo para confirmar visita",
                settings.getMinStay() + " s",
                new String[]{"30 s","60 s","90 s","120 s"},
                new float[]{30,60,90,120},
                idx -> settings.setMinStay(new int[]{30,60,90,120}[idx])
        );

        setupToggle(R.id.itemAskProduction,
                "Pedir producción al salir",
                "Tarjeta al alejarse del punto",
                settings.askProduction(),
                settings::setAskProduction
        );

        // Unidades
        setupSpinner(R.id.itemCoordFormat,
                "Sistema de coordenadas",
                "Formato de posición GPS",
                settings.getCoordFormat(),
                new String[]{"DD","DMS","DDM","UTM"},
                null,
                idx -> settings.setCoordFormat(
                        new String[]{"DD","DMS","DDM","UTM"}[idx])
        );

        setupSpinner(R.id.itemDistanceUnit,
                "Unidad de distancia",
                "Para distancias y altitud",
                settings.getDistanceUnit().equals("m") ? "Metros" : "Pies",
                new String[]{"Metros","Pies"},
                null,
                idx -> settings.setDistanceUnit(
                        new String[]{"m","ft"}[idx])
        );

        setupSpinner(R.id.itemSpeedUnit,
                "Unidad de velocidad",
                "",
                settings.getSpeedUnit(),
                new String[]{"Nudos","km/h","m/s"},
                null,
                idx -> settings.setSpeedUnit(
                        new String[]{"kn","km/h","m/s"}[idx])
        );

        // Waypoints
        setupAction(R.id.itemManageSpecies,
                "Gestionar especies",
                () -> startActivity(new Intent(this, SpeciesActivity.class))
        );

        setupAction(R.id.itemExportKml,
                "Exportar waypoints como KML",
                this::exportWaypointsKml
        );

        setupAction(R.id.itemExportGpx,
                "Exportar waypoints como GPX",
                this::exportWaypointsGpx
        );

        setupAction(R.id.itemImportWaypoints,
                "Importar waypoints (KML / GPX)",
                this::openImportPicker
        );

        // Pantalla
        setupToggle(R.id.itemKeepScreen,
                "Pantalla siempre encendida",
                "Recomendado en navegación",
                settings.keepScreenOn(),
                settings::setKeepScreenOn
        );

        setupToggle(R.id.itemMaxBrightness,
                "Brillo al máximo en mapa",
                "Para visibilidad bajo el sol",
                settings.maxBrightness(),
                settings::setMaxBrightness
        );

        // Datos
        setupAction(R.id.itemDeleteAll,
                "⚠ Borrar todos los datos",
                this::confirmDeleteAll
        );
        // Colorear rojo el item de borrar
        View deleteItem = findViewById(R.id.itemDeleteAll);
        TextView deleteTitle = deleteItem.findViewById(R.id.tvSettingTitle);
        deleteTitle.setTextColor(android.graphics.Color.parseColor("#FCA5A5"));

        // Cuenta
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String accountLabel = user != null
                ? "Cuenta: " + user.getEmail()
                : "Sin cuenta — toca para iniciar sesión";

        setupAction(R.id.itemAccount, accountLabel, () -> {
            com.google.firebase.auth.FirebaseUser currentUser =
                    com.google.firebase.auth.FirebaseAuth
                            .getInstance().getCurrentUser();

            if (currentUser == null) {
                // Sin sesión → abrir pantalla de login/registro
                // Limpiar modo local para que AuthActivity muestre el login
                getSharedPreferences("sechura_settings", MODE_PRIVATE)
                        .edit()
                        .putBoolean("chose_local_mode", false)
                        .apply();
                startActivity(new android.content.Intent(
                        this,
                        com.sechuranavigator.app.ui.AuthActivity.class));
            } else {
                // Con sesión → mostrar info de la cuenta
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Tu cuenta")
                        .setMessage(
                                "Sesión activa como:\n" + currentUser.getEmail() +
                                        "\n\nTus puntos y rutas se sincronizan automáticamente " +
                                        "con la nube.")
                        .setPositiveButton("Cerrar", null)
                        .show();
            }
        });

        setupAction(R.id.itemLogout, "Cerrar sesión", () ->
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Cerrar sesión")
                        .setMessage("¿Cerrar sesión? Tus datos locales se mantienen.")
                        .setPositiveButton("Cerrar sesión", (d, w) -> {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                            startActivity(new Intent(this,
                                    com.sechuranavigator.app.ui.AuthActivity.class));
                            finish();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    // ── Helpers de construcción ────────────────────────────────────────────

    private void setupToggle(int viewId, String title, String subtitle,
                             boolean initialValue, OnBooleanChange onChange) {
        View item     = findViewById(viewId);
        TextView tv1  = item.findViewById(R.id.tvSettingTitle);
        TextView tv2  = item.findViewById(R.id.tvSettingSubtitle);
        Switch   sw   = item.findViewById(R.id.switchSetting);

        tv1.setText(title);
        tv2.setText(subtitle);
        sw.setChecked(initialValue);
        sw.setTrackTintList(android.content.res.ColorStateList.valueOf(
                initialValue ? android.graphics.Color.parseColor("#10B981")
                        : android.graphics.Color.parseColor("#1E3A5F")));

        sw.setOnCheckedChangeListener((b, checked) -> {
            onChange.onChange(checked);
            sw.setTrackTintList(android.content.res.ColorStateList.valueOf(
                    checked ? android.graphics.Color.parseColor("#10B981")
                            : android.graphics.Color.parseColor("#1E3A5F")));
        });
    }

    private void setupSpinner(int viewId, String title, String subtitle,
                              String currentValue, String[] options,
                              float[] numericValues, OnIndexChange onChange) {
        View item    = findViewById(viewId);
        TextView tv1 = item.findViewById(R.id.tvSettingTitle);
        TextView tv2 = item.findViewById(R.id.tvSettingSubtitle);
        TextView tvV = item.findViewById(R.id.tvSettingValue);

        tv1.setText(title);
        tv2.setText(subtitle);
        tvV.setText(currentValue);

        tvV.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setItems(options, (d, idx) -> {
                            tvV.setText(options[idx]);
                            onChange.onChange(idx);
                        })
                        .show()
        );
    }

    private void setupAction(int viewId, String title, Runnable action) {
        View item    = findViewById(viewId);
        TextView tv1 = item.findViewById(R.id.tvSettingTitle);
        tv1.setText(title);
        item.setOnClickListener(v -> action.run());
    }

    // ── Exportación ────────────────────────────────────────────────────────

    private void exportWaypointsKml() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.util.List<com.sechuranavigator.app.data.local.entities.WaypointEntity>
                        wps = AppDatabase.getInstance(this).waypointDao().getAll();
                File file = exportManager.exportWaypointsKml(wps);
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            wps.size() + " waypoints exportados",
                            Toast.LENGTH_SHORT).show();
                    startActivity(Intent.createChooser(
                            exportManager.createShareIntent(file),
                            "Compartir KML"));
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        });
    }

    private void exportWaypointsGpx() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                java.util.List<com.sechuranavigator.app.data.local.entities.WaypointEntity>
                        wps = AppDatabase.getInstance(this).waypointDao().getAll();
                File file = exportManager.exportWaypointsGpx(wps);
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            wps.size() + " waypoints exportados",
                            Toast.LENGTH_SHORT).show();
                    startActivity(Intent.createChooser(
                            exportManager.createShareIntent(file),
                            "Compartir GPX"));
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        });
    }

    // ── Importación ────────────────────────────────────────────────────────

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{
                        "application/vnd.google-earth.kml+xml",
                        "application/gpx+xml",
                        "*/*"
                });
        startActivityForResult(
                Intent.createChooser(intent, "Seleccionar KML o GPX"),
                REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT &&
                resultCode == Activity.RESULT_OK &&
                data != null && data.getData() != null) {

            Uri uri = data.getData();
            Toast.makeText(this, "Importando...",
                    Toast.LENGTH_SHORT).show();

            importManager.importFromUri(uri, (count, error) -> {
                if (error != null) {
                    Toast.makeText(this,
                            "Error al importar: " + error,
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this,
                            "✓ " + count + " waypoints importados",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // ── Borrar todos los datos ─────────────────────────────────────────────

    private void confirmDeleteAll() {
        new AlertDialog.Builder(this)
                .setTitle("⚠ Borrar todos los datos")
                .setMessage("Se eliminarán TODOS los waypoints, rutas y visitas. " +
                        "Esta acción no se puede deshacer.")
                .setPositiveButton("Borrar todo", (d, w) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase.getInstance(this)
                                .waypointDao().deleteAll();
                        runOnUiThread(() ->
                                Toast.makeText(this,
                                        "Todos los datos eliminados",
                                        Toast.LENGTH_SHORT).show());
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ── Interfaces funcionales ─────────────────────────────────────────────

    interface OnBooleanChange { void onChange(boolean value); }
    interface OnIndexChange   { void onChange(int index); }
}