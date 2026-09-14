// archivo: app/src/main/java/com/sechuranavigator/app/managers/SettingsManager.java
package com.sechuranavigator.app.managers;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private static final String PREFS_NAME = "sechura_settings";

    // ── Claves ─────────────────────────────────────────────────────────────
    public static final String KEY_COORD_FORMAT    = "coord_format";
    public static final String KEY_DISTANCE_UNIT   = "distance_unit";
    public static final String KEY_SPEED_UNIT      = "speed_unit";
    public static final String KEY_MIN_ACCURACY    = "min_accuracy";
    public static final String KEY_READINGS_AVG    = "readings_avg";
    public static final String KEY_TRACK_INTERVAL  = "track_interval";
    public static final String KEY_ARRIVAL_RADIUS  = "arrival_radius";
    public static final String KEY_MIN_STAY        = "min_stay";
    public static final String KEY_ASK_PRODUCTION  = "ask_production";
    public static final String KEY_MAP_ORIENTATION = "map_orientation";
    public static final String KEY_SHOW_LABELS     = "show_labels";
    public static final String KEY_KEEP_SCREEN_ON  = "keep_screen_on";
    public static final String KEY_MAX_BRIGHTNESS  = "max_brightness";

    // ── Valores por defecto ────────────────────────────────────────────────
    public static final String DEFAULT_COORD_FORMAT   = "DD";     // DD, DMS, DDM, UTM
    public static final String DEFAULT_DISTANCE_UNIT  = "m";      // m, ft
    public static final String DEFAULT_SPEED_UNIT     = "kn";     // kn, km/h, m/s
    public static final float  DEFAULT_MIN_ACCURACY   = 8f;       // metros
    public static final int    DEFAULT_READINGS_AVG   = 10;       // lecturas
    public static final int    DEFAULT_TRACK_INTERVAL = 5;        // segundos
    public static final float  DEFAULT_ARRIVAL_RADIUS = 25f;      // metros
    public static final int    DEFAULT_MIN_STAY       = 60;       // segundos
    public static final boolean DEFAULT_ASK_PRODUCTION = true;
    public static final String DEFAULT_MAP_ORIENTATION = "north"; // north, heading
    public static final boolean DEFAULT_SHOW_LABELS   = true;
    public static final boolean DEFAULT_KEEP_SCREEN   = true;
    public static final boolean DEFAULT_MAX_BRIGHTNESS = true;

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public String getCoordFormat()   { return prefs.getString(KEY_COORD_FORMAT, DEFAULT_COORD_FORMAT); }
    public String getDistanceUnit()  { return prefs.getString(KEY_DISTANCE_UNIT, DEFAULT_DISTANCE_UNIT); }
    public String getSpeedUnit()     { return prefs.getString(KEY_SPEED_UNIT, DEFAULT_SPEED_UNIT); }
    public float  getMinAccuracy()   { return prefs.getFloat(KEY_MIN_ACCURACY, DEFAULT_MIN_ACCURACY); }
    public int    getReadingsAvg()   { return prefs.getInt(KEY_READINGS_AVG, DEFAULT_READINGS_AVG); }
    public int    getTrackInterval() { return prefs.getInt(KEY_TRACK_INTERVAL, DEFAULT_TRACK_INTERVAL); }
    public float  getArrivalRadius() { return prefs.getFloat(KEY_ARRIVAL_RADIUS, DEFAULT_ARRIVAL_RADIUS); }
    public int    getMinStay()       { return prefs.getInt(KEY_MIN_STAY, DEFAULT_MIN_STAY); }
    public boolean askProduction()   { return prefs.getBoolean(KEY_ASK_PRODUCTION, DEFAULT_ASK_PRODUCTION); }
    public String getMapOrientation(){ return prefs.getString(KEY_MAP_ORIENTATION, DEFAULT_MAP_ORIENTATION); }
    public boolean showLabels()      { return prefs.getBoolean(KEY_SHOW_LABELS, DEFAULT_SHOW_LABELS); }
    public boolean keepScreenOn()    { return prefs.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN); }
    public boolean maxBrightness()   { return prefs.getBoolean(KEY_MAX_BRIGHTNESS, DEFAULT_MAX_BRIGHTNESS); }

    // ── Setters ────────────────────────────────────────────────────────────

    public void setCoordFormat(String v)   { prefs.edit().putString(KEY_COORD_FORMAT, v).apply(); }
    public void setDistanceUnit(String v)  { prefs.edit().putString(KEY_DISTANCE_UNIT, v).apply(); }
    public void setSpeedUnit(String v)     { prefs.edit().putString(KEY_SPEED_UNIT, v).apply(); }
    public void setMinAccuracy(float v)    { prefs.edit().putFloat(KEY_MIN_ACCURACY, v).apply(); }
    public void setReadingsAvg(int v)      { prefs.edit().putInt(KEY_READINGS_AVG, v).apply(); }
    public void setTrackInterval(int v)    { prefs.edit().putInt(KEY_TRACK_INTERVAL, v).apply(); }
    public void setArrivalRadius(float v)  { prefs.edit().putFloat(KEY_ARRIVAL_RADIUS, v).apply(); }
    public void setMinStay(int v)          { prefs.edit().putInt(KEY_MIN_STAY, v).apply(); }
    public void setAskProduction(boolean v){ prefs.edit().putBoolean(KEY_ASK_PRODUCTION, v).apply(); }
    public void setMapOrientation(String v){ prefs.edit().putString(KEY_MAP_ORIENTATION, v).apply(); }
    public void setShowLabels(boolean v)   { prefs.edit().putBoolean(KEY_SHOW_LABELS, v).apply(); }
    public void setKeepScreenOn(boolean v) { prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, v).apply(); }
    public void setMaxBrightness(boolean v){ prefs.edit().putBoolean(KEY_MAX_BRIGHTNESS, v).apply(); }

    // ── Formato de coordenadas ─────────────────────────────────────────────

    /**
     * Formatea una coordenada según el formato configurado.
     */
    public String formatCoordinate(double latitude, double longitude) {
        switch (getCoordFormat()) {
            case "DMS": return toDMS(latitude, longitude);
            case "DDM": return toDDM(latitude, longitude);
            case "UTM": return toUTM(latitude, longitude);
            default:    return String.format(java.util.Locale.US,
                    "%.6f°, %.6f°", latitude, longitude);
        }
    }

    private String toDMS(double lat, double lon) {
        return dmsString(lat, "S", "N") + "\n" + dmsString(lon, "W", "E");
    }

    private String dmsString(double val, String neg, String pos) {
        String hemi = val < 0 ? neg : pos;
        val = Math.abs(val);
        int deg = (int) val;
        double minFull = (val - deg) * 60;
        int min = (int) minFull;
        double sec = (minFull - min) * 60;
        return String.format(java.util.Locale.US,
                "%d°%02d'%05.2f\"%s", deg, min, sec, hemi);
    }

    private String toDDM(double lat, double lon) {
        return ddmString(lat, "S", "N") + "\n" + ddmString(lon, "W", "E");
    }

    private String ddmString(double val, String neg, String pos) {
        String hemi = val < 0 ? neg : pos;
        val = Math.abs(val);
        int deg = (int) val;
        double min = (val - deg) * 60;
        return String.format(java.util.Locale.US,
                "%d°%08.5f'%s", deg, min, hemi);
    }

    private String toUTM(double lat, double lon) {
        // Conversión UTM simplificada para zona 17S (Sechura)
        // Para producción usar una librería completa como proj4j
        int zone = (int) Math.floor((lon + 180) / 6) + 1;
        String band = lat < 0 ? "S" : "N";
        return String.format(java.util.Locale.US,
                "%d%s (aprox.)", zone, band);
    }

    // ── Formato de velocidad ───────────────────────────────────────────────

    public String formatSpeed(float speedMs) {
        switch (getSpeedUnit()) {
            case "km/h": return String.format(java.util.Locale.US,
                    "%.1f km/h", speedMs * 3.6f);
            case "m/s":  return String.format(java.util.Locale.US,
                    "%.1f m/s", speedMs);
            default:     return String.format(java.util.Locale.US,
                    "%.1f kt", speedMs * 1.94384f);
        }
    }

    // ── Formato de distancia ───────────────────────────────────────────────

    public String formatDistance(double meters) {
        if ("ft".equals(getDistanceUnit())) {
            double feet = meters * 3.28084;
            if (feet > 5280)
                return String.format(java.util.Locale.US, "%.1f mi", feet/5280);
            return String.format(java.util.Locale.US, "%.0f ft", feet);
        }
        if (meters >= 1000)
            return String.format(java.util.Locale.US, "%.1f km", meters/1000);
        return String.format(java.util.Locale.US, "%.0f m", meters);
    }

    // ── Reset ──────────────────────────────────────────────────────────────

    public void resetToDefaults() {
        prefs.edit().clear().apply();
    }
}