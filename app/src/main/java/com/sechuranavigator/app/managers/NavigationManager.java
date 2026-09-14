// archivo: app/src/main/java/com/sechuranavigator/app/managers/NavigationManager.java
package com.sechuranavigator.app.managers;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import com.sechuranavigator.app.data.local.entities.WaypointEntity;
import com.sechuranavigator.app.models.GnssData;

import java.util.ArrayDeque;
import java.util.Deque;

public class NavigationManager {

    // ── Configuración ──────────────────────────────────────────────────────
    // Radio de llegada al destino — cuando estás más cerca que esto,
    // se considera que llegaste
    private static final float  ARRIVAL_RADIUS_M       = 25f;

    // Buffer para calcular velocidad promedio (últimas N lecturas)
    private static final int    SPEED_BUFFER_SIZE       = 5;

    // ── Estado ────────────────────────────────────────────────────────────
    private WaypointEntity  destination;
    private long            startTimeMs;
    private GnssData        lastGnssData;
    private boolean         isNavigating = false;

    // Buffer de velocidades para suavizar el cálculo de ETA
    private final Deque<Float> speedBuffer = new ArrayDeque<>();

    // ── Listener ──────────────────────────────────────────────────────────
    private NavigationListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void setListener(NavigationListener listener) {
        this.listener = listener;
    }

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Inicia la navegación hacia un waypoint.
     */
    public void startNavigation(WaypointEntity destination) {
        this.destination  = destination;
        this.startTimeMs  = System.currentTimeMillis();
        this.isNavigating = true;
        speedBuffer.clear();
    }

    /**
     * Detiene la navegación activa.
     */
    public void stopNavigation() {
        isNavigating  = false;
        destination   = null;
        lastGnssData  = null;
        speedBuffer.clear();
    }

    public boolean isNavigating() { return isNavigating; }
    public WaypointEntity getDestination() { return destination; }

    /**
     * Procesa nuevo dato GPS y calcula todos los valores de navegación.
     * Llamar desde GnssListener.onGnssUpdate().
     */
    public void onNewGnssData(GnssData data) {
        if (!isNavigating || destination == null) return;
        lastGnssData = data;

        // ── Calcular distancia al destino ──────────────────────────────────
        float[] distResult = new float[1];
        Location.distanceBetween(
                data.latitude,      data.longitude,
                destination.latitude, destination.longitude,
                distResult
        );
        float distanceMeters = distResult[0];

        // ── Calcular rumbo hacia el destino ────────────────────────────────
        // bearingTo: 0° = Norte, 90° = Este, 180° = Sur, 270° = Oeste
        float[] bearingResult = new float[3];
        Location.distanceBetween(
                data.latitude,        data.longitude,
                destination.latitude, destination.longitude,
                bearingResult
        );
        // El índice 1 de distanceBetween no da el bearing inicial
        // Usamos la fórmula directa:
        float bearingToDestination = bearingBetween(
                data.latitude, data.longitude,
                destination.latitude, destination.longitude
        );

        // ── Velocidad promediada ────────────────────────────────────────────
        speedBuffer.addLast(data.speed);
        if (speedBuffer.size() > SPEED_BUFFER_SIZE) {
            speedBuffer.pollFirst();
        }
        float avgSpeedMs = 0f;
        for (float s : speedBuffer) avgSpeedMs += s;
        if (!speedBuffer.isEmpty()) avgSpeedMs /= speedBuffer.size();

        // ── Tiempo recorrido ───────────────────────────────────────────────
        long elapsedMs = System.currentTimeMillis() - startTimeMs;

        // ── ETA y tiempo restante ──────────────────────────────────────────
        long etaMs        = 0L;
        long remainingMs  = 0L;
        if (avgSpeedMs > 0.1f) { // evitar división por cero o velocidad nula
            remainingMs = (long) ((distanceMeters / avgSpeedMs) * 1000L);
            etaMs       = System.currentTimeMillis() + remainingMs;
        }

        // ── ¿Llegamos? ─────────────────────────────────────────────────────
        if (distanceMeters <= ARRIVAL_RADIUS_M) {
            mainHandler.post(() -> {
                if (listener != null) listener.onArrivalDetected();
            });
        }

        // ── Emitir actualización ───────────────────────────────────────────
        final NavigationData navData = new NavigationData(
                distanceMeters,
                bearingToDestination,
                data.bearing,         // rumbo actual del bote (del GPS)
                avgSpeedMs,
                elapsedMs,
                remainingMs,
                etaMs
        );

        mainHandler.post(() -> {
            if (listener != null) listener.onNavigationUpdate(navData);
        });
    }

    // ── Cálculo de rumbo ───────────────────────────────────────────────────

    /**
     * Calcula el rumbo inicial desde (lat1,lon1) hacia (lat2,lon2).
     * Resultado en grados, 0 = Norte, sentido horario.
     */
    private float bearingBetween(double lat1, double lon1,
                                 double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double x = Math.sin(dLon) * Math.cos(rLat2);
        double y = Math.cos(rLat1) * Math.sin(rLat2)
                - Math.sin(rLat1) * Math.cos(rLat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(x, y));
        return (float) ((bearing + 360) % 360);
    }

    // ── Modelo de datos de navegación ──────────────────────────────────────

    public static class NavigationData {
        public final float distanceMeters;      // distancia al destino
        public final float bearingToDestination;// rumbo que debemos seguir
        public final float currentBearing;      // rumbo actual del bote
        public final float speedMs;             // velocidad en m/s
        public final long  elapsedMs;           // tiempo recorrido
        public final long  remainingMs;         // tiempo restante estimado
        public final long  etaTimestamp;        // timestamp de llegada estimada

        public NavigationData(float distanceMeters, float bearingToDestination,
                              float currentBearing, float speedMs,
                              long elapsedMs, long remainingMs,
                              long etaTimestamp) {
            this.distanceMeters       = distanceMeters;
            this.bearingToDestination = bearingToDestination;
            this.currentBearing       = currentBearing;
            this.speedMs              = speedMs;
            this.elapsedMs            = elapsedMs;
            this.remainingMs          = remainingMs;
            this.etaTimestamp         = etaTimestamp;
        }
    }

    // ── Interfaz listener ──────────────────────────────────────────────────

    public interface NavigationListener {
        void onNavigationUpdate(NavigationData data);
        void onArrivalDetected();
    }
}