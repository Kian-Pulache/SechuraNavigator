// archivo: app/src/main/java/com/sechuranavigator/app/StopTrackingReceiver.java
package com.sechuranavigator.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopTrackingReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP_FROM_NOTIF =
            "com.sechuranavigator.app.STOP_FROM_NOTIF";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_STOP_FROM_NOTIF.equals(intent.getAction())) {
            Intent stopIntent = new Intent(context, TrackingService.class);
            stopIntent.setAction(TrackingService.ACTION_STOP);
            // Usar startForegroundService para garantizar que el intent llega
            if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(stopIntent);
            } else {
                context.startService(stopIntent);
            }
        }
    }
}
