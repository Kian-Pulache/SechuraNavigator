// archivo: app/src/main/java/com/sechuranavigator/app/ui/NavigationActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.data.WaypointRepository;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;
import com.sechuranavigator.app.managers.GnssListener;
import com.sechuranavigator.app.managers.GnssManager;
import com.sechuranavigator.app.managers.NavigationManager;
import com.sechuranavigator.app.models.GnssData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class NavigationActivity extends AppCompatActivity
        implements GnssListener, NavigationManager.NavigationListener {

    // ── Clave del Intent ───────────────────────────────────────────────────
    public static final String EXTRA_WAYPOINT_ID = "waypoint_id";

    // ── Vistas ─────────────────────────────────────────────────────────────
    private MapView              navMapView;
    private NavigationCompassView navCompass;
    private TextView             tvDestinationName, tvDistanceBadge;
    private TextView             tvDistance, tvSpeed, tvBearing;
    private TextView             tvElapsed, tvEta;

    // ── Managers ───────────────────────────────────────────────────────────
    private GnssManager         gnssManager;
    private NavigationManager   navigationManager;
    private WaypointRepository  waypointRepository;

    // ── Estado ─────────────────────────────────────────────────────────────
    private WaypointEntity destination;
    private GnssData       lastGnssData;

    // Overlays del mapa
    private Marker   boatMarker;
    private Marker   destMarker;
    private Polyline routeLine;

    // Para autozoom
    private static final double ZOOM_FAR    = 12.0;
    private static final double ZOOM_MEDIUM = 14.0;
    private static final double ZOOM_NEAR   = 16.0;
    private static final double ZOOM_VERY_NEAR = 17.5;

    private MapScaleView navMapScale;

    // Notificacion de llegada
    private boolean arrivalNotified = false; // evita repetir la notificación

    // ── Ciclo de vida ──────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_navigation);

        bindViews();
        setupMap();

        gnssManager        = new GnssManager(this);
        navigationManager  = new NavigationManager();
        waypointRepository = new WaypointRepository(this);

        gnssManager.setListener(this);
        navigationManager.setListener(this);

        // Cargar el waypoint destino desde el Intent
        long waypointId = getIntent().getLongExtra(EXTRA_WAYPOINT_ID, -1L);
        if (waypointId < 0) { finish(); return; }

        waypointRepository.getById(waypointId, waypoint -> {
            if (waypoint == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Waypoint no encontrado",
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }
            destination = waypoint;
            runOnUiThread(() -> initNavigation(waypoint));
        });

        findViewById(R.id.btnFinish).setOnClickListener(v ->
                confirmFinish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        navMapView.onResume();
        gnssManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        navMapView.onPause();
        gnssManager.stop();
    }

    @Override
    public void onBackPressed() {
        confirmFinish();
    }

    // ── Inicialización ─────────────────────────────────────────────────────

    private void bindViews() {
        navMapView       = findViewById(R.id.navMapView);
        navCompass       = findViewById(R.id.navCompass);
        tvDestinationName = findViewById(R.id.tvDestinationName);
        tvDistanceBadge  = findViewById(R.id.tvDistanceBadge);
        tvDistance       = findViewById(R.id.tvDistance);
        tvSpeed          = findViewById(R.id.tvSpeed);
        tvBearing        = findViewById(R.id.tvBearing);
        tvElapsed        = findViewById(R.id.tvElapsed);
        tvEta            = findViewById(R.id.tvEta);
        navMapScale = findViewById(R.id.navMapScale);
    }

    private void setupMap() {
        navMapView.setTileSource(TileSourceFactory.MAPNIK);
        navMapView.setMultiTouchControls(true);
        navMapView.setBuiltInZoomControls(false);
        navMapView.getController().setZoom(ZOOM_FAR);

        navMapView.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                updateNavScale();
                return false;
            }
            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) {
                updateNavScale();
                return false;
            }
        });
        navMapView.post(this::updateNavScale);
    }

    private void initNavigation(WaypointEntity waypoint) {
        tvDestinationName.setText(
                waypoint.name != null ? waypoint.name : "Sin nombre");

        // Marcador del destino (verde)
        destMarker = new Marker(navMapView);
        destMarker.setPosition(
                new GeoPoint(waypoint.latitude, waypoint.longitude));
        destMarker.setTitle(waypoint.name);
        destMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        destMarker.setIcon(createDestMarker(
                com.sechuranavigator.app.managers.WaypointManager
                        .getColorForSpecies(waypoint.species)));
        destMarker.setInfoWindow(null);
        navMapView.getOverlays().add(destMarker);

        // Marcador del bote (flecha ámbar — se crea en onGnssUpdate)
        // Centramos el mapa en el destino mientras esperamos GPS
        navMapView.getController().setCenter(
                new GeoPoint(waypoint.latitude, waypoint.longitude));

        // Iniciar navegación en el manager
        navigationManager.startNavigation(waypoint);
    }

    // ── GnssListener ───────────────────────────────────────────────────────

    @Override
    public void onGnssUpdate(GnssData data) {
        lastGnssData = data;

        // Actualizar marcador del bote
        updateBoatMarker(data);

        // Actualizar brújula con el rumbo actual del bote
        navCompass.setCurrentBearing(data.bearing);

        // Pasar al NavigationManager para cálculos
        navigationManager.onNewGnssData(data);
    }

    @Override
    public void onGnssStatusChange(GnssListener.GnssStatus status) {
        // No necesitamos reaccionar a cambios de estado en esta pantalla
    }

    // ── NavigationListener ─────────────────────────────────────────────────

    @Override
    public void onNavigationUpdate(NavigationManager.NavigationData data) {
        // Brújula — aguja ámbar apunta al destino
        navCompass.setBearingToDestination(data.bearingToDestination);

        // Distancia
        String distStr = formatDistance(data.distanceMeters);
        tvDistance.setText(distStr);
        tvDistanceBadge.setText(distStr);

        // Velocidad
        tvSpeed.setText(formatSpeed(data.speedMs));

        // Rumbo hacia destino
        tvBearing.setText(String.format(Locale.US,
                "%.0f°", data.bearingToDestination));

        // Tiempo recorrido
        tvElapsed.setText(formatDuration(data.elapsedMs));

        // ETA
        if (data.etaTimestamp > 0) {
            SimpleDateFormat sdf =
                    new SimpleDateFormat("HH:mm", Locale.US);
            tvEta.setText(sdf.format(new Date(data.etaTimestamp)));
        } else {
            tvEta.setText("—:—");
        }

        // Línea de ruta entre bote y destino
        updateRouteLine(data);

        // Autozoom según distancia
        autoZoom(data.distanceMeters);
    }

    @Override
    public void onArrivalDetected() {
        // Solo notificar una vez
        if (arrivalNotified) return;
        arrivalNotified = true;

        // Mostrar un Toast largo — no bloquea la navegación
        // El usuario puede seguir avanzando hacia el punto exacto
        Toast.makeText(this,
                "📍 Cerca de " + (destination != null ? destination.name : "destino")
                        + " — continúa hacia el punto exacto",
                Toast.LENGTH_LONG).show();

        // Resetear después de 30 segundos por si el usuario se aleja y vuelve
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> arrivalNotified = false, 30_000L);
    }

    // ── Overlays del mapa ──────────────────────────────────────────────────

    private void updateBoatMarker(GnssData data) {
        GeoPoint pos = new GeoPoint(data.latitude, data.longitude);

        if (boatMarker == null) {
            boatMarker = new Marker(navMapView);
            boatMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            boatMarker.setIcon(createBoatArrow());
            boatMarker.setInfoWindow(null);
            navMapView.getOverlays().add(boatMarker);
        }

        boatMarker.setPosition(pos);
        boatMarker.setRotation(-data.bearing);

        // Centrar mapa en el bote
        navMapView.getController().animateTo(pos);
        navMapView.invalidate();
    }

    private void updateRouteLine(NavigationManager.NavigationData data) {
        if (lastGnssData == null || destination == null) return;

        List<GeoPoint> points = Arrays.asList(
                new GeoPoint(lastGnssData.latitude, lastGnssData.longitude),
                new GeoPoint(destination.latitude,  destination.longitude)
        );

        if (routeLine == null) {
            routeLine = new Polyline();
            routeLine.getOutlinePaint().setColor(
                    Color.parseColor("#F59E0B"));
            routeLine.getOutlinePaint().setStrokeWidth(4f);
            routeLine.getOutlinePaint().setPathEffect(
                    new android.graphics.DashPathEffect(
                            new float[]{20, 15}, 0));
            navMapView.getOverlays().add(0, routeLine);
        }

        routeLine.setPoints(points);
        navMapView.invalidate();
    }

    // ── Autozoom ───────────────────────────────────────────────────────────

    private void autoZoom(float distanceMeters) {
        if (lastGnssData == null || destination == null) return;

        double lat1 = lastGnssData.latitude;
        double lon1 = lastGnssData.longitude;
        double lat2 = destination.latitude;
        double lon2 = destination.longitude;

        // Centro exacto entre bote y destino
        double centerLat = (lat1 + lat2) / 2.0;
        double centerLon = (lon1 + lon2) / 2.0;

        // Span en grados entre los dos puntos
        double latSpan = Math.abs(lat2 - lat1);
        double lonSpan = Math.abs(lon2 - lon1);

        // Padding del 40% para que los marcadores no queden en el borde
        latSpan = latSpan * 1.4 + 0.0008; // mínimo ~89m de margen
        lonSpan = lonSpan * 1.4 + 0.0008;

        // Tamaño de pantalla disponible para el mapa (mitad superior)
        int mapWidthPx  = navMapView.getWidth();
        int mapHeightPx = navMapView.getHeight();
        if (mapWidthPx == 0 || mapHeightPx == 0) return;

        // Calcular zoom para cada eje
        // A zoom N, un tile de 256px representa (360 / 2^N) grados de longitud
        // Corrección de latitud: los grados de longitud se comprimen con cos(lat)
        double cosLat = Math.cos(Math.toRadians(centerLat));

        double zoomLat = Math.log(mapHeightPx / (latSpan * 256.0 / 360.0))
                / Math.log(2);
        double zoomLon = Math.log(mapWidthPx  / (lonSpan * 256.0 / 360.0 / cosLat))
                / Math.log(2);

        // El zoom más restrictivo garantiza que ambos puntos sean visibles
        double targetZoom = Math.min(zoomLat, zoomLon);

        // Limitar entre zoom 10 (muy alejado) y zoom 18 (muy cerca)
        targetZoom = Math.max(10.0, Math.min(18.0, targetZoom));

        double currentZoom = navMapView.getZoomLevelDouble();

        // Animar solo si el cambio es significativo (evita animaciones constantes)
        if (Math.abs(targetZoom - currentZoom) > 0.5) {
            navMapView.getController().animateTo(
                    new org.osmdroid.util.GeoPoint(centerLat, centerLon),
                    targetZoom,
                    600L  // 600ms de animación suave
            );
        } else {
            // Aunque no cambie el zoom, centrar siempre en el punto medio
            navMapView.getController().animateTo(
                    new org.osmdroid.util.GeoPoint(centerLat, centerLon));
        }
    }

    // ── Finalizar ──────────────────────────────────────────────────────────

    private void confirmFinish() {
        new AlertDialog.Builder(this)
                .setTitle("Finalizar navegación")
                .setMessage("¿Detener la navegación hacia " +
                        (destination != null ? destination.name : "este punto") + "?")
                .setPositiveButton("Finalizar", (d, w) -> {
                    navigationManager.stopNavigation();
                    finish();
                })
                .setNegativeButton("Continuar", null)
                .show();
    }

    // ── Íconos ─────────────────────────────────────────────────────────────

    private android.graphics.drawable.Drawable createBoatArrow() {
        int size = 60;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);

        android.graphics.Paint fill = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.parseColor("#F59E0B"));
        fill.setStyle(android.graphics.Paint.Style.FILL);

        android.graphics.Paint border = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(android.graphics.Paint.Style.STROKE);
        border.setStrokeWidth(3f);

        float cx = size / 2f;
        android.graphics.Path arrow = new android.graphics.Path();
        arrow.moveTo(cx, 4);
        arrow.lineTo(cx - 14, size - 8);
        arrow.lineTo(cx, size - 16);
        arrow.lineTo(cx + 14, size - 8);
        arrow.close();

        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, border);

        return new android.graphics.drawable.BitmapDrawable(
                getResources(), bmp);
    }

    private android.graphics.drawable.Drawable createDestMarker(String hexColor) {
        int size = 50;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);

        android.graphics.Paint fill = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.parseColor(hexColor));

        android.graphics.Paint border = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(android.graphics.Paint.Style.STROKE);
        border.setStrokeWidth(4f);

        // Círculo exterior pulsante (radio mayor)
        android.graphics.Paint outer = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        outer.setColor(Color.parseColor(hexColor));
        outer.setAlpha(60);
        outer.setStyle(android.graphics.Paint.Style.FILL);

        float cx = size / 2f;
        canvas.drawCircle(cx, cx, cx - 2, outer);
        canvas.drawCircle(cx, cx, cx * 0.6f, fill);
        canvas.drawCircle(cx, cx, cx * 0.6f, border);

        // Estrella en el centro
        android.graphics.Paint star = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        star.setColor(Color.WHITE);
        star.setTextSize(18f);
        star.setTextAlign(android.graphics.Paint.Align.CENTER);
        canvas.drawText("★", cx, cx + 6, star);

        return new android.graphics.drawable.BitmapDrawable(
                getResources(), bmp);
    }

    // ── Formateo ───────────────────────────────────────────────────────────

    private String formatDistance(float meters) {
        if (meters >= 1000)
            return String.format(Locale.US, "%.1f km", meters / 1000);
        return String.format(Locale.US, "%.0f m", meters);
    }

    private String formatSpeed(float speedMs) {
        float knots = speedMs * 1.94384f;
        return String.format(Locale.US, "%.1f kt", knots);
    }

    private String formatDuration(long ms) {
        long hours   = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        if (hours > 0)
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void updateNavScale() {
        if (navMapScale == null) return;
        double zoom = navMapView.getZoomLevelDouble();
        org.osmdroid.api.IGeoPoint center = navMapView.getMapCenter();
        navMapScale.updateScale(zoom, center.getLatitude());
    }
}