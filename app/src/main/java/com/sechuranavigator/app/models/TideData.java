// archivo: app/src/main/java/com/sechuranavigator/app/models/TideData.java
package com.sechuranavigator.app.models;

import java.util.List;

public class TideData {

    // ── Marea actual ───────────────────────────────────────────────────────
    public double  currentHeight;      // altura en metros ahora mismo
    public boolean isRising;           // true = subiendo, false = bajando
    public long    computedAt;         // timestamp del cálculo

    // ── Próximos extremos del día ──────────────────────────────────────────
    public TideExtreme nextHigh;       // próxima pleamar
    public TideExtreme nextLow;        // próxima bajamar

    // ── Coeficiente y luna ────────────────────────────────────────────────
    public int    coefficient;         // 20-120, deriva del rango mareal
    public String tideType;            // "Viva" o "Muerta"
    public String moonPhase;           // 🌑 🌒 🌓 🌔 🌕 🌖 🌗 🌘
    public String moonPhaseName;       // "Nueva", "Creciente", etc.

    // ── Curva del día (hora por hora) ─────────────────────────────────────
    public List<HourlyPoint> hourlyPoints;   // 24 puntos para la gráfica

    // ── Tabla de extremos próximos 3 días ─────────────────────────────────
    public List<TideExtreme> extremes3Days;

    // ── Clases internas ───────────────────────────────────────────────────

    public static class TideExtreme {
        public long   timestamp;   // cuando ocurre
        public double height;      // altura en metros
        public boolean isHigh;     // true = pleamar, false = bajamar

        public TideExtreme(long timestamp, double height, boolean isHigh) {
            this.timestamp = timestamp;
            this.height    = height;
            this.isHigh    = isHigh;
        }
    }

    public static class HourlyPoint {
        public long   timestamp;
        public double height;
        public boolean isNow;      // true = el punto más cercano a ahora

        public HourlyPoint(long timestamp, double height) {
            this.timestamp = timestamp;
            this.height    = height;
        }
    }
}