// archivo: app/src/main/java/com/sechuranavigator/app/ui/WaypointDetailActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.data.WaypointRepository;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;
import com.sechuranavigator.app.managers.WaypointManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WaypointDetailActivity extends AppCompatActivity {

    public static final String EXTRA_WAYPOINT_ID = "waypoint_id";

    // ── Vistas ─────────────────────────────────────────────────────────────
    private TextView tvSpeciesIcon, tvCoordLat, tvCoordLon;
    private TextView tvAccuracy, tvSatellites, tvVisitCount, tvAltitude;
    private TextView tvCreatedAt, btnFavorite, btnSave;
    private EditText etName, etDescription;
    private TextView chipConcha, chipPulpo, chipPescado;
    private TextView chipLangosta, chipCaracol, chipOtro;

    // ── Estado ─────────────────────────────────────────────────────────────
    private WaypointEntity  waypoint;
    private WaypointRepository repository;
    private final java.util.List<String> selectedSpecies = new java.util.ArrayList<>();
    private boolean         hasChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waypoint_detail);

        bindViews();
        repository = new WaypointRepository(this);

        long waypointId = getIntent().getLongExtra(EXTRA_WAYPOINT_ID, -1L);
        if (waypointId < 0) { finish(); return; }

        // Cargar waypoint de la BD
        repository.getById(waypointId, wp -> {
            if (wp == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Punto no encontrado",
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            waypoint = wp;
            runOnUiThread(this::populateViews);
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
    }

    @Override
    public void onBackPressed() {
        if (hasChanges) {
            new AlertDialog.Builder(this)
                    .setTitle("Cambios sin guardar")
                    .setMessage("¿Descartar los cambios?")
                    .setPositiveButton("Descartar", (d, w) -> finish())
                    .setNegativeButton("Seguir editando", null)
                    .show();
        } else {
            finish();
        }
    }

    // ── Vincular vistas ────────────────────────────────────────────────────

    private void bindViews() {
        tvSpeciesIcon  = findViewById(R.id.tvSpeciesIcon);
        tvCoordLat     = findViewById(R.id.tvCoordLat);
        tvCoordLon     = findViewById(R.id.tvCoordLon);
        tvAccuracy     = findViewById(R.id.tvAccuracy);
        tvSatellites   = findViewById(R.id.tvSatellites);
        tvVisitCount   = findViewById(R.id.tvVisitCount);
        tvAltitude     = findViewById(R.id.tvAltitude);
        tvCreatedAt    = findViewById(R.id.tvCreatedAt);
        btnFavorite    = findViewById(R.id.btnFavorite);
        btnSave        = findViewById(R.id.btnSave);
        etName         = findViewById(R.id.etName);
        etDescription  = findViewById(R.id.etDescription);
        chipConcha     = findViewById(R.id.chipConcha);
        chipPulpo      = findViewById(R.id.chipPulpo);
        chipPescado    = findViewById(R.id.chipPescado);
        chipLangosta   = findViewById(R.id.chipLangosta);
        chipCaracol    = findViewById(R.id.chipCaracol);
        chipOtro       = findViewById(R.id.chipOtro);
    }

    // ── Poblar vistas con datos del waypoint ───────────────────────────────

    private void populateViews() {
        // Coordenadas
        tvCoordLat.setText(String.format(Locale.US,
                "%.6f° S", Math.abs(waypoint.latitude)));
        tvCoordLon.setText(String.format(Locale.US,
                "%.6f° W", Math.abs(waypoint.longitude)));

        // Stats
        tvAccuracy.setText(waypoint.accuracy > 0
                ? String.format(Locale.US, "%.1f m", waypoint.accuracy)
                : "—");
        tvSatellites.setText(waypoint.satelliteCount > 0
                ? String.valueOf(waypoint.satelliteCount)
                : "—");
        tvVisitCount.setText(String.valueOf(waypoint.visitCount));
        tvAltitude.setText(waypoint.altitude > 0
                ? String.format(Locale.US, "%.0f m", waypoint.altitude)
                : "—");

        // Fecha de creación
        SimpleDateFormat sdf = new SimpleDateFormat(
                "dd MMM yyyy · HH:mm", new Locale("es", "PE"));
        tvCreatedAt.setText(sdf.format(new Date(waypoint.createdAt)));

        // Nombre
        if (waypoint.name != null) etName.setText(waypoint.name);

        // Descripción
        if (waypoint.description != null)
            etDescription.setText(waypoint.description);

        // Especie y color — cargar múltiples especies si las hay
        selectedSpecies.clear();
        if (waypoint.species != null && !waypoint.species.isEmpty()) {
            for (String s : waypoint.species.split(",")) {
                selectedSpecies.add(s.trim());
            }
        }
        // Para el ícono usamos la primera especie
        String primarySpecies = selectedSpecies.isEmpty()
                ? null : selectedSpecies.get(0);
        updateSpeciesChipsMulti();
        updateSpeciesIcon(primarySpecies);

        // Favorito
        updateFavoriteButton(waypoint.isFavorite);

        // Configurar listeners ahora que los datos están cargados
        setupListeners();
    }

    // ── Chips de especie ───────────────────────────────────────────────────

    private void setupListeners() {
        // Chips
        TextView[] chips = {chipConcha, chipPulpo, chipPescado,
                chipLangosta, chipCaracol, chipOtro};
        String[]   names = {"Concha de abanico", "Pulpo", "Pescado",
                "Langosta", "Caracol rosado", null};

        for (int i = 0; i < chips.length; i++) {
            final String sp   = names[i];
            final TextView chip = chips[i];
            chip.setOnClickListener(v -> {
                if (sp == null) return; // chipOtro — sin acción
                if (selectedSpecies.contains(sp)) {
                    selectedSpecies.remove(sp);
                } else {
                    selectedSpecies.add(sp);
                }
                updateSpeciesChipsMulti();
                String primary = selectedSpecies.isEmpty()
                        ? null : selectedSpecies.get(0);
                updateSpeciesIcon(primary);
                markChanged();
            });
        }

        // Favorito
        btnFavorite.setOnClickListener(v -> {
            waypoint.isFavorite = !waypoint.isFavorite;
            updateFavoriteButton(waypoint.isFavorite);
            markChanged();
        });

        // Detectar cambios en campos de texto
        TextWatcher changeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                markChanged();
            }
        };
        etName.addTextChangedListener(changeWatcher);
        etDescription.addTextChangedListener(changeWatcher);

        // Guardar
        btnSave.setOnClickListener(v -> saveChanges());

        // Navegar
        findViewById(R.id.btnNavigate).setOnClickListener(v -> {
            Intent intent = new Intent(this, NavigationActivity.class);
            intent.putExtra(NavigationActivity.EXTRA_WAYPOINT_ID,
                    waypoint.id);
            startActivity(intent);
        });

        // Eliminar
        findViewById(R.id.btnDelete).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Eliminar waypoint")
                        .setMessage("¿Eliminar \"" + waypoint.name + "\"? " +
                                "Esta acción no se puede deshacer.")
                        .setPositiveButton("Eliminar", (d, w) -> {
                            repository.deleteById(waypoint.id);
                            Toast.makeText(this, "Waypoint eliminado",
                                    Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show()
        );
    }

    private void updateSpeciesChips(String species) {
        TextView[] chips = {chipConcha, chipPulpo, chipPescado,
                chipLangosta, chipCaracol, chipOtro};
        String[]   names = {"Concha de abanico", "Pulpo", "Pescado",
                "Langosta", "Caracol rosado", null};

        for (int i = 0; i < chips.length; i++) {
            boolean selected = (species == null && names[i] == null)
                    || (species != null && species.equals(names[i]));

            if (selected) {
                String color = WaypointManager.getColorForSpecies(species);
                chips[i].setBackgroundColor(
                        Color.parseColor(color.length() == 7
                                ? color + "33" : color));
                chips[i].setTextColor(Color.parseColor(color));
            } else {
                chips[i].setBackgroundColor(Color.parseColor("#0D1F3C"));
                chips[i].setTextColor(Color.parseColor("#6B7280"));
            }
        }
    }

    
    /**
     * Actualiza el color visual de los chips para multi-selección.
     * Un chip se resalta si su especie está en la lista selectedSpecies.
     */
    private void updateSpeciesChipsMulti() {
        TextView[] chips = {chipConcha, chipPulpo, chipPescado,
                chipLangosta, chipCaracol, chipOtro};
        String[]   names = {"Concha de abanico", "Pulpo", "Pescado",
                "Langosta", "Caracol rosado", null};

        for (int i = 0; i < chips.length; i++) {
            boolean sel = (names[i] != null)
                    && selectedSpecies.contains(names[i]);
            if (sel) {
                String color = WaypointManager.getColorForSpecies(names[i]);
                chips[i].setBackgroundColor(
                        Color.parseColor(color + "40"));
                chips[i].setTextColor(Color.parseColor(color));
            } else {
                chips[i].setBackgroundColor(Color.parseColor("#0D1F3C"));
                chips[i].setTextColor(Color.parseColor("#6B7280"));
            }
        }
    }
    

    private void updateSpeciesIcon(String species) {
        String icon;
        if (species == null) { icon = "📍"; }
        else switch (species) {
            case "Concha de abanico": icon = "🐚"; break;
            case "Pulpo":             icon = "🐙"; break;
            case "Pescado":           icon = "🐟"; break;
            case "Langosta":          icon = "🦞"; break;
            case "Caracol rosado":    icon = "🐌"; break;
            default:                  icon = "📍"; break;
        }
        tvSpeciesIcon.setText(icon);

        String color = WaypointManager.getColorForSpecies(species);
        tvSpeciesIcon.setBackgroundColor(
                Color.parseColor(color.length() == 7
                        ? color + "22" : color));
    }

    private void updateFavoriteButton(boolean isFavorite) {
        btnFavorite.setText(isFavorite ? "★" : "☆");
        btnFavorite.setTextColor(isFavorite
                ? Color.parseColor("#FBBF24")
                : Color.parseColor("#4B5563"));
    }

    // ── Guardar cambios ────────────────────────────────────────────────────

    private void markChanged() {
        if (!hasChanges) {
            hasChanges = true;
            btnSave.setVisibility(View.VISIBLE);
        }
    }

    private void saveChanges() {
        waypoint.name        = etName.getText().toString().trim();
        waypoint.description = etDescription.getText().toString().trim();
        waypoint.species     = selectedSpecies.isEmpty()
                ? null
                : android.text.TextUtils.join(",", selectedSpecies);
        waypoint.speciesColor = WaypointManager
                .getColorForSpecies(waypoint.species);
        waypoint.updatedAt   = System.currentTimeMillis();

        repository.update(waypoint);

        hasChanges = false;
        btnSave.setVisibility(View.GONE);

        // Ocultar teclado
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
        android.view.View focus = getCurrentFocus();
        if (focus != null)
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);

        Toast.makeText(this, "✓ Cambios guardados",
                Toast.LENGTH_SHORT).show();
    }
}