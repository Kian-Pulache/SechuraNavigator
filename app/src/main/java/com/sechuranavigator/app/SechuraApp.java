// archivo: app/src/main/java/com/sechuranavigator/app/SechuraApp.java
package com.sechuranavigator.app;

import android.app.Application;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class SechuraApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Inicializar Crashlytics
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();

        // Estos datos aparecen en cada reporte de crash
        // para saber en qué módulo ocurrió el problema
        crashlytics.setCustomKey("app_version", "1.0");
        crashlytics.setCustomKey("map_library", "OsmDroid 6.1.18");
        crashlytics.setCustomKey("db_version", "3");
    }
}