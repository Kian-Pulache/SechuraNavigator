// archivo: app/src/main/java/com/sechuranavigator/app/data/TrackRepository.java
package com.sechuranavigator.app.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.sechuranavigator.app.data.local.AppDatabase;
import com.sechuranavigator.app.data.local.dao.TrackDao;
import com.sechuranavigator.app.data.local.entities.TrackEntity;
import com.sechuranavigator.app.data.local.entities.TrackPointEntity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TrackRepository {

    private final TrackDao       dao;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public TrackRepository(Context context) {
        dao = AppDatabase.getInstance(context).trackDao();
    }

    // ── Tracks ─────────────────────────────────────────────────────────────

    public void insertTrack(TrackEntity track, OnTrackInsertCallback callback) {
        executor.execute(() -> {
            long id = dao.insertTrack(track);
            if (callback != null) callback.onInserted(id);
        });
    }

    public void updateTrack(TrackEntity track) {
        executor.execute(() -> dao.updateTrack(track));
    }

    public void deleteTrack(long trackId) {
        executor.execute(() -> dao.deleteTrackById(trackId));
    }

    public LiveData<List<TrackEntity>> getAllTracksLive() {
        return dao.getAllTracksLive();
    }

    public void getActiveTrack(OnTrackFetchCallback callback) {
        executor.execute(() -> {
            TrackEntity track = dao.getActiveTrack();
            if (callback != null) callback.onFetched(track);
        });
    }

    public void getTrackById(long id, OnTrackFetchCallback callback) {
        executor.execute(() -> {
            TrackEntity track = dao.getTrackById(id);
            if (callback != null) callback.onFetched(track);
        });
    }

    // ── Puntos ─────────────────────────────────────────────────────────────

    public void insertPoint(TrackPointEntity point) {
        executor.execute(() -> dao.insertPoint(point));
    }

    public void getPointsForTrack(long trackId, OnPointsFetchCallback callback) {
        executor.execute(() -> {
            List<TrackPointEntity> points = dao.getPointsForTrack(trackId);
            if (callback != null) callback.onFetched(points);
        });
    }

    // ── Callbacks ──────────────────────────────────────────────────────────

    public interface OnTrackInsertCallback {
        void onInserted(long newId);
    }

    public interface OnTrackFetchCallback {
        void onFetched(TrackEntity track);
    }

    public interface OnPointsFetchCallback {
        void onFetched(List<TrackPointEntity> points);
    }
}