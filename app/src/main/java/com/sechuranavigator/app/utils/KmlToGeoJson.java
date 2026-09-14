// archivo: app/src/main/java/com/sechuranavigator/app/utils/KmlToGeoJson.java
package com.sechuranavigator.app.utils;

import android.content.Context;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;

/**
 * Conversor KML → GeoJSON mínimo, suficiente para polígonos de concesiones.
 * Solo procesa <Polygon> y <Point>. No procesa estilos KML.
 */
public class KmlToGeoJson {

    public static String convert(Context context, String assetFileName) {
        try {
            InputStream is = context.getAssets().open(assetFileName);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(is, "UTF-8");

            StringBuilder features = new StringBuilder();
            boolean inCoordinates = false;
            boolean inPolygon     = false;
            String  currentCoords = "";
            String  currentName   = "";
            boolean inName        = false;
            boolean firstFeature  = true;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();

                if (eventType == XmlPullParser.START_TAG) {
                    if ("Polygon".equalsIgnoreCase(tagName))     inPolygon = true;
                    if ("coordinates".equalsIgnoreCase(tagName)) inCoordinates = true;
                    if ("name".equalsIgnoreCase(tagName))        inName = true;

                } else if (eventType == XmlPullParser.TEXT) {
                    if (inCoordinates) currentCoords = parser.getText().trim();
                    if (inName)        currentName   = parser.getText().trim();

                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("name".equalsIgnoreCase(tagName)) inName = false;

                    if ("Polygon".equalsIgnoreCase(tagName) && !currentCoords.isEmpty()) {
                        if (!firstFeature) features.append(",");
                        features.append(buildPolygonFeature(currentCoords, currentName));
                        firstFeature  = false;
                        inPolygon     = false;
                        currentCoords = "";
                        currentName   = "";
                    }
                    if ("coordinates".equalsIgnoreCase(tagName)) inCoordinates = false;
                }
                eventType = parser.next();
            }
            is.close();

            return "{\"type\":\"FeatureCollection\",\"features\":["
                    + features.toString() + "]}";

        } catch (Exception e) {
            e.printStackTrace();
            // Devolver GeoJSON vacío en caso de error
            return "{\"type\":\"FeatureCollection\",\"features\":[]}";
        }
    }

    private static String buildPolygonFeature(String rawCoords, String name) {
        // KML: "lon,lat,alt lon,lat,alt ..."
        // GeoJSON: [[lon,lat],[lon,lat],...]
        String[] pairs = rawCoords.trim().split("\\s+");
        StringBuilder coords = new StringBuilder("[");
        for (int i = 0; i < pairs.length; i++) {
            String[] parts = pairs[i].split(",");
            if (parts.length >= 2) {
                if (i > 0) coords.append(",");
                coords.append("[").append(parts[0]).append(",").append(parts[1]).append("]");
            }
        }
        coords.append("]");

        return "{\"type\":\"Feature\","
                + "\"properties\":{\"name\":\"" + name.replace("\"","\\\"") + "\"},"
                + "\"geometry\":{\"type\":\"Polygon\",\"coordinates\":["
                + coords.toString() + "]}}";
    }
}