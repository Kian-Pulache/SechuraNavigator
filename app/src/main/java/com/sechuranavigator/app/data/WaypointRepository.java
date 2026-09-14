// archivo: app/src/main/java/com/sechuranavigator/app/data/WaypointRepository.java
package com.sechuranavigator.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.sechuranavigator.app.data.local.AppDatabase;
import com.sechuranavigator.app.data.local.dao.WaypointDao;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WaypointRepository {

    private final WaypointDao dao;

    // Pool de 2 hilos para operaciones de BD en background
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public WaypointRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        dao = db.waypointDao();
    }

    // ── Escritura (en background) ─────────────────────────────────────────

    public void insert(WaypointEntity waypoint, OnInsertCallback callback) {
        executor.execute(() -> {
            long id = dao.insert(waypoint);
            if (callback != null) callback.onInserted(id);
        });
    }

    public void update(WaypointEntity waypoint) {
        executor.execute(() -> dao.update(waypoint));
    }

    public void deleteById(long id) {
        executor.execute(() -> dao.deleteById(id));
    }

    // ── Lectura (LiveData — Room maneja el hilo automáticamente) ──────────

    public LiveData<List<WaypointEntity>> getAllLive() {
        return dao.getAllLive();
    }

    public LiveData<List<WaypointEntity>> searchLive(String query) {
        return dao.searchLive(query);
    }

    public LiveData<List<WaypointEntity>> getBySpeciesLive(String species) {
        return dao.getBySpeciesLive(species);
    }

    // ── Lectura puntual (en background) ──────────────────────────────────

    public void getById(long id, OnFetchCallback callback) {
        executor.execute(() -> {
            WaypointEntity entity = dao.getById(id);
            if (callback != null) callback.onFetched(entity);
        });
    }

    public void count(OnCountCallback callback) {
        executor.execute(() -> {
            int total = dao.count();
            if (callback != null) callback.onCount(total);
        });
    }

    // ── Interfaces de callback ────────────────────────────────────────────

    public interface OnInsertCallback {
        void onInserted(long newId);
    }

    public interface OnFetchCallback {
        void onFetched(WaypointEntity entity);
    }

    public interface OnCountCallback {
        void onCount(int count);
    }

    public void countAll(java.util.function.Consumer<Integer> callback) {
        executor.execute(() -> {
            int count = dao.countAll();
            new Handler(Looper.getMainLooper()).post(() -> callback.accept(count));
        });
    }
}