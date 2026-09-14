// archivo: app/src/main/java/com/sechuranavigator/app/data/local/entities/TrackEntity.java
package com.sechuranavigator.app.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "tracks")
public class TrackEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "name")
    public String name;                // nombre opcional, ej. "Salida 28 jun"

    @ColumnInfo(name = "started_at")
    public long startedAt;             // Unix timestamp inicio

    @ColumnInfo(name = "ended_at")
    public long endedAt;               // Unix timestamp fin (0 si aún grabando)

    @ColumnInfo(name = "duration_ms")
    public long durationMs;            // duración total en milisegundos

    @ColumnInfo(name = "distance_meters")
    public double distanceMeters;      // distancia total recorrida

    @ColumnInfo(name = "point_count")
    public int pointCount;             // cantidad de puntos GPS grabados

    @ColumnInfo(name = "is_active")
    public boolean isActive;           // true = grabando actualmente

    @ColumnInfo(name = "is_paused")
    public boolean isPaused;           // true = pausado

    public TrackEntity() {
        startedAt = System.currentTimeMillis();
        isActive  = true;
        isPaused  = false;
    }
}