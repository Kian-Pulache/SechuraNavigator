// archivo: app/src/main/java/com/sechuranavigator/app/data/local/entities/VisitEntity.java
package com.sechuranavigator.app.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "visits",
        foreignKeys = @ForeignKey(
                entity       = WaypointEntity.class,
                parentColumns = "id",
                childColumns  = "waypoint_id",
                onDelete     = ForeignKey.CASCADE
        ),
        indices = { @Index("waypoint_id") }
)
public class VisitEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "waypoint_id")
    public long waypointId;

    @ColumnInfo(name = "arrived_at")
    public long arrivedAt;           // timestamp de llegada

    @ColumnInfo(name = "departed_at")
    public long departedAt;          // timestamp de salida (0 si aún en el punto)

    @ColumnInfo(name = "duration_ms")
    public long durationMs;          // permanencia total en ms

    @ColumnInfo(name = "avg_accuracy")
    public float avgAccuracy;        // precisión GPS promedio durante la visita

    @ColumnInfo(name = "catch_kg")
    public float catchKg;            // producción registrada (0 si no se registró)

    @ColumnInfo(name = "catch_notes")
    public String catchNotes;        // notas opcionales de producción

    @ColumnInfo(name = "is_active")
    public boolean isActive;         // true = aún dentro del waypoint

    // ── Campos nuevos v4 ──────────────────────────────────────────────────

    // Producción en texto libre — el pescador usa la unidad que necesite.
// Ejemplos: "20 valdes", "5 cajas", "8 pulpos", "20 kg", "250 caracoles"
// No reemplaza catchKg — ambos coexisten para compatibilidad con
// datos históricos y para el cálculo de catchKgPerPerson.
    @ColumnInfo(name = "catch_quantity")
    public String catchQuantity;

    // Número de personas que fueron a pescar en esta salida.
// Permite normalizar la producción por esfuerzo de pesca.
    @ColumnInfo(name = "num_persons")
    public int numPersons = 1;

    // Producción en kg dividida entre numPersons.
    // Solo se calcula cuando catchKg > 0 y la unidad es kg.
    // Permite comparar días con diferente número de tripulantes.
    @ColumnInfo(name = "catch_kg_per_person")
    public float catchKgPerPerson = 0f;

    // Distancia total recorrida desde el puerto hasta este punto
    // en metros. Se toma automáticamente del track grabado cuando existe.
    // Representa el esfuerzo de transporte y consumo de combustible.
    @ColumnInfo(name = "distance_from_port_m")
    public float distanceFromPortM = 0f;

    // Etiquetas de condición del agua seleccionadas por el pescador
    // al salir del punto. Pregunta única, selección múltiple.
    // Opciones: "corrientuda", "turbia", "amarillenta", "agua movida",
    //           "viento fuerte", "normal", "ns" (no sabe)
    // Si está vacío o null = condiciones normales (equivale a "normal").
    // Separadas por coma cuando hay múltiples: "corrientuda,turbia"
    @ColumnInfo(name = "condition_tags")
    public String conditionTags;

    public VisitEntity() {
        arrivedAt = System.currentTimeMillis();
        isActive  = true;
        catchKg   = 0f;
    }
}