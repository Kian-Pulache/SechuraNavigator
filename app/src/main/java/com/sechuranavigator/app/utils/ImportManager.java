// archivo: app/src/main/java/com/sechuranavigator/app/utils/ImportManager.java
package com.sechuranavigator.app.utils;

import android.content.Context;
import android.net.Uri;

import com.sechuranavigator.app.data.WaypointRepository;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;
import com.sechuranavigator.app.managers.WaypointManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ImportManager {

    private final Context           context;
    private final WaypointRepository repository;
    private final com.sechuranavigator.app.managers.SyncManager syncManager;

    public ImportManager(Context context) {
        this.context    = context;
        this.repository = new WaypointRepository(context);
        this.syncManager = new
                com.sechuranavigator.app.managers.SyncManager(context); // ← nuevo
    }

    // ── API pública ────────────────────────────────────────────────────────

    public void importFromUri(Uri uri, OnImportCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String filename = getFileName(uri);
                List<WaypointEntity> imported;

                if (filename != null && filename.toLowerCase().endsWith(".gpx")) {
                    imported = parseGpx(uri);
                } else {
                    imported = parseKml(uri);
                }

                // Guardar todos los waypoints importados
                int[] saved = {0};
                for (WaypointEntity wp : imported) {
                    repository.insert(wp, newId -> {
                        if (newId > 0) {
                            saved[0]++;
                            wp.id = newId;
                            syncManager.uploadWaypoint(wp); // ← agregar esta línea
                        }
                    });
                    Thread.sleep(50);
                }

                final int count = imported.size();
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onImportComplete(count, null));
                }

            } catch (Exception e) {
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> callback.onImportComplete(0, e.getMessage()));
                }
            }
        });
    }

    // ── Parser KML ────────────────────────────────────────────────────────

    private List<WaypointEntity> parseKml(Uri uri) throws Exception {
        List<WaypointEntity> result = new ArrayList<>();
        InputStream is = context.getContentResolver().openInputStream(uri);
        if (is == null) return result;

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        WaypointEntity current = null;
        boolean inPlacemark = false;
        boolean inPoint     = false;
        boolean inName      = false;
        boolean inDesc      = false;
        boolean inCoords    = false;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if ("Placemark".equalsIgnoreCase(tag)) {
                    current = new WaypointEntity();
                    inPlacemark = true;
                }
                if (inPlacemark) {
                    if ("Point".equalsIgnoreCase(tag))       inPoint  = true;
                    if ("name".equalsIgnoreCase(tag))        inName   = true;
                    if ("description".equalsIgnoreCase(tag)) inDesc   = true;
                    if ("coordinates".equalsIgnoreCase(tag)) inCoords = true;
                }

            } else if (eventType == XmlPullParser.TEXT && current != null) {
                String text = parser.getText().trim();
                if (inName && !text.isEmpty()) {
                    current.name = text;
                    inName = false;
                }
                if (inDesc && !text.isEmpty()) {
                    current.description = text;
                    inDesc = false;
                }
                if (inCoords && inPoint) {
                    // KML: "lon,lat,alt"
                    String[] parts = text.split(",");
                    if (parts.length >= 2) {
                        current.longitude = Double.parseDouble(parts[0].trim());
                        current.latitude  = Double.parseDouble(parts[1].trim());
                        if (parts.length >= 3)
                            current.altitude = Double.parseDouble(parts[2].trim());
                    }
                    inCoords = false;
                }

            } else if (eventType == XmlPullParser.END_TAG) {
                if ("Placemark".equalsIgnoreCase(tag) && current != null) {
                    // Solo importar si tiene coordenadas válidas
                    if (current.latitude != 0 || current.longitude != 0) {
                        current.speciesColor =
                                WaypointManager.getColorForSpecies(current.species);
                        if (current.name == null || current.name.isEmpty())
                            current.name = "Importado";
                        result.add(current);
                    }
                    current     = null;
                    inPlacemark = false;
                    inPoint     = false;
                }
                if ("Point".equalsIgnoreCase(tag)) inPoint = false;
            }
            eventType = parser.next();
        }
        is.close();
        return result;
    }

    // ── Parser GPX ────────────────────────────────────────────────────────

    private List<WaypointEntity> parseGpx(Uri uri) throws Exception {
        List<WaypointEntity> result = new ArrayList<>();
        InputStream is = context.getContentResolver().openInputStream(uri);
        if (is == null) return result;

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        WaypointEntity current = null;
        boolean inName = false;
        boolean inDesc = false;
        boolean inType = false;
        boolean inEle  = false;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();

            if (eventType == XmlPullParser.START_TAG) {
                if ("wpt".equalsIgnoreCase(tag)) {
                    current = new WaypointEntity();
                    try {
                        current.latitude  = Double.parseDouble(
                                parser.getAttributeValue(null, "lat"));
                        current.longitude = Double.parseDouble(
                                parser.getAttributeValue(null, "lon"));
                    } catch (Exception ignored) {}
                }
                if (current != null) {
                    if ("name".equalsIgnoreCase(tag)) inName = true;
                    if ("desc".equalsIgnoreCase(tag)) inDesc = true;
                    if ("type".equalsIgnoreCase(tag)) inType = true;
                    if ("ele".equalsIgnoreCase(tag))  inEle  = true;
                }

            } else if (eventType == XmlPullParser.TEXT && current != null) {
                String text = parser.getText().trim();
                if (inName && !text.isEmpty()) { current.name = text; inName = false; }
                if (inDesc && !text.isEmpty()) { current.description = text; inDesc = false; }
                if (inType && !text.isEmpty()) { current.species = text; inType = false; }
                if (inEle  && !text.isEmpty()) {
                    try { current.altitude = Double.parseDouble(text); }
                    catch (Exception ignored) {}
                    inEle = false;
                }

            } else if (eventType == XmlPullParser.END_TAG) {
                if ("wpt".equalsIgnoreCase(tag) && current != null) {
                    current.speciesColor =
                            WaypointManager.getColorForSpecies(current.species);
                    if (current.name == null || current.name.isEmpty())
                        current.name = "Importado";
                    result.add(current);
                    current = null;
                }
            }
            eventType = parser.next();
        }
        is.close();
        return result;
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private String getFileName(Uri uri) {
        String path = uri.getPath();
        if (path == null) return null;
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public interface OnImportCallback {
        void onImportComplete(int count, String errorMsg);
    }
}