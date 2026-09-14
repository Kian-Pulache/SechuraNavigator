// archivo: app/src/main/java/com/sechuranavigator/app/managers/GnssListener.java
package com.sechuranavigator.app.managers;

import com.sechuranavigator.app.models.GnssData;

public interface GnssListener {

    // Se llama cada vez que llega una posición GPS nueva
    void onGnssUpdate(GnssData data);

    // Se llama cuando el estado del GPS cambia (buscando, listo, sin permiso)
    void onGnssStatusChange(GnssStatus status);

    enum GnssStatus {
        SEARCHING,      // buscando satélites
        READY,          // fix obtenido, precisión aceptable
        LOW_ACCURACY,   // fix obtenido pero precisión > umbral configurado
        NO_PERMISSION   // el usuario no otorgó permiso de ubicación
    }
}
