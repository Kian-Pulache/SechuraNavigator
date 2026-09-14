// archivo: app/src/main/java/com/sechuranavigator/app/ui/MapPickerActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.managers.GnssListener;
import com.sechuranavigator.app.managers.GnssManager;
import com.sechuranavigator.app.models.GnssData;

import java.util.Locale;

public class MapPickerActivity extends AppCompatActivity
        implements GnssListener {

    private MapView  mapView;
    private TextView tvPickedCoords;
    private TextView btnConfirm;

    private GnssManager gnssManager;
    private Marker      pickedMarker;
    private double      pickedLat = Double.NaN;
    private double      pickedLon = Double.NaN;

    private static final double SECHURA_LAT = -5.57;
    private static final double SECHURA_LON = -80.81;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_map_picker);

        mapView       = findViewById(R.id.pickerMapView);
        tvPickedCoords = findViewById(R.id.tvPickedCoords);
        btnConfirm    = findViewById(R.id.btnConfirmPicker);

        setupMap();

        gnssManager = new GnssManager(this);
        gnssManager.setListener(this);

        findViewById(R.id.btnCancelPicker)
                .setOnClickListener(v -> {
                    setResult(RESULT_CANCELED);
                    finish();
                });

        btnConfirm.setOnClickListener(v -> {
            if (!Double.isNaN(pickedLat)) {
                Intent result = new Intent();
                result.putExtra("picked_lat", pickedLat);
                result.putExtra("picked_lon", pickedLon);
                setResult(RESULT_OK, result);
                finish();
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        gnssManager.start();
    }

    @Override protected void onPause() {
        super.onPause();
        mapView.onPause();
        gnssManager.stop();
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.getController().setZoom(13.0);
        mapView.getController().setCenter(
                new GeoPoint(SECHURA_LAT, SECHURA_LON));

        // Listener de toque en el mapa
        MapEventsOverlay eventsOverlay = new MapEventsOverlay(
                new MapEventsReceiver() {
                    @Override
                    public boolean singleTapConfirmedHelper(GeoPoint p) {
                        onMapTapped(p);
                        return true;
                    }
                    @Override
                    public boolean longPressHelper(GeoPoint p) {
                        return false;
                    }
                }
        );
        mapView.getOverlays().add(0, eventsOverlay);
    }

    private void onMapTapped(GeoPoint point) {
        pickedLat = point.getLatitude();
        pickedLon = point.getLongitude();

        // Mostrar o mover el marcador
        if (pickedMarker == null) {
            pickedMarker = new Marker(mapView);
            pickedMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            pickedMarker.setInfoWindow(null);
            mapView.getOverlays().add(pickedMarker);
        }
        pickedMarker.setPosition(point);
        mapView.invalidate();

        // Mostrar coordenadas
        tvPickedCoords.setVisibility(View.VISIBLE);
        tvPickedCoords.setText(String.format(Locale.US,
                "%.6f° S  %.6f° W",
                Math.abs(pickedLat), Math.abs(pickedLon)));

        // Habilitar botón confirmar
        btnConfirm.setEnabled(true);
        btnConfirm.setAlpha(1.0f);
    }

    // ── GnssListener — centrar mapa en posición real ──────────────────────

    @Override
    public void onGnssUpdate(GnssData data) {
        // Solo centramos el mapa la primera vez que hay fix
        // para que el usuario sepa dónde está
        if (pickedMarker == null) {
            mapView.getController().animateTo(
                    new GeoPoint(data.latitude, data.longitude));
        }
    }

    @Override
    public void onGnssStatusChange(GnssListener.GnssStatus status) {}
}