// archivo: app/src/main/java/com/sechuranavigator/app/managers/WaypointManager.java
package com.sechuranavigator.app.managers;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.sechuranavigator.app.data.WaypointRepository;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;
import com.sechuranavigator.app.models.GnssData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WaypointManager {

    // ── Colores por especie ───────────────────────────────────────────────
    // Mapa estático: nombre de especie → color hex del marcador en el mapa
    public static final Map<String, String> SPECIES_COLORS = new HashMap<>();
    static {
        SPECIES_COLORS.put("Concha de abanico", "#F59E0B"); // ámbar
        SPECIES_COLORS.put("Pulpo",             "#60A5FA"); // azul
        SPECIES_COLORS.put("Langosta",          "#A78BFA"); // violeta
        SPECIES_COLORS.put("Pescado",           "#10B981"); // verde
        SPECIES_COLORS.put("Caracol rosado",    "#FB923C"); // naranja
        SPECIES_COLORS.put("Almejas",           "#F472B6"); // rosa
        SPECIES_COLORS.put("Caracol bola",      "#FACC15"); // amarillo
    }

    public static final String DEFAULT_COLOR = "#9CA3AF"; // gris para "Otro"

    // ── Dependencias ──────────────────────────────────────────────────────
    private final WaypointRepository repository;
    private final SyncManager syncManager;
    private final android.content.Context context;

    public WaypointManager(Context context) {
        this.context    = context.getApplicationContext();
        this.repository = new WaypointRepository(context);
        this.syncManager = new SyncManager(context);
    }

    // ── Guardar waypoint desde GPS actual ─────────────────────────────────

    /**
     * Guarda un nuevo waypoint usando la posición GPS actual.
     *
     * @param gnssData   datos del GPS (latitud, longitud, etc.)
     * @param name       nombre del punto (puede ser null o vacío)
     * @param species    especie asociada (puede ser null)
     * @param callback   se llama cuando el guardado termina, con el ID asignado
     */
    public void saveFromCurrentLocation(
            GnssData gnssData,
            String name,
            String species,
            String description,
            WaypointRepository.OnInsertCallback callback) {

        // Validación: no guardar si no hay fix GPS válido
        if (gnssData == null || !gnssData.hasValidFix) {
            if (callback != null) callback.onInserted(-1L);
            // -1 indica fallo — la Activity debe mostrar mensaje de error
            return;
        }

        WaypointEntity entity = new WaypointEntity();

        // Posición
        entity.latitude       = gnssData.latitude;
        entity.longitude      = gnssData.longitude;
        entity.altitude       = gnssData.altitude;
        entity.accuracy       = gnssData.accuracy;
        entity.satelliteCount = gnssData.satelliteCount;

        // Nombre: si está vacío, generar uno automático con timestamp
        if (name != null && !name.trim().isEmpty()) {
            entity.name = name.trim();
        } else {
            entity.name = generateAutoName(species);
        }

        // Especie y color
        entity.species      = species;
        entity.speciesColor = getColorForSpecies(species);
        entity.description  = description;

        repository.insert(entity, newId -> {
            if (newId > 0) {
                entity.id = newId; // ← ahora sí tenemos el ID real de Room
                // Sincronizar con Firestore DESPUÉS de que Room asignó el ID
                new SyncManager(context).uploadWaypoint(entity);
            }
            if (callback != null) callback.onInserted(newId);
        });
    }

    /**
     * Guarda un waypoint con coordenadas ingresadas manualmente.
     */
    public void saveManual(
            double latitude,
            double longitude,
            String name,
            String species,
            String description,
            WaypointRepository.OnInsertCallback callback) {

        // Validación básica de coordenadas para la Bahía de Sechura
        // Rango aproximado: lat -5.0 a -6.5, lon -81.5 a -80.0
        if (latitude < -90 || latitude > 90 ||
                longitude < -180 || longitude > 180) {
            if (callback != null) callback.onInserted(-1L);
            return;
        }

        WaypointEntity entity = new WaypointEntity();
        entity.latitude  = latitude;
        entity.longitude = longitude;
        entity.altitude  = 0;
        entity.accuracy  = 0; // sin dato de precisión para coordenadas manuales
        entity.name      = (name != null && !name.trim().isEmpty())
                ? name.trim()
                : generateAutoName(species);
        entity.species      = species;
        entity.speciesColor = getColorForSpecies(species);
        entity.description  = description;

        repository.insert(entity, newId -> {
            if (newId > 0) {
                entity.id = newId;
                new SyncManager(context).uploadWaypoint(entity);
            }
            if (callback != null) callback.onInserted(newId);
        });
    }

    // ── Eliminar ──────────────────────────────────────────────────────────

    public void delete(long waypointId) {
        syncManager.deleteWaypoint(waypointId);
        repository.deleteById(waypointId);
    }

    // ── Consultas (devuelven LiveData para actualización automática de UI) ─

    public LiveData<List<WaypointEntity>> getAllLive() {
        return repository.getAllLive();
    }

    public LiveData<List<WaypointEntity>> searchLive(String query) {
        return repository.searchLive(query);
    }

    public LiveData<List<WaypointEntity>> getBySpeciesLive(String species) {
        return repository.getBySpeciesLive(species);
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    // Este metodo ya funciona con "pulpo,pescado" porque hace contains()
    // El marcador del mapa tomará el color de la primera especie listada
    public static String getColorForSpecies(String species) {
        if (species == null) return "#9CA3AF";
        // Para multi-especie, usamos la primera
        String primary = species.split(",")[0].trim();
        String color = SPECIES_COLORS.get(primary);
        return color != null ? color : "#9CA3AF";
    }

    private String generateAutoName(String species) {
        String base = (species != null && !species.isEmpty()) ? species : "Punto";
        // Hora actual en formato HH:mm para diferenciar puntos del mismo día
        java.util.Date now = new java.util.Date();
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(now);
        return base + " " + time;
    }

    /**
     * Genera el ID compuesto formato "USRxxxxx_NNN"
     * donde xxxxx = primeros 5 caracteres del userId de Firebase
     * y NNN = el id numérico asignado por Room (con ceros a la izquierda).
     */
    private String buildCompositeId(long roomId) {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth
                        .getInstance().getCurrentUser();

        String userPrefix;
        if (user != null && user.getUid().length() >= 5) {
            userPrefix = "USR" + user.getUid().substring(0, 5).toUpperCase();
        } else {
            userPrefix = "LOCAL"; // sin cuenta de Firebase
        }

        return String.format(java.util.Locale.US,
                "%s_%03d", userPrefix, roomId);
    }
}