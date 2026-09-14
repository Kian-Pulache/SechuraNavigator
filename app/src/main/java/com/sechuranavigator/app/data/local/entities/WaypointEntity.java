// archivo: app/src/main/java/com/sechuranavigator/app/data/local/entities/WaypointEntity.java
package com.sechuranavigator.app.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "waypoints")
public class WaypointEntity {

    // ── Identificador ─────────────────────────────────────────────────────
    @PrimaryKey(autoGenerate = true)
    public long id;

    // ── Posición GPS ──────────────────────────────────────────────────────
    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    @ColumnInfo(name = "altitude")
    public double altitude;

    @ColumnInfo(name = "accuracy")
    public float accuracy;

    @ColumnInfo(name = "satellite_count")
    public int satelliteCount;

    // ── Información del punto ─────────────────────────────────────────────
    @ColumnInfo(name = "name")
    public String name;             // puede ser null (nombre opcional)

    @ColumnInfo(name = "description")
    public String description;      // comentarios adicionales

    @ColumnInfo(name = "species")
    public String species;          // especie asociada (puede ser null)

    @ColumnInfo(name = "species_color")
    public String speciesColor;     // color hex del marcador, ej. "#F59E0B"

    // ── Producción (se llena en el Motor de Visitas, Módulo 8) ────────────
    @ColumnInfo(name = "last_catch_kg")
    public float lastCatchKg;       // última cantidad registrada en kg

    // ── Timestamps ────────────────────────────────────────────────────────
    @ColumnInfo(name = "created_at")
    public long createdAt;          // Unix timestamp en milisegundos

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    // ── Flags ─────────────────────────────────────────────────────────────
    @ColumnInfo(name = "is_favorite")
    public boolean isFavorite;

    @ColumnInfo(name = "visit_count")
    public int visitCount;          // cuántas veces se ha visitado este punto

    // ── Constructor ───────────────────────────────────────────────────────
    public WaypointEntity() {
        createdAt = System.currentTimeMillis();
        updatedAt = System.currentTimeMillis();
        isFavorite = false;
        visitCount = 0;
        lastCatchKg = 0f;
    }

    // ID compuesto que incluye el userId de Firebase para identificar
    // de forma única los waypoints entre diferentes usuarios en la nube.
    // Formato: "USRxxxxx_001" donde xxxxx son los primeros 5 chars del userId
    // Se rellena al crear el waypoint si hay sesión activa.
    @ColumnInfo(name = "composite_id")
    public String compositeId;
}