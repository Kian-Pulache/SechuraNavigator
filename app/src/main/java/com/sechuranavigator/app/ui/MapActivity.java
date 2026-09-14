// archivo: app/src/main/java/com/sechuranavigator/app/ui/MapActivity.java
package com.sechuranavigator.app.ui;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import com.sechuranavigator.app.R;
import com.sechuranavigator.app.data.local.entities.TrackEntity;
import com.sechuranavigator.app.data.local.entities.VisitEntity;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;
import com.sechuranavigator.app.managers.GnssListener;
import com.sechuranavigator.app.managers.GnssManager;
import com.sechuranavigator.app.managers.SettingsManager;
import com.sechuranavigator.app.managers.TrackManager;
import com.sechuranavigator.app.managers.VisitManager;
import com.sechuranavigator.app.managers.WaypointManager;
import com.sechuranavigator.app.models.GnssData;
import com.sechuranavigator.app.utils.KmlToGeoJson;

import java.io.InputStream;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import android.content.Intent;
import com.sechuranavigator.app.TrackingService;

public class MapActivity extends AppCompatActivity implements GnssListener {

    private MapView         mapView;
    private TextView        tvSpeed, tvCoords, tvAccuracy;
    private TextView        btnBoya, btnZoomIn, btnZoomOut;
    private TextView        toggleConcesiones;
    private TextView toggleBoyas;
    private LinearLayout    miniMenu;
    private TextView        miniMenuTitle, miniMenuNavigate, miniMenuDelete;

    private GnssManager     gnssManager;
    private WaypointManager waypointManager;
    private GnssData        lastGnssData;
    private boolean         centeredOnUser    = false;
    private boolean         concesionesVisible = true;
    private long            selectedWaypointId = -1L;
    private boolean boyasVisible = true;

    private List<Marker>         waypointMarkers = new ArrayList<>();
    private WaypointEntity       selectedWaypoint;

    private static final double SECHURA_LAT  = -5.57;
    private static final double SECHURA_LON  = -80.81;
    private static final double INITIAL_ZOOM = 12.0;

    private EditText etSearch;
    private android.widget.ListView listSearchResults;
    private android.widget.ArrayAdapter<String> searchAdapter;
    private List<WaypointEntity> allWaypoints = new ArrayList<>();
    private List<WaypointEntity> filteredWaypoints = new ArrayList<>();

    private TrackManager trackManager;
    private org.osmdroid.views.overlay.Polyline trackPolyline;
    private boolean isRecording = false;

    private VisitManager visitManager;

    private org.osmdroid.views.overlay.Polygon visitRadiusOverlay;

    private MapScaleView mapScale;

    // Para la polyline en vivo durante la grabación
    private org.osmdroid.views.overlay.Polyline liveTrackPolyline;
    private final java.util.List<org.osmdroid.util.GeoPoint> liveTrackPoints
            = new java.util.ArrayList<>();

    // BroadcastReceiver para recibir puntos del TrackingService
    private androidx.localbroadcastmanager.content.LocalBroadcastManager lbm;
    private android.content.BroadcastReceiver trackPointReceiver;

    // Lista de overlays de paradas detectadas
    private final java.util.List<org.osmdroid.views.overlay.Polygon>
            stopMarkers = new java.util.ArrayList<>();

    // Campo de la clase:
    private android.content.BroadcastReceiver stopDetectedReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // OsmDroid requiere configurar el user agent
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map);

        bindViews();
        setupSearch();
        setupOsmMap();
        loadConcesionesOverlay();
        setupButtons();

        // Registrar receiver para polyline en vivo
        lbm = androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(this);

        trackPointReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context,
                                  android.content.Intent intent) {
                double lat = intent.getDoubleExtra(
                        TrackingService.EXTRA_TRACK_LAT, 0);
                double lon = intent.getDoubleExtra(
                        TrackingService.EXTRA_TRACK_LON, 0);
                if (lat != 0 || lon != 0) {
                    addLiveTrackPoint(lat, lon);
                }
            }
        };

        android.content.BroadcastReceiver stopDetectedReceiver =
                new android.content.BroadcastReceiver() {
                    @Override
                    public void onReceive(android.content.Context context,
                                          android.content.Intent intent) {
                        double lat = intent.getDoubleExtra(
                                TrackingService.EXTRA_STOP_LAT, 0);
                        double lon = intent.getDoubleExtra(
                                TrackingService.EXTRA_STOP_LON, 0);
                        if (lat != 0 || lon != 0) {
                            drawStopMarker(lat, lon);
                        }
                    }
                };
        // Registrar en onResume():
        lbm.registerReceiver(
                stopDetectedReceiver,
                new android.content.IntentFilter(TrackingService.ACTION_STOP_DETECTED));

        gnssManager     = new GnssManager(this);
        waypointManager = new WaypointManager(this);
        gnssManager.setListener(this);

        trackManager = new TrackManager(this);
        trackManager.setListener(new TrackManager.TrackListener() {

            @Override
            public void onTrackStarted(TrackEntity track) {
                isRecording = true;
                updateRecordButton();
                Toast.makeText(MapActivity.this,
                        "⏺ Grabando ruta...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTrackPaused() {
                isRecording = false;
                updateRecordButton();
                Toast.makeText(MapActivity.this,
                        "⏸ Grabación pausada", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTrackResumed() {
                isRecording = true;
                updateRecordButton();
            }

            @Override
            public void onTrackStopped(TrackEntity track) {
                isRecording = false;
                updateRecordButton();
                // Limpiar línea del mapa
                if (trackPolyline != null) {
                    mapView.getOverlays().remove(trackPolyline);
                    trackPolyline = null;
                    mapView.invalidate();
                }
                Toast.makeText(MapActivity.this,
                        "✓ Ruta guardada: " + formatDistance(track.distanceMeters),
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onTrackPointAdded(List<GnssData> allPoints) {
                drawTrackLine(allPoints);
            }
        });

        visitManager = new VisitManager(this);
        visitManager.setListener(new VisitManager.VisitListener() {

            @Override
            public void onArrival(WaypointEntity waypoint, VisitEntity visit) {
                showVisitRadius(waypoint);
                Toast.makeText(MapActivity.this,
                        "📍 Llegada: " + waypoint.name,
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onDeparture(WaypointEntity waypoint, VisitEntity visit) {
                removeVisitRadius();
                showProductionDialog(waypoint, visit);
            }
        });

        observeWaypoints();
    }

    @Override protected void onResume()  {

        // ── NUEVO: restaurar estado del botón de grabación ─────────────────
        boolean serviceRunning = isTrackingServiceRunning();
        if (serviceRunning != isRecording) {
            isRecording = serviceRunning;
            updateRecordButton();
        }

        super.onResume();
        mapView.onResume();
        gnssManager.start();
        applyBrightnessSettings();

        // Registrar receivers
        lbm.registerReceiver(trackPointReceiver,
                new android.content.IntentFilter(TrackingService.ACTION_TRACK_POINT));
        lbm.registerReceiver(stopDetectedReceiver,
                new android.content.IntentFilter(TrackingService.ACTION_STOP_DETECTED));
    }

    @Override protected void onPause()   { super.onPause();
        mapView.onPause();
        gnssManager.stop();

        lbm.unregisterReceiver(trackPointReceiver);
        lbm.unregisterReceiver(stopDetectedReceiver);
    }

    // ── Configurar OsmDroid ────────────────────────────────────────────────
    private Marker boatMarker;
    private void setupOsmMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(INITIAL_ZOOM);
        mapView.getController().setCenter(new GeoPoint(SECHURA_LAT, SECHURA_LON));
        mapView.setBuiltInZoomControls(false); // para desactivar controles zoom de OSM

        // NO usar MyLocationNewOverlay — tiene la figura de persona
        // La posición del bote la manejamos con un Marker propio
        boatMarker = new Marker(mapView);
        boatMarker.setPosition(new GeoPoint(SECHURA_LAT, SECHURA_LON));
        boatMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        boatMarker.setIcon(createBoatArrowDrawable());
        boatMarker.setInfoWindow(null); // sin popup al tocar
        mapView.getOverlays().add(boatMarker);

        // Listener de zoom para actualizar la escala
        mapView.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                updateScale();
                return false;
            }
            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) {
                updateScale();
                return false;
            }
        });

// Actualizar escala inicial
        mapView.post(this::updateScale);
    }

    //metodo para dibujar la flecha ambar
    private android.graphics.drawable.Drawable createBoatArrowDrawable() {
        int size = 60;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.parseColor("#F59E0B"));
        fill.setStyle(Paint.Style.FILL);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
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

        return new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
    }

    //cargar concesiones
    private void loadConcesionesOverlay() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream is = getAssets().open("concesiones.kml");
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(is, "UTF-8");

                List<org.osmdroid.views.overlay.Polygon> polygons = new ArrayList<>();
                List<GeoPoint> currentPoints = new ArrayList<>();
                boolean inCoordinates = false;
                boolean inPolygon = false;

                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    String tag = parser.getName();

                    if (eventType == XmlPullParser.START_TAG) {
                        if ("Polygon".equalsIgnoreCase(tag)) inPolygon = true;
                        if ("coordinates".equalsIgnoreCase(tag)) inCoordinates = true;

                    } else if (eventType == XmlPullParser.TEXT && inCoordinates) {
                        String raw = parser.getText().trim();
                        String[] pairs = raw.split("\\s+");
                        currentPoints.clear();
                        for (String pair : pairs) {
                            String[] parts = pair.split(",");
                            if (parts.length >= 2) {
                                try {
                                    double lon = Double.parseDouble(parts[0].trim());
                                    double lat = Double.parseDouble(parts[1].trim());
                                    currentPoints.add(new GeoPoint(lat, lon));
                                } catch (NumberFormatException ignored) {}
                            }
                        }

                    } else if (eventType == XmlPullParser.END_TAG) {
                        if ("coordinates".equalsIgnoreCase(tag)) inCoordinates = false;

                        if ("Polygon".equalsIgnoreCase(tag) && !currentPoints.isEmpty()) {
                            org.osmdroid.views.overlay.Polygon polygon =
                                    new org.osmdroid.views.overlay.Polygon();
                            polygon.setPoints(new ArrayList<>(currentPoints));
                            polygon.getFillPaint().setColor(
                                    Color.argb(50, 16, 185, 129));  // verde 20% opacidad
                            polygon.getOutlinePaint().setColor(
                                    Color.parseColor("#10B981"));
                            polygon.getOutlinePaint().setStrokeWidth(3f);
                            polygon.setInfoWindow(null);
                            polygons.add(polygon);
                            inPolygon = false;
                            currentPoints.clear();
                        }
                    }
                    eventType = parser.next();
                }
                is.close();

                runOnUiThread(() -> {
                    for (org.osmdroid.views.overlay.Polygon p : polygons) {
                        mapView.getOverlays().add(0, p); // agregar debajo de los marcadores
                    }
                    concesionPolygons.addAll(polygons);
                    mapView.invalidate();
                    android.widget.Toast.makeText(MapActivity.this,
                            "Concesiones: " + polygons.size() + " áreas cargadas",
                            android.widget.Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        android.widget.Toast.makeText(MapActivity.this,
                                "Error al cargar concesiones: " + e.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show()
                );
            }
        });
    }


    // ── Waypoints ──────────────────────────────────────────────────────────

    private void observeWaypoints() {
        waypointManager.getAllLive().observe(this, waypoints -> {
            if (waypoints == null) return;
            allWaypoints = waypoints; // ← guardar para búsqueda
            refreshWaypointMarkers(waypoints);
            visitManager.updateWaypoints(waypoints);
        });
    }

    private void refreshWaypointMarkers(List<WaypointEntity> waypoints) {
        // Eliminar marcadores anteriores
        for (Marker m : waypointMarkers) {
            mapView.getOverlays().remove(m);
        }
        waypointMarkers.clear();

        // Agregar marcadores nuevos
        for (WaypointEntity wp : waypoints) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(wp.latitude, wp.longitude));
            marker.setTitle(wp.name != null ? wp.name : "Sin nombre");
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);

            // Color según especie
            String color = com.sechuranavigator.app.managers.WaypointManager
                    .getColorForSpecies(wp.species);
            marker.setIcon(createCircleDrawable(Color.parseColor(color)));

            final WaypointEntity wpRef = wp;
            marker.setOnMarkerClickListener((m, mv) -> {
                showMiniMenu(wpRef);
                return true;
            });

            mapView.getOverlays().add(marker);
            waypointMarkers.add(marker);
        }
        mapView.invalidate();
    }

    // ── Ícono circular para marcadores ────────────────────────────────────

    private android.graphics.drawable.Drawable createCircleDrawable(int color) {
        int size = 48;
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(4f);

        float cx = size / 2f, cy = size / 2f, r = size / 2f - 4f;
        canvas.drawCircle(cx, cy, r, fill);
        canvas.drawCircle(cx, cy, r, border);

        return new android.graphics.drawable.BitmapDrawable(
                getResources(), bmp);
    }

    // ── Mini-menú ──────────────────────────────────────────────────────────

    private void showMiniMenu(WaypointEntity wp) {
        selectedWaypointId = wp.id;
        selectedWaypoint   = wp;
        miniMenuTitle.setText(wp.name != null ? wp.name : "Sin nombre");
        miniMenu.setVisibility(View.VISIBLE);
    }

    private void hideMiniMenu() {
        miniMenu.setVisibility(View.GONE);
        selectedWaypointId = -1L;
    }

    // ── GPS ────────────────────────────────────────────────────────────────

    @Override
    public void onGnssUpdate(GnssData data) {
        lastGnssData = data;

        SettingsManager settings = new SettingsManager(this);
        tvSpeed.setText(settings.formatSpeed(data.speed));
        tvCoords.setText(settings.formatCoordinate(
                data.latitude, data.longitude));
        tvAccuracy.setText(String.format(Locale.US, "%.0fm", data.accuracy));

        if (data.accuracy <= 5f)
            tvAccuracy.setTextColor(Color.parseColor("#10B981"));
        else if (data.accuracy <= 8f)
            tvAccuracy.setTextColor(Color.parseColor("#F59E0B"));
        else
            tvAccuracy.setTextColor(Color.parseColor("#EF4444"));

        // Actualizar posición y rotación del bote
        if (boatMarker != null) {
            boatMarker.setPosition(new GeoPoint(data.latitude, data.longitude));
            boatMarker.setRotation(-data.bearing); // negativo porque OsmDroid rota al revés
            mapView.invalidate();
        }

        if (!centeredOnUser) {
            centeredOnUser = true;
            mapView.getController().animateTo(
                    new GeoPoint(data.latitude, data.longitude));
        }

        // Detector de visitas
        if (visitManager != null) {
            visitManager.onNewGnssData(data);
        }
    }

    @Override
    public void onGnssStatusChange(GnssListener.GnssStatus status) {
        if (status == GnssListener.GnssStatus.SEARCHING) {
            tvCoords.setText("Buscando GPS...");
        }
    }

    // ── Boya ───────────────────────────────────────────────────────────────

    private void createBoya() {
        if (lastGnssData == null || !lastGnssData.hasValidFix) {
            Toast.makeText(this, "Esperando señal GPS...",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        waypointManager.saveFromCurrentLocation(
                lastGnssData, "Boya", null, null,
                newId -> runOnUiThread(() -> {
                    if (newId > 0)
                        Toast.makeText(this, "🔵 Boya creada",
                                Toast.LENGTH_SHORT).show();
                })
        );
    }

    //
    private void setupSearch() {
        etSearch = findViewById(R.id.etSearch);
        listSearchResults = findViewById(R.id.listSearchResults);

        searchAdapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        );
        listSearchResults.setAdapter(searchAdapter);

        // Estilo del texto de la lista
        searchAdapter.setDropDownViewResource(android.R.layout.simple_list_item_1);

        // Filtrar conforme el usuario escribe
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(android.text.Editable s) {}

            @Override
            public void onTextChanged(CharSequence query, int st, int b, int c) {
                filterWaypoints(query.toString().trim());
            }
        });

        // Al tocar un resultado — centrar mapa en ese waypoint
        listSearchResults.setOnItemClickListener((parent, view, position, id) -> {
            if (position < filteredWaypoints.size()) {
                WaypointEntity wp = filteredWaypoints.get(position);
                mapView.getController().animateTo(
                        new GeoPoint(wp.latitude, wp.longitude));
                mapView.getController().setZoom(15.0);
                etSearch.setText("");
                listSearchResults.setVisibility(View.GONE);
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
            }
        });

        // Ocultar lista si el usuario borra todo el texto
        etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                listSearchResults.setVisibility(View.GONE);
            }
        });
    }

    private void filterWaypoints(String query) {
        filteredWaypoints.clear();
        searchAdapter.clear();

        if (query.isEmpty()) {
            listSearchResults.setVisibility(View.GONE);
            return;
        }

        String queryLower = query.toLowerCase();
        for (WaypointEntity wp : allWaypoints) {
            boolean nameMatch = wp.name != null &&
                    wp.name.toLowerCase().contains(queryLower);
            boolean speciesMatch = wp.species != null &&
                    wp.species.toLowerCase().contains(queryLower);

            if (nameMatch || speciesMatch) {
                filteredWaypoints.add(wp);
                // Mostrar nombre + especie + distancia si hay GPS
                String label = (wp.name != null ? wp.name : "Sin nombre");
                if (wp.species != null) label += " · " + wp.species;
                if (lastGnssData != null && lastGnssData.hasValidFix) {
                    double dist = distanceMeters(
                            lastGnssData.latitude, lastGnssData.longitude,
                            wp.latitude, wp.longitude);
                    if (dist < 1000) {
                        label += String.format(Locale.US, " · %.0fm", dist);
                    } else {
                        label += String.format(Locale.US, " · %.1fkm", dist/1000);
                    }
                }
                searchAdapter.add(label);
            }
        }

        searchAdapter.notifyDataSetChanged();
        listSearchResults.setVisibility(
                filteredWaypoints.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private double distanceMeters(double lat1, double lon1,
                                  double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    // ── Botones ────────────────────────────────────────────────────────────

    private void bindViews() {
        tvSpeed    = findViewById(R.id.tvSpeed);
        tvCoords   = findViewById(R.id.tvCoords);
        tvAccuracy = findViewById(R.id.tvAccuracy);
        btnBoya    = findViewById(R.id.btnBoya);
        btnZoomIn  = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        toggleConcesiones = findViewById(R.id.toggleConcesiones);
        toggleBoyas = findViewById(R.id.toggleBoyas);
        miniMenu          = findViewById(R.id.miniMenu);
        miniMenuTitle     = findViewById(R.id.miniMenuTitle);
        miniMenuNavigate  = findViewById(R.id.miniMenuNavigate);
        miniMenuDelete    = findViewById(R.id.miniMenuDelete);
        mapView           = findViewById(R.id.mapView);
        etSearch = findViewById(R.id.etSearch);
        listSearchResults = findViewById(R.id.listSearchResults);
        mapScale = findViewById(R.id.mapScale);
    }

    private List<org.osmdroid.views.overlay.Polygon> concesionPolygons = new ArrayList<>();
    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnBoya.setOnClickListener(v -> createBoya());

        btnZoomIn.setOnClickListener(v ->
                mapView.getController().zoomIn());
        btnZoomOut.setOnClickListener(v ->
                mapView.getController().zoomOut());

        toggleConcesiones.setOnClickListener(v -> {
            concesionesVisible = !concesionesVisible;
            for (org.osmdroid.views.overlay.Polygon p : concesionPolygons) {
                p.getFillPaint().setAlpha(concesionesVisible ? 50 : 0);
                p.getOutlinePaint().setAlpha(concesionesVisible ? 255 : 0);
            }
            mapView.invalidate();
            toggleConcesiones.setTextColor(concesionesVisible
                    ? Color.parseColor("#6EE7B7")
                    : Color.parseColor("#6B7280"));
        });

        toggleBoyas.setOnClickListener(v -> {
            boyasVisible = !boyasVisible;
            toggleBoyas.setTextColor(boyasVisible
                    ? Color.parseColor("#93C5FD")
                    : Color.parseColor("#6B7280"));
            toggleBoyas.setBackgroundColor(boyasVisible
                    ? Color.parseColor("#1A60A5FA")
                    : Color.parseColor("#0D1F3C"));

            for (Marker m : waypointMarkers) {
                if (m.getTitle() != null &&
                        m.getTitle().toLowerCase().contains("boya")) {
                    m.setVisible(boyasVisible);
                }
            }
            mapView.invalidate();
        });

        miniMenuNavigate.setOnClickListener(v -> {
            if (selectedWaypointId > 0) {
                Intent intent = new Intent(this, NavigationActivity.class);
                intent.putExtra(NavigationActivity.EXTRA_WAYPOINT_ID,
                        selectedWaypointId);
                startActivity(intent);
            }
            hideMiniMenu();
        });

        miniMenuDelete.setOnClickListener(v -> {
            if (selectedWaypointId > 0) {
                waypointManager.delete(selectedWaypointId);
                hideMiniMenu();
                Toast.makeText(this, "Waypoint eliminado",
                        Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.miniMenuEdit).setOnClickListener(v -> {
            if (selectedWaypointId > 0) {
                Intent intent = new Intent(this, WaypointDetailActivity.class);
                intent.putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_ID,
                        selectedWaypointId);
                startActivity(intent);
            }
            hideMiniMenu();
        });

        //botones de rutas
        TextView btnRecord = findViewById(R.id.btnRecord);
        btnRecord.setOnClickListener(v -> {
            if (!isRecording) {
                // Iniciar servicio de grabación en segundo plano
                Intent startIntent = new Intent(this, TrackingService.class);
                startIntent.setAction(TrackingService.ACTION_START);
                startForegroundService(startIntent);
                isRecording = true;
                updateRecordButton();
                Toast.makeText(this, "⏺ Grabando ruta...",
                        Toast.LENGTH_SHORT).show();
            } else {
                // Detener servicio
                new AlertDialog.Builder(this)
                        .setTitle("Grabando ruta")
                        .setMessage("¿Qué deseas hacer?")
                        .setPositiveButton("Finalizar", (d, w) -> stopTracking())
                        .setNegativeButton("Continuar", null)
                        .show();
            }
        });
    }

    //metodos auxiliares
    private void updateRecordButton() {
        TextView btnRecord = findViewById(R.id.btnRecord);
        if (isRecording) {
            btnRecord.setText("⏹");
            btnRecord.setBackgroundColor(Color.parseColor("#6B7280"));
        } else if (trackManager.isPaused()) {
            btnRecord.setText("▶");
            btnRecord.setBackgroundColor(Color.parseColor("#10B981"));
        } else {
            btnRecord.setText("⏺");
            btnRecord.setBackgroundColor(Color.parseColor("#EF4444"));
        }
    }

    private void drawTrackLine(List<GnssData> points) {
        if (points.isEmpty()) return;

        List<GeoPoint> geoPoints = new ArrayList<>();
        for (GnssData p : points) {
            geoPoints.add(new GeoPoint(p.latitude, p.longitude));
        }

        if (trackPolyline == null) {
            trackPolyline = new org.osmdroid.views.overlay.Polyline();
            trackPolyline.getOutlinePaint().setColor(Color.parseColor("#F59E0B"));
            trackPolyline.getOutlinePaint().setStrokeWidth(6f);
            trackPolyline.getOutlinePaint().setStrokeCap(
                    android.graphics.Paint.Cap.ROUND);
            mapView.getOverlays().add(trackPolyline);
        }

        trackPolyline.setPoints(geoPoints);
        mapView.invalidate();
    }

    private String formatDistance(double meters) {
        if (meters < 1000) {
            return String.format(Locale.US, "%.0f m", meters);
        } else {
            return String.format(Locale.US, "%.1f km", meters / 1000);
        }
    }

    private void showProductionDialog(WaypointEntity waypoint,
                                      VisitEntity visit) {
        // Verificar configuración
        SettingsManager settings = new SettingsManager(this);
        if (!settings.askProduction()) return;

        android.view.View dialogView = android.view.LayoutInflater
                .from(this).inflate(R.layout.dialog_production, null);

        // ── Rellenar cabecera ──────────────────────────────────────────────
        ((android.widget.TextView) dialogView
                .findViewById(R.id.tvWaypointName))
                .setText(waypoint.name != null ? waypoint.name : "Sin nombre");

        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);

        ((android.widget.TextView) dialogView.findViewById(R.id.tvArrivalTime))
                .setText(sdf.format(new java.util.Date(visit.arrivedAt)));
        ((android.widget.TextView) dialogView.findViewById(R.id.tvDepartureTime))
                .setText(sdf.format(new java.util.Date(visit.departedAt)));

        long mins = visit.durationMs / 60000;
        String dur = mins >= 60
                ? String.format(java.util.Locale.US, "%dh %02dm", mins/60, mins%60)
                : mins + " min";
        ((android.widget.TextView) dialogView.findViewById(R.id.tvDuration))
                .setText(dur);

        // ── Vistas de entrada ──────────────────────────────────────────────
        android.widget.EditText etQuantity =
                dialogView.findViewById(R.id.etCatchQuantity);
        android.widget.EditText etPersons =
                dialogView.findViewById(R.id.etNumPersons);
        android.widget.EditText etNotes =
                dialogView.findViewById(R.id.etCatchNotes);

        // ── Crear diálogo ──────────────────────────────────────────────────
        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();

        // Botón Omitir
        dialogView.findViewById(R.id.btnSkip)
                .setOnClickListener(v -> dialog.dismiss());

        // Botón Guardar
        dialogView.findViewById(R.id.btnSaveProduction)
                .setOnClickListener(v -> {
                    String quantity = etQuantity.getText().toString().trim();
                    String notes    = etNotes.getText().toString().trim();

                    // Número de personas
                    int persons = 1;
                    try {
                        String p = etPersons.getText().toString().trim();
                        if (!p.isEmpty()) persons = Integer.parseInt(p);
                        if (persons < 1) persons = 1;
                    } catch (NumberFormatException ignored) {}

                    // Recoger tags seleccionados
                    com.google.android.material.chip.ChipGroup chipGroup =
                            dialogView.findViewById(R.id.chipGroupConditions);
                    StringBuilder tagsBuilder = new StringBuilder();
                    for (int i = 0; i < chipGroup.getChildCount(); i++) {
                        com.google.android.material.chip.Chip chip =
                                (com.google.android.material.chip.Chip)
                                        chipGroup.getChildAt(i);
                        if (chip != null && chip.isChecked()) {
                            if (tagsBuilder.length() > 0) tagsBuilder.append(",");
                            tagsBuilder.append(chip.getText().toString().trim());
                        }
                    }
                    String conditionTags = tagsBuilder.length() > 0
                            ? tagsBuilder.toString()
                            : "normal"; // default si no seleccionó nada

                    // Guardar via VisitManager
                    final int finalPersons = persons;
                    visitManager.recordProductionFull(
                            quantity, notes, finalPersons, conditionTags);

                    String msg = quantity.isEmpty()
                            ? "✓ Visita guardada"
                            : "✓ Guardado: " + quantity;
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });

        dialog.show();
    }

    private void showVisitRadius(WaypointEntity waypoint) {
        removeVisitRadius();

        // Crear un círculo de 25m alrededor del waypoint
        List<GeoPoint> circlePoints = new ArrayList<>();
        double lat = waypoint.latitude;
        double lon = waypoint.longitude;
        double radiusDeg = 25.0 / 111320.0; // 25 metros en grados aprox.

        for (int i = 0; i <= 36; i++) {
            double angle = Math.toRadians(i * 10);
            circlePoints.add(new GeoPoint(
                    lat + radiusDeg * Math.cos(angle),
                    lon + radiusDeg * Math.sin(angle) /
                            Math.cos(Math.toRadians(lat))
            ));
        }

        visitRadiusOverlay = new org.osmdroid.views.overlay.Polygon();
        visitRadiusOverlay.setPoints(circlePoints);
        visitRadiusOverlay.getFillPaint().setColor(
                android.graphics.Color.argb(40, 245, 158, 11));
        visitRadiusOverlay.getOutlinePaint().setColor(
                android.graphics.Color.parseColor("#F59E0B"));
        visitRadiusOverlay.getOutlinePaint().setStrokeWidth(3f);
        visitRadiusOverlay.setInfoWindow(null);

        mapView.getOverlays().add(visitRadiusOverlay);
        mapView.invalidate();
    }

    private void removeVisitRadius() {
        if (visitRadiusOverlay != null) {
            mapView.getOverlays().remove(visitRadiusOverlay);
            visitRadiusOverlay = null;
            mapView.invalidate();
        }
    }

    private void applyBrightnessSettings() {
        SettingsManager settings = new SettingsManager(this);
        android.view.WindowManager.LayoutParams lp =
                getWindow().getAttributes();

        if (settings.maxBrightness()) {
            lp.screenBrightness =
                    android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        } else {
            lp.screenBrightness =
                    android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }
        getWindow().setAttributes(lp);
    }

    private void stopTracking() {
        Intent stopIntent = new Intent(this, TrackingService.class);
        stopIntent.setAction(TrackingService.ACTION_STOP);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(stopIntent);
        } else {
            startService(stopIntent);
        }

        isRecording = false;
        updateRecordButton();

        // Limpiar polyline en vivo
        liveTrackPoints.clear();
        if (liveTrackPolyline != null) {
            mapView.getOverlays().remove(liveTrackPolyline);
            liveTrackPolyline = null;
            mapView.invalidate();
        }

        // Limpiar marcadores de parada
        for (org.osmdroid.views.overlay.Polygon p : stopMarkers) {
            mapView.getOverlays().remove(p);
        }
        stopMarkers.clear();

        Toast.makeText(this, "✓ Ruta guardada", Toast.LENGTH_SHORT).show();
    }

    private void updateScale() {
        if (mapScale == null) return;
        double zoom = mapView.getZoomLevelDouble();
        org.osmdroid.api.IGeoPoint center = mapView.getMapCenter();
        mapScale.updateScale(zoom, center.getLatitude());
    }

    private void addLiveTrackPoint(double lat, double lon) {
        org.osmdroid.util.GeoPoint point =
                new org.osmdroid.util.GeoPoint(lat, lon);
        liveTrackPoints.add(point);

        if (liveTrackPolyline == null) {
            liveTrackPolyline = new org.osmdroid.views.overlay.Polyline();
            liveTrackPolyline.getOutlinePaint().setColor(
                    android.graphics.Color.parseColor("#F59E0B")); // ámbar
            liveTrackPolyline.getOutlinePaint().setStrokeWidth(5f);
            liveTrackPolyline.getOutlinePaint().setStrokeCap(
                    android.graphics.Paint.Cap.ROUND);
            mapView.getOverlays().add(liveTrackPolyline);
        }

        liveTrackPolyline.setPoints(new java.util.ArrayList<>(liveTrackPoints));
        mapView.invalidate();
    }

    private void drawStopMarker(double lat, double lon) {
        // Radio visual en grados (aprox. 40 metros)
        double radiusDeg = 40.0 / 111320.0;

        java.util.List<org.osmdroid.util.GeoPoint> circlePoints =
                new java.util.ArrayList<>();
        for (int i = 0; i <= 36; i++) {
            double angle = Math.toRadians(i * 10);
            circlePoints.add(new org.osmdroid.util.GeoPoint(
                    lat + radiusDeg * Math.cos(angle),
                    lon + radiusDeg * Math.sin(angle)
                            / Math.cos(Math.toRadians(lat))
            ));
        }

        org.osmdroid.views.overlay.Polygon stopCircle =
                new org.osmdroid.views.overlay.Polygon();
        stopCircle.setPoints(circlePoints);

        // Círculo verde oscuro semitransparente
        stopCircle.getFillPaint().setColor(
                android.graphics.Color.argb(100, 16, 185, 129)); // verde
        stopCircle.getOutlinePaint().setColor(
                android.graphics.Color.parseColor("#10B981")); // borde verde
        stopCircle.getOutlinePaint().setStrokeWidth(3f);
        stopCircle.setInfoWindow(null);

        mapView.getOverlays().add(stopCircle);
        stopMarkers.add(stopCircle);
        mapView.invalidate();
    }


    //Verifica si TrackingService está corriendo actualmente.
    //Consulta el ActivityManager del sistema.
    private boolean isTrackingServiceRunning() {
        android.app.ActivityManager am =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am == null) return false;
        for (android.app.ActivityManager.RunningServiceInfo svc :
                am.getRunningServices(Integer.MAX_VALUE)) {
            if (TrackingService.class.getName()
                    .equals(svc.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}