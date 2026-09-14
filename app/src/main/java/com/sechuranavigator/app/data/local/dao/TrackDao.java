// archivo: app/src/main/java/com/sechuranavigator/app/data/local/dao/TrackDao.java
package com.sechuranavigator.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.sechuranavigator.app.data.local.entities.TrackEntity;
import com.sechuranavigator.app.data.local.entities.TrackPointEntity;

import java.util.List;

@Dao
public interface TrackDao {

    // ── Tracks ────────────────────────────────────────────────────────────

    @Insert
    long insertTrack(TrackEntity track);

    @Update
    void updateTrack(TrackEntity track);

    @Query("SELECT * FROM tracks ORDER BY started_at DESC")
    LiveData<List<TrackEntity>> getAllTracksLive();

    @Query("SELECT * FROM tracks ORDER BY started_at DESC")
    List<TrackEntity> getAllTracks();

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    TrackEntity getTrackById(long id);

    @Query("SELECT * FROM tracks WHERE is_active = 1 LIMIT 1")
    TrackEntity getActiveTrack();

    @Query("DELETE FROM tracks WHERE id = :id")
    void deleteTrackById(long id);

    // ── Puntos ────────────────────────────────────────────────────────────

    @Insert
    long insertPoint(TrackPointEntity point);

    @Query("SELECT * FROM track_points WHERE track_id = :trackId ORDER BY recorded_at ASC")
    List<TrackPointEntity> getPointsForTrack(long trackId);

    @Query("SELECT COUNT(*) FROM track_points WHERE track_id = :trackId")
    int countPointsForTrack(long trackId);
}