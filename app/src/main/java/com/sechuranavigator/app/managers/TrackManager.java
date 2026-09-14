// archivo: app/src/main/java/com/sechuranavigator/app/managers/TrackManager.java
package com.sechuranavigator.app.managers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.sechuranavigator.app.data.TrackRepository;
import com.sechuranavigator.app.data.local.entities.TrackEntity;
import com.sechuranavigator.app.data.local.entities.TrackPointEntity;
import com.sechuranavigator.app.models.GnssData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrackManager {

    // Distancia mínima entre puntos para grabar (evita ruido GPS estático)
    private static final float MIN_DISTANCE_METERS = 5.0f;
    // Precisión mínima para aceptar un punto
    private static final float MAX_ACCURACY_METERS = 15.0f;

    private final TrackRepository  repository;
    private final Handler          mainHandler = new Handler(Looper.getMainLooper());

    // Estado actual
    private TrackEntity  activeTrack     = null;
    private GnssData     lastPoint       = null;
    private double       totalDistance   = 0.0;
    private long         pausedAt        = 0L;
    private long         totalPausedMs   = 0L;

    // Buffer de puntos para dibujar en el mapa en tiempo real
    private final List<GnssData> livePoints = new ArrayList<>();

    // Listener para notificar a la UI
    private TrackListener listener;

    private SyncManager syncManager;

    public TrackManager(Context context) {
        this.repository = new TrackRepository(context);
        this.syncManager = new SyncManager(context);
    }

    public void setListener(TrackListener listener) {
        this.listener = listener;
    }

    // ── API pública ────────────────────────────────────────────────────────

    public boolean isRecording() {
        return activeTrack != null && activeTrack.isActive && !activeTrack.isPaused;
    }

    public boolean isPaused() {
        return activeTrack != null && activeTrack.isPaused;
    }

    public boolean hasActiveTrack() {
        return activeTrack != null && activeTrack.isActive;
    }

    /**
     * Inicia un nuevo track de grabación.
     */
    public void startRecording() {
        if (hasActiveTrack()) return; // ya hay uno activo

        TrackEntity track = new TrackEntity();
        track.name = generateTrackName();

        repository.insertTrack(track, newId -> {
            track.id = newId;
            activeTrack = track;
            livePoints.clear();
            totalDistance = 0.0;
            lastPoint     = null;

            mainHandler.post(() -> {
                if (listener != null) listener.onTrackStarted(track);
            });
        });
    }

    /**
     * Pausa la grabación — los puntos GPS no se guardan hasta resumir.
     */
    public void pauseRecording() {
        if (!isRecording()) return;
        activeTrack.isPaused = true;
        pausedAt = System.currentTimeMillis();
        repository.updateTrack(activeTrack);
        mainHandler.post(() -> {
            if (listener != null) listener.onTrackPaused();
        });
    }

    /**
     * Reanuda la grabación después de una pausa.
     */
    public void resumeRecording() {
        if (!isPaused()) return;
        activeTrack.isPaused = false;
        if (pausedAt > 0) {
            totalPausedMs += System.currentTimeMillis() - pausedAt;
            pausedAt = 0;
        }
        repository.updateTrack(activeTrack);
        mainHandler.post(() -> {
            if (listener != null) listener.onTrackResumed();
        });
    }

    /**
     * Finaliza y guarda el track completo.
     */
    public void stopRecording() {
        if (!hasActiveTrack()) return;

        long now = System.currentTimeMillis();
        activeTrack.isActive      = false;
        activeTrack.isPaused      = false;
        activeTrack.endedAt       = now;
        activeTrack.durationMs    = (now - activeTrack.startedAt) - totalPausedMs;
        activeTrack.distanceMeters = totalDistance;
        activeTrack.pointCount    = livePoints.size();

        repository.updateTrack(activeTrack);
        syncManager.uploadTrack(activeTrack);

        TrackEntity finishedTrack = activeTrack;
        activeTrack   = null;
        lastPoint     = null;
        totalDistance = 0.0;
        totalPausedMs = 0L;
        livePoints.clear();

        mainHandler.post(() -> {
            if (listener != null) listener.onTrackStopped(finishedTrack);
        });
    }

    /**
     * Recibe un nuevo dato GPS — llamar desde GnssListener.onGnssUpdate()
     */
    public void onNewGnssData(GnssData data) {
        if (!isRecording()) return;
        if (data.accuracy > MAX_ACCURACY_METERS) return;

        // Filtrar por distancia mínima
        if (lastPoint != null) {
            float[] results = new float[1];
            android.location.Location.distanceBetween(
                    lastPoint.latitude, lastPoint.longitude,
                    data.latitude, data.longitude, results);
            if (results[0] < MIN_DISTANCE_METERS) return;
            totalDistance += results[0];
        }

        // Guardar punto en BD
        TrackPointEntity point = new TrackPointEntity();
        point.trackId   = activeTrack.id;
        point.latitude  = data.latitude;
        point.longitude = data.longitude;
        point.altitude  = data.altitude;
        point.accuracy  = data.accuracy;
        point.speed     = data.speed;
        repository.insertPoint(point);

        // Agregar al buffer en vivo
        livePoints.add(data);
        lastPoint = data;

        // Notificar a la UI para redibujar la línea
        final List<GnssData> snapshot = new ArrayList<>(livePoints);
        mainHandler.post(() -> {
            if (listener != null) listener.onTrackPointAdded(snapshot);
        });
    }

    public List<GnssData> getLivePoints() {
        return new ArrayList<>(livePoints);
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private String generateTrackName() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "dd MMM yyyy", new Locale("es", "PE"));
        return "Salida " + sdf.format(new Date());
    }

    // ── Interfaz listener ──────────────────────────────────────────────────

    public interface TrackListener {
        void onTrackStarted(TrackEntity track);
        void onTrackPaused();
        void onTrackResumed();
        void onTrackStopped(TrackEntity track);
        void onTrackPointAdded(List<GnssData> allPoints);
    }
}