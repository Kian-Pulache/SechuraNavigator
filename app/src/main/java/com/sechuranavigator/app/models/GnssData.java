// archivo: app/src/main/java/com/sechuranavigator/app/models/GnssData.java
package com.sechuranavigator.app.models;

public class GnssData {

    // Posición
    public double latitude;       // grados decimales, ej. -5.628431
    public double longitude;      // grados decimales, ej. -80.851209
    public double altitude;       // metros sobre el nivel del mar
    public float accuracy;        // radio de precisión en metros (1 sigma)

    // Movimiento y orientación
    public float bearing;         // dirección en grados (0-360), 0 = Norte
    public float speed;           // velocidad en m/s

    // Calidad de señal
    public int satelliteCount;    // satélites usados en el fix actual

    // Estado
    public boolean hasValidFix;   // true cuando la señal es confiable

    // ── Calidad de la lectura ──────────────────────────────────────────────
    public boolean isApproximate;  // true = coordenadas aproximadas (precisión pobre) / false = coordenadas confiables

    // Constructor vacío — Room y otros sistemas lo necesitan
    public GnssData() {
        hasValidFix = false;
    }
}
