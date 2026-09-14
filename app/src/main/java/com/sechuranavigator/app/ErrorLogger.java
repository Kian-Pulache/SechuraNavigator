// archivo: app/src/main/java/com/sechuranavigator/app/ErrorLogger.java
package com.sechuranavigator.app;

import android.util.Log;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class ErrorLogger {

    private static final String TAG = "SechuraNav";

    /**
     * Registra un error no fatal — aparece en Crashlytics sin cerrar la app.
     * Úsalo en catch blocks donde puedes recuperarte del error.
     */
    public static void logError(String context, Exception e) {
        Log.e(TAG, "[" + context + "] " + e.getMessage(), e);
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.log("Error en: " + context);
            crashlytics.recordException(e);
        } catch (Exception ignored) {
            // Si Crashlytics falla, no queremos otro crash
        }
    }

    /**
     * Registra un evento importante sin error.
     * Aparece como breadcrumb en el reporte de crash.
     */
    public static void log(String message) {
        Log.d(TAG, message);
        try {
            FirebaseCrashlytics.getInstance().log(message);
        } catch (Exception ignored) {}
    }

    /**
     * Registra un error de BD — muy común en apps con Room.
     */
    public static void logDbError(String operation, Exception e) {
        logError("DB:" + operation, e);
    }

    /**
     * Registra un error de GPS.
     */
    public static void logGpsError(String context, Exception e) {
        logError("GPS:" + context, e);
    }
}