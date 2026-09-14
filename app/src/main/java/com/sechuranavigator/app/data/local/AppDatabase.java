// archivo: app/src/main/java/com/sechuranavigator/app/data/local/AppDatabase.java
package com.sechuranavigator.app.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.sechuranavigator.app.data.local.dao.TrackDao;
import com.sechuranavigator.app.data.local.dao.VisitDao;
import com.sechuranavigator.app.data.local.dao.WaypointDao;
import com.sechuranavigator.app.data.local.entities.TrackEntity;
import com.sechuranavigator.app.data.local.entities.TrackPointEntity;
import com.sechuranavigator.app.data.local.entities.VisitEntity;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;

@Database(
        entities = {
                WaypointEntity.class,
                TrackEntity.class,
                TrackPointEntity.class,
                VisitEntity.class
        },
        version = 4,
        exportSchema = false
)

public abstract class AppDatabase extends RoomDatabase {

    // El nombre del archivo SQLite en el dispositivo
    private static final String DB_NAME = "sechura_navigator.db";

    // Instancia única (Singleton)
    private static volatile AppDatabase instance;

    // Migración de versión 3 a 4
// Agrega los nuevos campos a visits y cambia el tipo de id en waypoints
    static final androidx.room.migration.Migration MIGRATION_3_4 =
            new androidx.room.migration.Migration(3, 4) {
                @Override
                public void migrate(
                        @androidx.annotation.NonNull
                        androidx.sqlite.db.SupportSQLiteDatabase db) {

                    // ── Nuevos campos en visits ────────────────────────────────
                    // ALTER TABLE en SQLite solo permite agregar columnas,
                    // nunca eliminar ni modificar. Esto es seguro y no pierde datos.

                    db.execSQL("ALTER TABLE visits ADD COLUMN " +
                            "catch_quantity TEXT");

                    db.execSQL("ALTER TABLE visits ADD COLUMN " +
                            "num_persons INTEGER NOT NULL DEFAULT 1");

                    db.execSQL("ALTER TABLE visits ADD COLUMN " +
                            "catch_kg_per_person REAL NOT NULL DEFAULT 0");

                    db.execSQL("ALTER TABLE visits ADD COLUMN " +
                            "distance_from_port_m REAL NOT NULL DEFAULT 0");

                    db.execSQL("ALTER TABLE visits ADD COLUMN " +
                            "condition_tags TEXT");

                    // ── NOTA sobre waypoints.id ────────────────────────────────
                    // SQLite NO permite cambiar el tipo de una columna PK.
                    // La solución es mantener id como INTEGER (Room lo exige para
                    // autoGenerate) y agregar una columna separada para el ID
                    // compuesto con userId. Ver Paso 2b para el detalle.

                    db.execSQL("ALTER TABLE waypoints ADD COLUMN " +
                            "composite_id TEXT");
                }
            };

    // ── Singleton thread-safe ─────────────────────────────────────────────
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DB_NAME
                            )
                            .addMigrations(MIGRATION_3_4)
                            // ↑ Durante desarrollo: si cambias el esquema,
                            //   borra y recrea la BD en vez de fallar.
                            //   IMPORTANTE: en producción (v2.0) esto debe
                            //   reemplazarse con migraciones explícitas para
                            //   no perder los datos del usuario.
                            .build();
                }
            }
        }
        return instance;
    }

    // ── DAOs disponibles ──────────────────────────────────────────────────
    public abstract WaypointDao waypointDao();
    public abstract TrackDao trackDao();
    public abstract VisitDao visitDao();
}