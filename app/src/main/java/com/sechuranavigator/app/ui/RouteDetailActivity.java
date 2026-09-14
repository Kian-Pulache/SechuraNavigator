// archivo: app/src/main/java/com/sechuranavigator/app/ui/RouteDetailActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.data.TrackRepository;
import com.sechuranavigator.app.data.local.entities.TrackEntity;
import com.sechuranavigator.app.data.local.entities.TrackPointEntity;
import com.sechuranavigator.app.utils.ExportManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RouteDetailActivity extends AppCompatActivity {

    private MapView         mapView;
    private TextView        tvTrackName, tvStatDistance,
            tvStatDuration, tvStatPoints;
    private TrackRepository repository;
    private long            trackId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_route_detail);

        tvTrackName    = findViewById(R.id.tvTrackName);
        tvStatDistance = findViewById(R.id.tvStatDistance);
        tvStatDuration = findViewById(R.id.tvStatDuration);
        tvStatPoints   = findViewById(R.id.tvStatPoints);
        mapView        = findViewById(R.id.mapView);

        repository = new TrackRepository(this);
        trackId    = getIntent().getLongExtra("track_id", -1L);

        setupMap();
        loadTrackData();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnExport).setOnClickListener(v -> exportTrackKml());
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause()  { super.onPause();  mapView.onPause(); }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
    }

    private void loadTrackData() {
        if (trackId < 0) { finish(); return; }

        repository.getTrackById(trackId, track -> {
            if (track == null) { finish(); return; }

            repository.getPointsForTrack(trackId, points -> {
                runOnUiThread(() -> {
                    displayTrackInfo(track);
                    drawTrackOnMap(track, points);
                });
            });
        });
    }

    private void displayTrackInfo(TrackEntity track) {
        tvTrackName.setText(track.name != null ? track.name : "Ruta");

        // Distancia
        if (track.distanceMeters < 1000)
            tvStatDistance.setText(String.format(Locale.US,
                    "%.0f m", track.distanceMeters));
        else
            tvStatDistance.setText(String.format(Locale.US,
                    "%.1f km", track.distanceMeters / 1000));

        // Duración
        long hours   = TimeUnit.MILLISECONDS.toHours(track.durationMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(track.durationMs) % 60;
        if (hours > 0)
            tvStatDuration.setText(
                    String.format(Locale.US, "%dh %02dm", hours, minutes));
        else
            tvStatDuration.setText(
                    String.format(Locale.US, "%d min", minutes));

        // Puntos
        tvStatPoints.setText(track.pointCount + " puntos");
    }

    private void drawTrackOnMap(TrackEntity track,
                                List<TrackPointEntity> points) {
        if (points.isEmpty()) return;

        // Dibujar línea del recorrido
        List<GeoPoint> geoPoints = new ArrayList<>();
        for (TrackPointEntity p : points) {
            geoPoints.add(new GeoPoint(p.latitude, p.longitude));
        }

        Polyline polyline = new Polyline();
        polyline.setPoints(geoPoints);
        polyline.getOutlinePaint().setColor(Color.parseColor("#F59E0B"));
        polyline.getOutlinePaint().setStrokeWidth(6f);
        polyline.getOutlinePaint().setStrokeCap(
                android.graphics.Paint.Cap.ROUND);
        mapView.getOverlays().add(polyline);

        // Marcador de inicio (verde)
        TrackPointEntity first = points.get(0);
        Marker startMarker = new Marker(mapView);
        startMarker.setPosition(new GeoPoint(first.latitude, first.longitude));
        startMarker.setTitle("Inicio");
        startMarker.setSnippet(formatTime(track.startedAt));
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        startMarker.setIcon(createCircleDrawable(Color.parseColor("#10B981")));
        mapView.getOverlays().add(startMarker);

        // Marcador de fin (rojo)
        TrackPointEntity last = points.get(points.size() - 1);
        Marker endMarker = new Marker(mapView);
        endMarker.setPosition(new GeoPoint(last.latitude, last.longitude));
        endMarker.setTitle("Fin");
        endMarker.setSnippet(formatTime(track.endedAt));
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        endMarker.setIcon(createCircleDrawable(Color.parseColor("#EF4444")));
        mapView.getOverlays().add(endMarker);

        mapView.invalidate();

        // Ajustar zoom para ver todo el track
        BoundingBox box = BoundingBox.fromGeoPoints(geoPoints);
        mapView.post(() ->
                mapView.zoomToBoundingBox(box, true, 80));
    }

    private android.graphics.drawable.Drawable createCircleDrawable(int color) {
        int size = 40;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        android.graphics.Paint fill = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        android.graphics.Paint border = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(android.graphics.Paint.Style.STROKE);
        border.setStrokeWidth(3f);
        float cx = size / 2f, r = size / 2f - 3f;
        canvas.drawCircle(cx, cx, r, fill);
        canvas.drawCircle(cx, cx, r, border);
        return new android.graphics.drawable.BitmapDrawable(
                getResources(), bmp);
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "HH:mm", Locale.US);
        return sdf.format(new Date(timestamp));
    }

    private void exportTrackKml() {
        repository.getTrackById(trackId, track -> {
            if (track == null) return;
            repository.getPointsForTrack(trackId, points -> {
                try {
                    ExportManager exporter = new ExportManager(this);
                    File file = exporter.exportTrackKml(track, points);
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                "Ruta exportada: " + points.size() + " puntos",
                                Toast.LENGTH_SHORT).show();
                        startActivity(Intent.createChooser(
                                exporter.createShareIntent(file), "Compartir KML"));
                    });
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Error: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                }
            });
        });
    }
}