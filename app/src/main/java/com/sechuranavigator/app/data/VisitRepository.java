// archivo: app/src/main/java/com/sechuranavigator/app/data/VisitRepository.java
package com.sechuranavigator.app.data;

import android.content.Context;

import com.sechuranavigator.app.data.local.AppDatabase;
import com.sechuranavigator.app.data.local.dao.VisitDao;
import com.sechuranavigator.app.data.local.entities.VisitEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VisitRepository {

    private final VisitDao       dao;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public VisitRepository(Context context) {
        dao = AppDatabase.getInstance(context).visitDao();
    }

    public void insert(VisitEntity visit, OnInsertCallback callback) {
        executor.execute(() -> {
            long id = dao.insert(visit);
            if (callback != null) callback.onInserted(id);
        });
    }

    public void update(VisitEntity visit) {
        executor.execute(() -> dao.update(visit));
    }

    public void getActiveVisit(OnFetchCallback callback) {
        executor.execute(() -> {
            VisitEntity visit = dao.getActiveVisit();
            if (callback != null) callback.onFetched(visit);
        });
    }

    public void incrementVisitCount(long waypointId) {
        executor.execute(() -> dao.incrementVisitCount(waypointId));
    }

    public interface OnInsertCallback {
        void onInserted(long newId);
    }

    public interface OnFetchCallback {
        void onFetched(VisitEntity visit);
    }
}