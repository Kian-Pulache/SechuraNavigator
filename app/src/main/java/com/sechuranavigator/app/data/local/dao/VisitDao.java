// archivo: app/src/main/java/com/sechuranavigator/app/data/local/dao/VisitDao.java
package com.sechuranavigator.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.sechuranavigator.app.data.local.entities.VisitEntity;

import java.util.List;

@Dao
public interface VisitDao {

    @Insert
    long insert(VisitEntity visit);

    @Update
    void update(VisitEntity visit);

    @Query("SELECT * FROM visits WHERE waypoint_id = :waypointId " +
            "ORDER BY arrived_at DESC")
    List<VisitEntity> getVisitsForWaypoint(long waypointId);

    @Query("SELECT * FROM visits WHERE is_active = 1 LIMIT 1")
    VisitEntity getActiveVisit();

    @Query("SELECT COUNT(*) FROM visits WHERE waypoint_id = :waypointId")
    int countVisitsForWaypoint(long waypointId);

    @Query("UPDATE waypoints SET visit_count = visit_count + 1 " +
            "WHERE id = :waypointId")
    void incrementVisitCount(long waypointId);
}