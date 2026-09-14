// archivo: app/src/main/java/com/sechuranavigator/app/managers/GnssManager.java
package com.sechuranavigator.app.managers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GnssStatus;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.sechuranavigator.app.ErrorLogger;
import com.sechuranavigator.app.data.local.AppDatabase;
import com.sechuranavigator.app.managers.SettingsManager;
import com.sechuranavigator.app.models.GnssData;

import java.util.ArrayList;
import java.util.List;

public class GnssManager implements LocationListener, SensorEventListener {

    // Campo nuevo — umbral para filtrar actualizaciones de brújula
    private float lastEmittedBearing = -1f;
    private static final float BEARING_CHANGE_THRESHOLD = 2.0f; // grados

    // ── Configuración base (se sobreescribe con SettingsManager) ──────────
    private float MIN_ACCURACY_METERS = 8.0f;
    private int   READINGS_TO_AVERAGE = 10;

    private static final long  LOCATION_INTERVAL_MS = 1000;
    private static final float LOCATION_MIN_DIST_M  = 0.5f;

    // ── Dependencias ──────────────────────────────────────────────────────
    private final Context         context;
    private final LocationManager locationManager;
    private final SensorManager   sensorManager;
    private final Sensor          accelerometer;
    private final Sensor          magnetometer;

    // ── Estado interno ────────────────────────────────────────────────────
    private GnssListener listener;
    private boolean      isRunning = false;

    // Buffer para promediado (solo lecturas precisas)
    private final List<Location> locationBuffer = new ArrayList<>();

    // Última lectura raw (para flujo rápido)
    private Location lastRawLocation = null;

    // Brújula
    private float[] gravityData  = null;
    private float[] magneticData = null;
    private float   currentBearing = 0f;

    // Satélites
    private int satelliteCount = 0;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Constructor ───────────────────────────────────────────────────────

    public GnssManager(@NonNull Context context) {
        this.context = context.getApplicationContext();

        // Leer configuración guardada
        SettingsManager settings = new SettingsManager(this.context);
        MIN_ACCURACY_METERS = settings.getMinAccuracy();
        READINGS_TO_AVERAGE = settings.getReadingsAvg();

        locationManager = (LocationManager)
                this.context.getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager)
                this.context.getSystemService(Context.SENSOR_SERVICE);

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer  = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    // ── API pública ────────────────────────────────────────────────────────

    public void setListener(GnssListener listener) {
        this.listener = listener;
    }

    @SuppressWarnings("MissingPermission")
    public void start() {
        if (isRunning) return;

        // Releer configuración
        SettingsManager settings = new SettingsManager(context);
        MIN_ACCURACY_METERS = settings.getMinAccuracy();
        READINGS_TO_AVERAGE = settings.getReadingsAvg();

        isRunning = true;

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_INTERVAL_MS,
                    LOCATION_MIN_DIST_M,
                    this
            );
            locationManager.registerGnssStatusCallback(
                    gnssStatusCallback, mainHandler);
            ErrorLogger.log("GPS iniciado correctamente");
        } catch (SecurityException e) {
            ErrorLogger.logGpsError("start - permiso denegado", e);
            notifyStatus(GnssListener.GnssStatus.NO_PERMISSION);
        } catch (Exception e) {
            ErrorLogger.logGpsError("start", e);
        }

        try {
            sensorManager.registerListener(
                    this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(
                    this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        } catch (Exception e) {
            ErrorLogger.logError("GnssManager - sensores", e);
        }

        notifyStatus(GnssListener.GnssStatus.SEARCHING);
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        locationManager.removeUpdates(this);
        locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
        sensorManager.unregisterListener(this);
        locationBuffer.clear();
    }

    // ── LocationListener ──────────────────────────────────────────────────

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (!LocationManager.GPS_PROVIDER.equals(location.getProvider())) return;

        lastRawLocation = location;

        // ── FLUJO RÁPIDO: emitir inmediatamente con cualquier precisión ───
        GnssData quickData = buildGnssData(location, false);
        quickData.bearing  = currentBearing;
        quickData.satelliteCount = satelliteCount;
        // Es aproximado si la precisión no cumple el umbral
        quickData.isApproximate = (location.getAccuracy() > MIN_ACCURACY_METERS);
        notifyData(quickData);

        // Estado de señal
        if (location.getAccuracy() <= MIN_ACCURACY_METERS) {
            notifyStatus(GnssListener.GnssStatus.READY);
        } else {
            notifyStatus(GnssListener.GnssStatus.LOW_ACCURACY);
        }

        // ── FLUJO PRECISO: buffer de promediado (solo lecturas buenas) ────
        if (location.getAccuracy() <= MIN_ACCURACY_METERS) {
            locationBuffer.add(location);
            if (locationBuffer.size() >= READINGS_TO_AVERAGE) {
                emitAveragedLocation();
                locationBuffer.clear();
            }
        }
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider))
            notifyStatus(GnssListener.GnssStatus.SEARCHING);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {}

    // ── GnssStatus callback ───────────────────────────────────────────────

    private final GnssStatus.Callback gnssStatusCallback =
            new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    int count = 0;
                    for (int i = 0; i < status.getSatelliteCount(); i++) {
                        if (status.usedInFix(i)) count++;
                    }
                    satelliteCount = count;
                }
            };

    // ── SensorEventListener — brújula ─────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            gravityData = event.values.clone();
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            magneticData = event.values.clone();

        if (gravityData == null || magneticData == null) return;

        float[] rotationMatrix    = new float[9];
        float[] inclinationMatrix = new float[9];
        boolean success = SensorManager.getRotationMatrix(
                rotationMatrix, inclinationMatrix, gravityData, magneticData);
        if (!success) return;

        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);
        float azimuth = (float) Math.toDegrees(orientation[0]);
        currentBearing = (azimuth + 360) % 360;

        // ── BRÚJULA EN TIEMPO REAL: emitir solo el bearing actualizado ────
        // Así la brújula rota inmediatamente sin esperar al GPS
        float bearingDelta = Math.abs(currentBearing - lastEmittedBearing);
        // Normalizar delta (puede cruzar el 0/360)
        if (bearingDelta > 180f) bearingDelta = 360f - bearingDelta;

        // Solo notificar si el cambio de bearing es mayor al umbral
        if (bearingDelta >= BEARING_CHANGE_THRESHOLD && lastRawLocation != null) {
            lastEmittedBearing = currentBearing;
            GnssData compassData = buildGnssData(lastRawLocation, false);
            compassData.bearing        = currentBearing;
            compassData.satelliteCount = satelliteCount;
            compassData.isApproximate  =
                    (lastRawLocation.getAccuracy() > MIN_ACCURACY_METERS);
            notifyData(compassData);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ── Promediado ────────────────────────────────────────────────────────

    private void emitAveragedLocation() {
        if (locationBuffer.isEmpty()) return;

        double avgLat = 0, avgLon = 0, avgAlt = 0;
        float  avgAcc = 0, avgSpd = 0;

        for (Location loc : locationBuffer) {
            avgLat += loc.getLatitude();
            avgLon += loc.getLongitude();
            avgAlt += loc.getAltitude();
            avgAcc += loc.getAccuracy();
            avgSpd += loc.getSpeed();
        }
        int n = locationBuffer.size();
        avgLat /= n; avgLon /= n; avgAlt /= n;
        avgAcc /= n; avgSpd /= n;

        GnssData data = new GnssData();
        data.latitude       = avgLat;
        data.longitude      = avgLon;
        data.altitude       = avgAlt;
        data.accuracy       = avgAcc;
        data.speed          = avgSpd;
        data.bearing        = currentBearing;
        data.satelliteCount = satelliteCount;
        data.hasValidFix    = true;
        data.isApproximate  = false; // promediado = confiable

        // Este dato preciso se puede usar para guardar waypoints
        // Se notifica también como onGnssUpdate normal
        notifyData(data);
        notifyStatus(GnssListener.GnssStatus.READY);
    }

    // ── Utilidades ────────────────────────────────────────────────────────

    private GnssData buildGnssData(Location location, boolean precise) {
        GnssData data = new GnssData();
        data.latitude    = location.getLatitude();
        data.longitude   = location.getLongitude();
        data.altitude    = location.getAltitude();
        data.accuracy    = location.getAccuracy();
        data.speed       = location.getSpeed();
        data.bearing     = location.hasBearing()
                ? location.getBearing() : currentBearing;
        data.hasValidFix = true;
        return data;
    }

    private void notifyData(final GnssData data) {
        if (listener == null) return;
        mainHandler.post(() -> listener.onGnssUpdate(data));
    }

    private void notifyStatus(final GnssListener.GnssStatus status) {
        if (listener == null) return;
        mainHandler.post(() -> listener.onGnssStatusChange(status));
    }
}