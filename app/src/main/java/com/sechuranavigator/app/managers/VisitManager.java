// archivo: app/src/main/java/com/sechuranavigator/app/managers/VisitManager.java
package com.sechuranavigator.app.managers;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import com.sechuranavigator.app.data.VisitRepository;
import com.sechuranavigator.app.data.local.entities.VisitEntity;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;
import com.sechuranavigator.app.models.GnssData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class VisitManager {

    // ── Configuración (acordada en el diseño) ─────────────────────────────
    private static final float  DEPARTURE_RADIUS_M   = 50f;   // radio de salida
    private static final long   WINDOW_MS            = 60_000L; // ventana de 60 s
    private static final long   DEPARTURE_CONFIRM_MS = 30_000L; // 30s fuera para confirmar salida
    private static final float  MAX_SPEED_MS         = 1.03f;  // < 2 nudos en m/s
    private float arrivalRadiusM = 25f;
    private long  minInsideMs    = 40_000L;
    private boolean askProduction = true;
    // ── Dependencias ──────────────────────────────────────────────────────
    private final VisitRepository repository;
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    // ── Estado ────────────────────────────────────────────────────────────
    private List<WaypointEntity> waypoints;       // lista actual de waypoints
    private VisitEntity          activeVisit;     // visita en curso (null si ninguna)
    private WaypointEntity       activeWaypoint;  // waypoint siendo visitado

    // Buffer deslizante de 60s para detección de llegada
    // Cada entrada: timestamp de cuando el punto estuvo DENTRO del radio
    private final Deque<Long> insideWindowBuffer = new ArrayDeque<>();

    // Para detección de salida
    private Long firstOutsideTime = null; // cuando empezó a estar fuera

    // Listener
    private VisitListener listener;

    public VisitManager(Context context) {
        this.repository = new VisitRepository(context);

        // Leer configuración
        SettingsManager settings = new SettingsManager(context);
        arrivalRadiusM = settings.getArrivalRadius();
        minInsideMs    = (long) settings.getMinStay() * 1000L - 20_000L;
        // minInsideMs = minStay - 20s (los últimos 20s de los 60s de ventana)
        if (minInsideMs < 5000L) minInsideMs = 5000L; // mínimo 5s
        askProduction  = settings.askProduction();
    }

    public void setListener(VisitListener listener) {
        this.listener = listener;
    }

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Actualizar la lista de waypoints cuando cambia (via LiveData).
     */
    public void updateWaypoints(List<WaypointEntity> waypoints) {
        this.waypoints = waypoints;
    }

    /**
     * Procesar nuevo dato GPS. Llamar desde GnssListener.onGnssUpdate().
     */
    public void onNewGnssData(GnssData data) {
        if (waypoints == null || waypoints.isEmpty()) return;

        long now = System.currentTimeMillis();

        // ── Modo: buscando llegada ─────────────────────────────────────────
        if (activeVisit == null) {
            checkForArrival(data, now);
        }
        // ── Modo: visita activa — buscando salida ─────────────────────────
        else {
            checkForDeparture(data, now);
        }
    }

    /**
     * Versión original mantenida para compatibilidad.
     * Delega al nuevo metodo completo.
     */
    public void recordProduction(float kg, String notes) {
        recordProductionFull(
            kg > 0 ? kg + " kg" : "",
            notes, 1, "normal");
    }

    /**
     * Versión completa con todos los nuevos datos.
     * Llamada desde showProductionDialog() en MapActivity.
     *
     * @param catchQuantity texto libre: "20 valdes", "5 cajas", "8 pulpos"
     * @param notes         notas adicionales opcionales
     * @param numPersons    cuántas personas fueron a pescar
     * @param conditionTags etiquetas del agua separadas por coma
     */
    public void recordProductionFull(String catchQuantity, String notes,
                                      int numPersons, String conditionTags) {
        if (activeVisit == null) return;

        // Campos existentes (catchKg se mantiene para compatibilidad)
        activeVisit.catchNotes    = notes;

        // Campos nuevos
        activeVisit.catchQuantity  = catchQuantity;
        activeVisit.numPersons     = numPersons;
        activeVisit.conditionTags  = conditionTags;

        // Intentar extraer kg del texto para calcular catchKgPerPerson
        // Funciona si el usuario escribió algo como "15 kg" o "20.5 kg"
        float parsedKg = tryParseKg(catchQuantity);
        if (parsedKg > 0 && numPersons > 0) {
            activeVisit.catchKg          = parsedKg;
            activeVisit.catchKgPerPerson = parsedKg / numPersons;
        }

        repository.update(activeVisit);
    }

    /**
     * Metodo publico para que MapActivity actualice la distancia
     * desde el puerto cuando el track está activo.
     */
    public void updateDistanceFromPort(float distanceMeters) {
        if (activeVisit == null) return;
        activeVisit.distanceFromPortM = distanceMeters;
        repository.update(activeVisit);
    }

    /**
     * Extrae un número en kg de texto libre.
     * "20 kg" → 20.0 · "5 cajas" → 0.0 · "15.5 kg" → 15.5
     */
    private float tryParseKg(String text) {
        if (text == null || text.isEmpty()) return 0f;
        java.util.regex.Matcher m =
            java.util.regex.Pattern
                .compile("(\\d+\\.?\\d*)\\s*kg",
                    java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (m.find()) {
            try { return Float.parseFloat(m.group(1)); }
            catch (NumberFormatException e) { return 0f; }
        }
        return 0f;
    }

    // ── Detección de llegada ───────────────────────────────────────────────

    private void checkForArrival(GnssData data, long now) {
        // Velocidad demasiado alta — no puede ser una llegada
        if (data.speed > MAX_SPEED_MS) {
            insideWindowBuffer.clear();
            return;
        }

        // Buscar el waypoint más cercano dentro del radio de llegada
        WaypointEntity nearest = findNearestWithinRadius(
                data, arrivalRadiusM);

        if (nearest != null) {
            // Estamos dentro del radio — agregar al buffer
            insideWindowBuffer.addLast(now);

            // Limpiar entradas más antiguas que la ventana de 60s
            while (!insideWindowBuffer.isEmpty() &&
                    now - insideWindowBuffer.peekFirst() > WINDOW_MS) {
                insideWindowBuffer.pollFirst();
            }

            // Calcular tiempo total dentro de la ventana
            long insideTime = 0;
            if (!insideWindowBuffer.isEmpty()) {
                insideTime = now - insideWindowBuffer.peekFirst();
            }

            // ¿Confirmar llegada? (≥40s dentro de la ventana de 60s)
            if (insideTime >= minInsideMs) {
                confirmArrival(nearest, now);
                insideWindowBuffer.clear();
            }
        } else {
            // Fuera del radio — no limpiar el buffer inmediatamente
            // (el bote puede salir y volver dentro de la ventana)
            // Pero sí limpiar si ya pasó la ventana completa sin estar cerca
            if (!insideWindowBuffer.isEmpty() &&
                    now - insideWindowBuffer.peekFirst() > WINDOW_MS) {
                insideWindowBuffer.clear();
            }
        }
    }

    private void confirmArrival(WaypointEntity waypoint, long now) {
        activeWaypoint = waypoint;

        VisitEntity visit = new VisitEntity();
        visit.waypointId = waypoint.id;
        visit.arrivedAt  = now;

        // ← NUEVO: intentar obtener distancia desde el puerto del TrackManager
        // Si no hay track activo, quedará en 0 (el default)
        // Esta línea solo funciona si hay acceso al TrackManager desde aquí.
        // Si no está disponible, se puede actualizar desde MapActivity después.

        repository.insert(visit, newId -> {
            visit.id    = newId;
            activeVisit = visit;
            repository.incrementVisitCount(waypoint.id);

            mainHandler.post(() -> {
                if (listener != null)
                    listener.onArrival(waypoint, visit);
            });
        });
    }

    // ── Detección de salida ───────────────────────────────────────────────

    private void checkForDeparture(GnssData data, long now) {
        float distanceToActive = distanceTo(data, activeWaypoint);

        if (distanceToActive > DEPARTURE_RADIUS_M) {
            // Estamos fuera del radio de salida
            if (firstOutsideTime == null) {
                firstOutsideTime = now; // empezar a contar
            } else if (now - firstOutsideTime >= DEPARTURE_CONFIRM_MS) {
                // Llevamos ≥30s fuera — confirmar salida
                confirmDeparture(now);
            }
        } else {
            // Volvimos dentro del radio — cancelar contador de salida
            firstOutsideTime = null;
        }
    }

    private void confirmDeparture(long now) {
        activeVisit.isActive   = false;
        activeVisit.departedAt = now;
        activeVisit.durationMs = now - activeVisit.arrivedAt;
        repository.update(activeVisit);

        WaypointEntity departed = activeWaypoint;
        VisitEntity    visit    = activeVisit;

        activeVisit    = null;
        activeWaypoint = null;
        firstOutsideTime = null;

        mainHandler.post(() -> {
            if (listener != null)
                listener.onDeparture(departed, visit);
        });
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    private WaypointEntity findNearestWithinRadius(GnssData data, float radius) {
        WaypointEntity nearest  = null;
        float          minDist  = Float.MAX_VALUE;

        for (WaypointEntity wp : waypoints) {
            float dist = distanceBetween(
                    data.latitude, data.longitude,
                    wp.latitude,   wp.longitude);
            if (dist <= radius && dist < minDist) {
                minDist = dist;
                nearest = wp;
            }
        }
        return nearest;
    }

    private float distanceTo(GnssData data, WaypointEntity wp) {
        return distanceBetween(
                data.latitude, data.longitude,
                wp.latitude,   wp.longitude);
    }

    private float distanceBetween(double lat1, double lon1,
                                  double lat2, double lon2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, result);
        return result[0];
    }

    // ── Interfaz de listener ───────────────────────────────────────────────

    public interface VisitListener {
        void onArrival(WaypointEntity waypoint, VisitEntity visit);
        void onDeparture(WaypointEntity waypoint, VisitEntity visit);
    }

}