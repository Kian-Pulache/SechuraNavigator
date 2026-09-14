// archivo: app/src/main/java/com/sechuranavigator/app/utils/ExportManager.java
package com.sechuranavigator.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import androidx.core.content.FileProvider;

import com.sechuranavigator.app.data.local.entities.TrackEntity;
import com.sechuranavigator.app.data.local.entities.TrackPointEntity;
import com.sechuranavigator.app.data.local.entities.WaypointEntity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ExportManager {

    private final Context context;

    public ExportManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // ── Exportar waypoints como KML ───────────────────────────────────────

    public File exportWaypointsKml(List<WaypointEntity> waypoints)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        sb.append("<Document>\n");
        sb.append("  <name>Sechura Navigator - Waypoints</name>\n");

        // Estilos por especie
        sb.append(kmlStyles());

        // Placemarks
        for (WaypointEntity wp : waypoints) {
            sb.append("  <Placemark>\n");
            sb.append("    <name>").append(escapeXml(wp.name)).append("</name>\n");
            if (wp.description != null && !wp.description.isEmpty()) {
                sb.append("    <description>")
                        .append(escapeXml(wp.description))
                        .append("</description>\n");
            }
            // StyleUrl según especie
            String styleId = sanitizeId(wp.species);
            sb.append("    <styleUrl>#").append(styleId).append("</styleUrl>\n");

            // ExtendedData con metadatos
            sb.append("    <ExtendedData>\n");
            sb.append("      <Data name=\"species\"><value>")
                    .append(escapeXml(wp.species)).append("</value></Data>\n");
            sb.append("      <Data name=\"accuracy\"><value>")
                    .append(wp.accuracy).append("</value></Data>\n");
            sb.append("      <Data name=\"satellites\"><value>")
                    .append(wp.satelliteCount).append("</value></Data>\n");
            sb.append("      <Data name=\"visit_count\"><value>")
                    .append(wp.visitCount).append("</value></Data>\n");
            sb.append("    </ExtendedData>\n");

            sb.append("    <Point>\n");
            sb.append("      <coordinates>")
                    .append(String.format(Locale.US, "%.8f,%.8f,%.1f",
                            wp.longitude, wp.latitude, wp.altitude))
                    .append("</coordinates>\n");
            sb.append("    </Point>\n");
            sb.append("  </Placemark>\n");
        }

        sb.append("</Document>\n</kml>");

        return writeToFile("waypoints_" + timestamp() + ".kml", sb.toString());
    }

    // ── Exportar waypoints como GPX ───────────────────────────────────────

    public File exportWaypointsGpx(List<WaypointEntity> waypoints)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"Sechura Navigator\"\n");
        sb.append("  xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        sb.append("  <metadata><name>Sechura Navigator Waypoints</name></metadata>\n");

        SimpleDateFormat sdf = isoDateFormat();
        for (WaypointEntity wp : waypoints) {
            sb.append(String.format(Locale.US,
                    "  <wpt lat=\"%.8f\" lon=\"%.8f\">\n", wp.latitude, wp.longitude));
            sb.append(String.format(Locale.US,
                    "    <ele>%.1f</ele>\n", wp.altitude));
            sb.append("    <time>")
                    .append(sdf.format(new Date(wp.createdAt)))
                    .append("</time>\n");
            sb.append("    <name>").append(escapeXml(wp.name)).append("</name>\n");
            if (wp.description != null && !wp.description.isEmpty()) {
                sb.append("    <desc>")
                        .append(escapeXml(wp.description))
                        .append("</desc>\n");
            }
            if (wp.species != null) {
                sb.append("    <type>").append(escapeXml(wp.species))
                        .append("</type>\n");
            }
            sb.append("  </wpt>\n");
        }

        sb.append("</gpx>");
        return writeToFile("waypoints_" + timestamp() + ".gpx", sb.toString());
    }

    // ── Exportar ruta (track) como KML ────────────────────────────────────

    public File exportTrackKml(TrackEntity track,
                               List<TrackPointEntity> points)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        sb.append("<Document>\n");
        sb.append("  <name>")
                .append(escapeXml(track.name))
                .append("</name>\n");

        // Estilo de línea
        sb.append("  <Style id=\"trackStyle\">\n");
        sb.append("    <LineStyle><color>ff0ba9f5</color><width>4</width></LineStyle>\n");
        sb.append("  </Style>\n");

        // Inicio y fin como Placemarks
        if (!points.isEmpty()) {
            TrackPointEntity first = points.get(0);
            TrackPointEntity last  = points.get(points.size() - 1);

            sb.append("  <Placemark><name>Inicio</name><Point><coordinates>")
                    .append(String.format(Locale.US, "%.8f,%.8f",
                            first.longitude, first.latitude))
                    .append("</coordinates></Point></Placemark>\n");

            sb.append("  <Placemark><name>Fin</name><Point><coordinates>")
                    .append(String.format(Locale.US, "%.8f,%.8f",
                            last.longitude, last.latitude))
                    .append("</coordinates></Point></Placemark>\n");
        }

        // LineString del recorrido
        sb.append("  <Placemark>\n");
        sb.append("    <name>").append(escapeXml(track.name)).append("</name>\n");
        sb.append("    <styleUrl>#trackStyle</styleUrl>\n");
        sb.append("    <LineString>\n");
        sb.append("      <tessellate>1</tessellate>\n");
        sb.append("      <coordinates>\n");

        for (TrackPointEntity p : points) {
            sb.append(String.format(Locale.US,
                    "        %.8f,%.8f,%.1f\n",
                    p.longitude, p.latitude, p.altitude));
        }

        sb.append("      </coordinates>\n");
        sb.append("    </LineString>\n");
        sb.append("  </Placemark>\n");
        sb.append("</Document>\n</kml>");

        String name = (track.name != null ? track.name : "ruta")
                .replace(" ", "_") + ".kml";
        return writeToFile(name, sb.toString());
    }

    // ── Compartir archivo por Intent ───────────────────────────────────────

    /**
     * Abre el selector de apps para compartir el archivo
     * (WhatsApp, Gmail, Drive, etc.)
     */
    public Intent createShareIntent(File file) {
        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );

        String mimeType = file.getName().endsWith(".kml")
                ? "application/vnd.google-earth.kml+xml"
                : "application/gpx+xml";

        return new Intent(Intent.ACTION_SEND)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Waypoints - Sechura Navigator")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    // ── Utilidades privadas ────────────────────────────────────────────────

    private File writeToFile(String filename, String content)
            throws IOException {
        File dir = new File(context.getExternalFilesDir(null), "exports");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, filename);
        FileWriter writer = new FileWriter(file);
        writer.write(content);
        writer.close();
        return file;
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String sanitizeId(String species) {
        if (species == null) return "default";
        return species.toLowerCase()
                .replace(" ", "-")
                .replace("á","a").replace("é","e")
                .replace("í","i").replace("ó","o").replace("ú","u");
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
                .format(new Date());
    }

    private SimpleDateFormat isoDateFormat() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    }

    private String kmlStyles() {
        String[][] species = {
                {"concha-de-abanico", "ff0ba9f5"},
                {"pulpo",             "fffa9e10"},
                {"langosta",          "fffa7ab4"},
                {"pescado",           "ff81e934"},
                {"caracol-rosado",    "ff3c86fb"},
                {"almejas",           "ffb472f4"},
                {"default",           "ff9ca3af"}
        };
        StringBuilder sb = new StringBuilder();
        for (String[] s : species) {
            sb.append("  <Style id=\"").append(s[0]).append("\">\n");
            sb.append("    <IconStyle><color>").append(s[1])
                    .append("</color></IconStyle>\n");
            sb.append("  </Style>\n");
        }
        return sb.toString();
    }
}