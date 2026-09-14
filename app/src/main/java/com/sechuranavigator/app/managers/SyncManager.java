// archivo: app/src/main/java/com/sechuranavigator/app/managers/SyncManager.java
package com.sechuranavigator.app.managers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.sechuranavigator.app.ErrorLogger;
import com.sechuranavigator.app.data.WaypointRepository;
import com.sechuranavigator.app.data.TrackRepository;
import com.sechuranavigator.app.data.local.entities.TrackEntity;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class SyncManager {

    private final Context            context;
    private final FirebaseFirestore  db;
    private final FirebaseAuth       auth;
    private final WaypointRepository waypointRepo;
    private final TrackRepository    trackRepo;
    private final Handler            mainHandler;

    public SyncManager(Context context) {
        this.context      = context.getApplicationContext();
        this.db           = FirebaseFirestore.getInstance();
        this.auth         = FirebaseAuth.getInstance();
        this.waypointRepo = new WaypointRepository(context);
        this.trackRepo    = new TrackRepository(context);
        this.mainHandler  = new Handler(Looper.getMainLooper());
    }

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Sube un waypoint a Firestore.
     * Se llama automáticamente al crear o editar un waypoint.
     */
    public void uploadWaypoint(WaypointEntity waypoint) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return; // modo local, no sincronizar

        Map<String, Object> data = new HashMap<>();
        data.put("localId",      waypoint.id);
        data.put("name",         waypoint.name);
        data.put("latitude",     waypoint.latitude);
        data.put("longitude",    waypoint.longitude);
        data.put("altitude",     waypoint.altitude);
        data.put("species",      waypoint.species);
        data.put("speciesColor", waypoint.speciesColor);
        data.put("description",  waypoint.description);
        data.put("isFavorite",   waypoint.isFavorite);
        data.put("visitCount",   waypoint.visitCount);
        data.put("createdAt",    waypoint.createdAt);
        data.put("updatedAt",    System.currentTimeMillis());

        db.collection("usuarios")
                .document(user.getUid())
                .collection("waypoints")
                .document("wp_" + waypoint.id)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v ->
                        ErrorLogger.log("Waypoint sincronizado: " + waypoint.name))
                .addOnFailureListener(e ->
                        ErrorLogger.logError("SyncManager.uploadWaypoint", e));
    }

    /**
     * Elimina un waypoint de Firestore.
     */
    public void deleteWaypoint(long waypointId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("usuarios")
                .document(user.getUid())
                .collection("waypoints")
                .document("wp_" + waypointId)
                .delete()
                .addOnFailureListener(e ->
                        ErrorLogger.logError("SyncManager.deleteWaypoint", e));
    }

    /**
     * Sube la metadata de una ruta completada a Firestore.
     * No sube los puntos individuales (demasiados).
     */
    public void uploadTrack(TrackEntity track) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("localId",         track.id);
        data.put("name",            track.name);
        data.put("startedAt",       track.startedAt);
        data.put("endedAt",         track.endedAt);
        data.put("durationMs",      track.durationMs);
        data.put("distanceMeters",  track.distanceMeters);
        data.put("pointCount",      track.pointCount);

        db.collection("usuarios")
                .document(user.getUid())
                .collection("tracks")
                .document("track_" + track.id)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v ->
                        ErrorLogger.log("Ruta sincronizada: " + track.name))
                .addOnFailureListener(e ->
                        ErrorLogger.logError("SyncManager.uploadTrack", e));
    }

    /**
     * Descarga todos los waypoints del usuario desde Firestore
     * y los guarda en la BD local si no existen.
     * Se llama al hacer login por primera vez en un dispositivo nuevo.
     */
    public void downloadAllData(OnSyncCompleteCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onComplete(0, 0, "Sin sesión");
            return;
        }

        ErrorLogger.log("Iniciando descarga desde Firestore...");

        // Descargar waypoints
        db.collection("usuarios")
                .document(user.getUid())
                .collection("waypoints")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int[] count = {0};
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        WaypointEntity wp = new WaypointEntity();
                        wp.name         = doc.getString("name");
                        wp.latitude     = getDouble(doc, "latitude");
                        wp.longitude    = getDouble(doc, "longitude");
                        wp.altitude     = getDouble(doc, "altitude");
                        wp.species      = doc.getString("species");
                        wp.speciesColor = doc.getString("speciesColor");
                        wp.description  = doc.getString("description");
                        Boolean fav     = doc.getBoolean("isFavorite");
                        wp.isFavorite   = fav != null && fav;
                        Long visits     = doc.getLong("visitCount");
                        wp.visitCount   = visits != null ? visits.intValue() : 0;
                        Long created    = doc.getLong("createdAt");
                        wp.createdAt    = created != null ? created : System.currentTimeMillis();

                        waypointRepo.insert(wp, newId -> count[0]++);
                    }

                    // Descargar tracks (solo metadata)
                    db.collection("usuarios")
                            .document(user.getUid())
                            .collection("tracks")
                            .get()
                            .addOnSuccessListener(trackSnapshot -> {
                                int trackCount = 0;
                                for (DocumentSnapshot doc : trackSnapshot.getDocuments()) {
                                    TrackEntity track = new TrackEntity();
                                    track.name            = doc.getString("name");
                                    Long started          = doc.getLong("startedAt");
                                    track.startedAt       = started != null ? started : 0;
                                    Long ended            = doc.getLong("endedAt");
                                    track.endedAt         = ended != null ? ended : 0;
                                    Long duration         = doc.getLong("durationMs");
                                    track.durationMs      = duration != null ? duration : 0;
                                    Double dist           = doc.getDouble("distanceMeters");
                                    track.distanceMeters  = dist != null ? dist : 0;
                                    Long pts              = doc.getLong("pointCount");
                                    track.pointCount      = pts != null ? pts.intValue() : 0;
                                    track.isActive        = false;
                                    trackRepo.insertTrack(track, null);
                                    trackCount++;
                                }

                                final int finalTrackCount = trackCount;
                                mainHandler.post(() -> {
                                    ErrorLogger.log("Descarga completa: " +
                                            count[0] + " waypoints, " +
                                            finalTrackCount + " rutas");
                                    if (callback != null)
                                        callback.onComplete(count[0],
                                                finalTrackCount, null);
                                });
                            })
                            .addOnFailureListener(e -> {
                                ErrorLogger.logError("SyncManager.downloadTracks", e);
                                mainHandler.post(() -> {
                                    if (callback != null)
                                        callback.onComplete(count[0], 0,
                                                e.getMessage());
                                });
                            });
                })
                .addOnFailureListener(e -> {
                    ErrorLogger.logError("SyncManager.downloadWaypoints", e);
                    mainHandler.post(() -> {
                        if (callback != null)
                            callback.onComplete(0, 0, e.getMessage());
                    });
                });
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private double getDouble(DocumentSnapshot doc, String field) {
        Double v = doc.getDouble(field);
        return v != null ? v : 0.0;
    }

    public interface OnSyncCompleteCallback {
        void onComplete(int waypointCount, int trackCount, String error);
    }
}