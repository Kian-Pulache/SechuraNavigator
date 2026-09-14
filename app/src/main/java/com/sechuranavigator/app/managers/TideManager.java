// archivo: app/src/main/java/com/sechuranavigator/app/managers/TideManager.java
package com.sechuranavigator.app.managers;

import android.os.Handler;
import android.os.Looper;

import com.sechuranavigator.app.models.TideData;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TideManager {

    private static final long UPDATE_INTERVAL_MINUTES = 5;

    private final TideEngine              engine  = new TideEngine();
    private final Handler                 mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private TideListener    listener;
    private ScheduledFuture<?> scheduledTask;
    private TideData        lastData;

    public void setListener(TideListener listener) {
        this.listener = listener;
    }

    // ── Ciclo de vida ──────────────────────────────────────────────────────

    public void start() {
        // Calcular inmediatamente al iniciar
        computeAndNotify();

        // Luego cada 5 minutos
        scheduledTask = scheduler.scheduleAtFixedRate(
                this::computeAndNotify,
                UPDATE_INTERVAL_MINUTES,
                UPDATE_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    public TideData getLastData() {
        return lastData;
    }

    // ── Cálculo en background ──────────────────────────────────────────────

    private void computeAndNotify() {
        TideData data = engine.compute();
        lastData = data;

        mainHandler.post(() -> {
            if (listener != null) listener.onTideUpdate(data);
        });
    }

    // ── Listener ───────────────────────────────────────────────────────────

    public interface TideListener {
        void onTideUpdate(TideData data);
    }
}