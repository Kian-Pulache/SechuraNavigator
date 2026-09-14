// archivo: app/src/main/java/com/sechuranavigator/app/data/local/entities/TrackPointEntity.java
package com.sechuranavigator.app.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "track_points",
        foreignKeys = @ForeignKey(
                entity    = TrackEntity.class,
                parentColumns = "id",
                childColumns  = "track_id",
                onDelete  = ForeignKey.CASCADE  // si se borra el track, se borran sus puntos
        ),
        indices = { @Index("track_id") }
)
public class TrackPointEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "track_id")
    public long trackId;               // FK al track padre

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    @ColumnInfo(name = "altitude")
    public double altitude;

    @ColumnInfo(name = "accuracy")
    public float accuracy;

    @ColumnInfo(name = "speed")
    public float speed;                // m/s

    @ColumnInfo(name = "recorded_at")
    public long recordedAt;            // timestamp del punto

    public TrackPointEntity() {
        recordedAt = System.currentTimeMillis();
    }
}