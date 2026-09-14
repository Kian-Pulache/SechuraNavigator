// archivo: app/src/main/java/com/sechuranavigator/app/TrackingService.java
package com.sechuranavigator.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.sechuranavigator.app.managers.GnssListener;
import com.sechuranavigator.app.managers.GnssManager;
import com.sechuranavigator.app.managers.TrackManager;
import com.sechuranavigator.app.models.GnssData;
import com.sechuranavigator.app.ui.MapActivity;

public class TrackingService extends Service implements GnssListener {

    public static final String ACTION_START = "START_TRACKING";
    public static final String ACTION_STOP  = "STOP_TRACKING";

    public static final String ACTION_TRACK_POINT =
            "com.sechuranavigator.app.TRACK_POINT";
    public static final String EXTRA_TRACK_LAT = "track_lat";
    public static final String EXTRA_TRACK_LON = "track_lon";

    // Broadcast para notificar una parada detectada
    public static final String ACTION_STOP_DETECTED =
            "com.sechuranavigator.app.STOP_DETECTED";
    public static final String EXTRA_STOP_LAT = "stop_lat";
    public static final String EXTRA_STOP_LON = "stop_lon";

    // Parámetros de detección de parada
    private static final float  STOP_RADIUS_M    = 30f;  // metros
    private static final long   STOP_MIN_TIME_MS = 10 * 60 * 1000L; // 10 minutos

    private static final String CHANNEL_ID   = "tracking_channel";
    private static final int    NOTIF_ID     = 1001;

    private GnssManager  gnssManager;
    private TrackManager trackManager;
    private java.util.concurrent.ExecutorService trackExecutor;

    // Para detección de paradas
    private double stopAnchorLat = Double.NaN;
    private double stopAnchorLon = Double.NaN;
    private long   stopStartTime = 0L;
    private boolean stopAlreadyNotified = false;

    private boolean isRecording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        gnssManager  = new GnssManager(this);
        trackManager = new TrackManager(this);

        // Crear executor de baja prioridad para no competir con la UI
        trackExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TrackingService-Worker");
            t.setPriority(Thread.MIN_PRIORITY); // prioridad mínima
            return t;
        });

        gnssManager.setListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // El sistema reinició el servicio — detenerlo limpiamente
            stopSelfClean();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_START.equals(action)) {
            if (!isRecording) {
                isRecording = true;
                // startForeground DEBE llamarse dentro de los primeros 5 segundos
                startForeground(NOTIF_ID, buildNotification("Iniciando grabación..."));
                gnssManager.start();
                trackManager.startRecording();
            }

        } else if (ACTION_STOP.equals(action)) {
            stopSelfClean();
        }

        return START_NOT_STICKY;
    }

    /**
     * Detiene el servicio limpiamente en el orden correcto:
     * 1. Detener GPS (deja de emitir datos)
     * 2. Detener track (guarda el track en BD)
     * 3. Quitar notificación
     * 4. Detener el servicio
     */
    private void stopSelfClean() {
        isRecording = false;
        gnssManager.stop();
        trackManager.stopRecording();

        // IMPORTANTE: stopForeground antes que stopSelf
        // El parámetro true = eliminar la notificación
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        gnssManager.stop();

        if (trackExecutor != null) trackExecutor.shutdown();
    }

    // ── GnssListener ──────────────────────────────────────────────────────
    @Override
    public void onGnssUpdate(GnssData data) {
        // El guardado del punto va en background de baja prioridad
        trackExecutor.execute(() -> trackManager.onNewGnssData(data));

        // Emitir broadcast local con la posición para que MapActivity
        // dibuje la polyline en tiempo real
        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(this)
                .sendBroadcast(
                        new android.content.Intent(ACTION_TRACK_POINT)
                                .putExtra(EXTRA_TRACK_LAT, data.latitude)
                                .putExtra(EXTRA_TRACK_LON, data.longitude)
                );

        // Detección de parada
        detectStop(data.latitude, data.longitude);

        // La actualización de la notificación va en el hilo del servicio
        // (ya es rápida, solo un String)
        float knots = data.speed * 1.94384f;
        String txt = String.format(java.util.Locale.US,
                "Grabando · %.1f kt · %.5f°S %.5f°W",
                knots, Math.abs(data.latitude), Math.abs(data.longitude));
        updateNotification(txt);
    }

    @Override
    public void onGnssStatusChange(GnssListener.GnssStatus status) {}

    // ── Notificación persistente ───────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Grabación de ruta",
                    NotificationManager.IMPORTANCE_LOW // LOW = sin sonido
            );
            channel.setDescription("Notificación mientras se graba una ruta");
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        // Intent para abrir la app al tocar la notificación
        Intent tapIntent = new Intent(this, MapActivity.class);
        PendingIntent tapPending = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent para el botón "Detener" en la notificación
        Intent stopIntent = new Intent(StopTrackingReceiver.ACTION_STOP_FROM_NOTIF);
        stopIntent.setPackage(getPackageName());
        PendingIntent stopPending = PendingIntent.getBroadcast(
                this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🔴 Grabando ruta")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(tapPending)
                .setOngoing(true)
                .setSilent(true)
                // Botón de acción para detener desde la notificación
                .addAction(android.R.drawable.ic_media_pause,
                        "Detener grabación", stopPending)
                .build();
    }

    private void updateNotification(String text) {
        Notification notif = buildNotification(text);
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, notif);
    }

    // ── Acceso al TrackManager desde MapActivity ───────────────────────────
    // MapActivity también necesita saber el estado de grabación.
    // Usamos un Singleton simple para compartir el TrackManager.
    public static TrackManager sharedTrackManager = null;


    private void detectStop(double lat, double lon) {
        if (Double.isNaN(stopAnchorLat)) {
            // Primera posición — establecer ancla
            stopAnchorLat = lat;
            stopAnchorLon = lon;
            stopStartTime = System.currentTimeMillis();
            stopAlreadyNotified = false;
            return;
        }

        // Calcular distancia al ancla
        float[] result = new float[1];
        android.location.Location.distanceBetween(
                stopAnchorLat, stopAnchorLon, lat, lon, result);
        float distToAnchor = result[0];

        if (distToAnchor <= STOP_RADIUS_M) {
            // Seguimos cerca del ancla
            long timeInStop = System.currentTimeMillis() - stopStartTime;

            if (timeInStop >= STOP_MIN_TIME_MS && !stopAlreadyNotified) {
                // Parada confirmada — notificar a MapActivity
                stopAlreadyNotified = true;
                androidx.localbroadcastmanager.content.LocalBroadcastManager
                        .getInstance(this)
                        .sendBroadcast(
                                new android.content.Intent(ACTION_STOP_DETECTED)
                                        .putExtra(EXTRA_STOP_LAT, stopAnchorLat)
                                        .putExtra(EXTRA_STOP_LON, stopAnchorLon)
                        );
            }
        } else {
            // El bote se movió — resetear ancla
            stopAnchorLat = lat;
            stopAnchorLon = lon;
            stopStartTime = System.currentTimeMillis();
            stopAlreadyNotified = false;
        }
    }
}