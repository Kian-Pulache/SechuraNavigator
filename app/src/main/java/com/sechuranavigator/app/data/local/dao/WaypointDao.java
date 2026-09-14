// archivo: app/src/main/java/com/sechuranavigator/app/data/local/dao/WaypointDao.java
package com.sechuranavigator.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.sechuranavigator.app.data.local.entities.WaypointEntity;

import java.util.List;

@Dao
public interface WaypointDao {

    // ── Escritura ─────────────────────────────────────────────────────────

    @Insert
    long insert(WaypointEntity waypoint);
    // Devuelve el ID asignado automáticamente — útil para saber qué se guardó

    @Update
    void update(WaypointEntity waypoint);

    @Delete
    void delete(WaypointEntity waypoint);

    @Query("DELETE FROM waypoints WHERE id = :id")
    void deleteById(long id);

    // ── Lectura ───────────────────────────────────────────────────────────

    // LiveData: la UI se actualiza automáticamente cuando cambian los datos
    @Query("SELECT * FROM waypoints ORDER BY created_at DESC")
    LiveData<List<WaypointEntity>> getAllLive();

    // Lista normal (sin LiveData) — para operaciones en background
    @Query("SELECT * FROM waypoints ORDER BY created_at DESC")
    List<WaypointEntity> getAll();

    @Query("SELECT * FROM waypoints WHERE id = :id LIMIT 1")
    WaypointEntity getById(long id);

    // Búsqueda por nombre o especie
    @Query("SELECT * FROM waypoints WHERE name LIKE '%' || :query || '%' " +
            "OR species LIKE '%' || :query || '%' ORDER BY created_at DESC")
    LiveData<List<WaypointEntity>> searchLive(String query);

    // Filtrar por especie
    @Query("SELECT * FROM waypoints WHERE species = :species ORDER BY created_at DESC")
    LiveData<List<WaypointEntity>> getBySpeciesLive(String species);

    // Contar waypoints — útil para el título "Mis puntos (47)"
    @Query("SELECT COUNT(*) FROM waypoints")
    int count();

    @Query("DELETE FROM waypoints")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM waypoints")
    int countAll();
}