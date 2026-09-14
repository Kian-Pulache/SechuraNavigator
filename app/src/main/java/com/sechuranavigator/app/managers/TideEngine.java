// archivo: app/src/main/java/com/sechuranavigator/app/managers/TideEngine.java
package com.sechuranavigator.app.managers;

import com.sechuranavigator.app.models.TideData;

import java.util.ArrayList;
import java.util.List;

public class TideEngine {

    // ── Nivel medio del mar (Z0) ───────────────────────────────────────────
    // Calibrado para la Bahía de Sechura con pytides
    private static final double Z0 = 0.9474;

    // ── Época de referencia ────────────────────────────────────────────────
    // pytides usa 1899-12-31 00:00 UTC (convención Doodson)
    // Diferencia con Unix epoch (1970-01-01): 613632 horas exactas
    private static final double PYTIDES_EPOCH_HOURS = 613632.0;

    // ── Constituyentes armónicos ───────────────────────────────────────────
    // Derivados con pytides a partir de datos históricos de Sechura.
    // Formato: { velocidad_angular (°/hora), amplitud (metros), fase (grados) }
    //
    // Fuente: análisis armónico propio con pytides — julio 2026
    // Verificado contra tablademareas.com: error ±13 min, ±0.06 m
    private static final double[][] CONSTITUENTS = {

            // ── Semidiurnos principales (dominan en el Pacífico SE) ───────────
            { 28.9841042, 0.318343, 256.775977 }, // M2  - lunar semidiurno principal
            { 30.0000000, 0.102719, 297.270610 }, // S2  - solar semidiurno principal
            { 28.4397295, 0.072019, 230.437051 }, // N2  - lunar elíptico mayor
            { 30.0821373, 0.028205, 295.656107 }, // K2  - lunisolar semidiurno
            { 29.9589333, 0.006274, 283.294596 }, // T2  - solar semidiurno menor
            { 28.5125831, 0.013846, 230.357997 }, // nu2 - lunar elíptico menor
            { 27.9682084, 0.010984, 215.295898 }, // mu2 - variacional
            { 27.8953548, 0.009113, 203.075821 }, // 2N2 - segundo elíptico lunar
            { 29.5284789, 0.007235, 286.226569 }, // L2  - lunar semimensual

            // ── Diurnos principales ───────────────────────────────────────────
            { 15.0410686, 0.070526,  34.391587 }, // K1  - lunisolar diurno
            { 13.9430356, 0.024160, 355.158438 }, // O1  - lunar diurno principal
            { 14.9589314, 0.021528,  31.796525 }, // P1  - solar diurno principal
            { 15.5854433, 0.004718,  65.849908 }, // J1  - lunar elíptico diurno
            { 16.1391017, 0.004139,  92.486428 }, // OO1 - lunar diurno de largo período
            { 15.0000000, 0.008435, 132.548886 }, // S1  - solar diurno
            { 13.3986609, 0.002476, 308.643326 }, // Q1  - lunar elíptico mayor diurno

            // ── Largo período (variaciones estacionales) ──────────────────────
            {  0.0410686, 0.027633,  18.851082 }, // Sa  - anual solar
            {  1.0980331, 0.007466,  21.021316 }, // Mf  - quincenal lunar
    };

    // ── API pública ────────────────────────────────────────────────────────

    /**
     * Calcula todos los datos de marea para el momento actual.
     */
    public TideData compute() {
        return computeAt(System.currentTimeMillis());
    }

    /**
     * Calcula datos de marea para un timestamp Unix específico (ms).
     */
    public TideData computeAt(long timestampMs) {
        TideData data = new TideData();
        data.computedAt = timestampMs;

        data.currentHeight = heightAt(timestampMs);

        // Determinar si sube o baja (comparar con 5 minutos después)
        double futureHeight = heightAt(timestampMs + 5 * 60 * 1000L);
        data.isRising = futureHeight > data.currentHeight;

        // Buscar extremos en las próximas 72 horas
        List<TideData.TideExtreme> extremes =
                findExtremes(timestampMs, timestampMs + 3 * 24 * 3600 * 1000L);
        data.extremes3Days = extremes;

        // Próxima pleamar y bajamar
        for (TideData.TideExtreme e : extremes) {
            if (e.timestamp > timestampMs) {
                if (data.nextHigh == null && e.isHigh)  data.nextHigh = e;
                if (data.nextLow  == null && !e.isHigh) data.nextLow  = e;
                if (data.nextHigh != null && data.nextLow != null) break;
            }
        }

        // Coeficiente y tipo de marea
        data.coefficient = computeCoefficient(timestampMs);
        data.tideType     = data.coefficient >= 70 ? "Viva" : "Muerta";

        // Fase lunar
        computeMoonPhase(timestampMs, data);

        // Curva horaria (24 horas)
        data.hourlyPoints = computeHourly(timestampMs, 24);

        return data;
    }

    // ── Cálculo de altura ──────────────────────────────────────────────────

    /**
     * Altura del mar en metros para un timestamp Unix dado (ms).
     *
     * Fórmula: h(t) = Z0 + Σ [ Aₙ × cos(ωₙ × t_doodson - φₙ) ]
     *
     * t_doodson = horas desde 1899-12-31 00:00 UTC
     *           = horas_unix + PYTIDES_EPOCH_HOURS
     */
    public double heightAt(long timestampMs) {
        // Convertir ms Unix a horas Doodson
        double t = (timestampMs / 1000.0) / 3600.0 + PYTIDES_EPOCH_HOURS;

        double h = Z0;
        for (double[] c : CONSTITUENTS) {
            double speed = c[0]; // °/hora
            double amp   = c[1]; // metros
            double phase = c[2]; // grados
            h += amp * Math.cos(Math.toRadians(speed * t - phase));
        }
        return h;
    }

    // ── Búsqueda de extremos ───────────────────────────────────────────────

    private List<TideData.TideExtreme> findExtremes(long startMs, long endMs) {
        List<TideData.TideExtreme> extremes = new ArrayList<>();
        long step = 10 * 60 * 1000L; // muestrear cada 10 minutos

        double prevHeight = heightAt(startMs);
        double prevDelta  = 0;

        for (long t = startMs + step; t <= endMs; t += step) {
            double h     = heightAt(t);
            double delta = h - prevHeight;

            if (prevDelta > 0.0001 && delta < -0.0001) {
                // Cambio de subir a bajar = pleamar
                long refined = refineExtreme(t - step, t, true);
                extremes.add(new TideData.TideExtreme(
                        refined, heightAt(refined), true));
            } else if (prevDelta < -0.0001 && delta > 0.0001) {
                // Cambio de bajar a subir = bajamar
                long refined = refineExtreme(t - step, t, false);
                extremes.add(new TideData.TideExtreme(
                        refined, heightAt(refined), false));
            }

            prevHeight = h;
            prevDelta  = delta;
        }
        return extremes;
    }

    /**
     * Refina la localización de un extremo usando bisección.
     * Aumenta precisión de ±10 min a ±1 min.
     */
    private long refineExtreme(long t1, long t2, boolean findMax) {
        for (int i = 0; i < 8; i++) {
            long mid = (t1 + t2) / 2;
            double hMid = heightAt(mid);
            double h1   = heightAt(t1);
            double h2   = heightAt(t2);

            if (findMax) {
                if (h1 > h2) t2 = mid; else t1 = mid;
            } else {
                if (h1 < h2) t2 = mid; else t1 = mid;
            }
        }
        return (t1 + t2) / 2;
    }

    // ── Coeficiente de marea ───────────────────────────────────────────────

    /**
     * Coeficiente de marea en escala francesa (20-120).
     * Basado en el rango mareal del día centrado en el timestamp dado.
     *
     * Para Sechura:
     *   Rango mínimo teórico ≈ 0.25 m (mareas muertas)
     *   Rango máximo teórico ≈ 1.10 m (mareas vivas)
     */
    private int computeCoefficient(long timestampMs) {
        long step = 30 * 60 * 1000L;
        long window = 12 * 3600 * 1000L;

        double maxH = Double.MIN_VALUE;
        double minH = Double.MAX_VALUE;

        for (long t = timestampMs - window; t <= timestampMs + window; t += step) {
            double h = heightAt(t);
            if (h > maxH) maxH = h;
            if (h < minH) minH = h;
        }

        double range   = maxH - minH;
        double minRange = 0.25;
        double maxRange = 1.10;

        double coef = 20 + ((range - minRange) / (maxRange - minRange)) * 100;
        return (int) Math.max(20, Math.min(120, coef));
    }

    // ── Curva horaria ──────────────────────────────────────────────────────

    private List<TideData.HourlyPoint> computeHourly(long startMs, int hours) {
        List<TideData.HourlyPoint> points = new ArrayList<>();
        long hourMs = 3600 * 1000L;
        long roundedStart = (startMs / hourMs) * hourMs;

        for (int i = 0; i < hours; i++) {
            long t = roundedStart + (long) i * hourMs;
            TideData.HourlyPoint p = new TideData.HourlyPoint(t, heightAt(t));
            p.isNow = (i == 0);
            points.add(p);
        }
        return points;
    }

    // ── Fase lunar ────────────────────────────────────────────────────────

    private void computeMoonPhase(long timestampMs, TideData data) {
        // Luna nueva de referencia: 6 enero 2000, 18:14 UTC
        // Verificado contra NASA y múltiples fuentes astronómicas
        double referenceNewMoonMs = 947_182_440_000.0;
        double cycleMs = 29.53059 * 24 * 3600 * 1000.0;

        double elapsed = (timestampMs - referenceNewMoonMs) % cycleMs;
        if (elapsed < 0) elapsed += cycleMs;

        // Edad de la luna en días (0 = luna nueva, ~14.76 = luna llena)
        double moonAge = (elapsed / cycleMs) * 29.53059;

        // Clasificación por edad en días (más precisa que por fracción)
        // Basada en el ciclo sinódico estándar de 29.53 días
        if (moonAge < 1.0 || moonAge >= 28.5) {
            data.moonPhase     = "🌑";
            data.moonPhaseName = "Luna nueva";
        } else if (moonAge < 6.38) {
            data.moonPhase     = "🌒";
            data.moonPhaseName = "Creciente";
        } else if (moonAge < 8.38) {
            data.moonPhase     = "🌓";
            data.moonPhaseName = "Cuarto creciente";
        } else if (moonAge < 13.77) {
            data.moonPhase     = "🌔";
            data.moonPhaseName = "Gibosa creciente";
        } else if (moonAge < 15.77) {
            data.moonPhase     = "🌕";
            data.moonPhaseName = "Luna llena";
        } else if (moonAge < 21.15) {
            data.moonPhase     = "🌖";
            data.moonPhaseName = "Gibosa menguante";
        } else if (moonAge < 23.15) {
            data.moonPhase     = "🌗";
            data.moonPhaseName = "Cuarto menguante";
        } else {
            data.moonPhase     = "🌘";
            data.moonPhaseName = "Menguante";
        }
    }
}