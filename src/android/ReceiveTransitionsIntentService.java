package com.cowbell.cordova.geofence;

import java.util.ArrayList;
import java.util.List;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

public class ReceiveTransitionsIntentService extends IntentService {
    protected BeepHelper beepHelper;
    protected GeoNotificationNotifier notifier;
    protected GeoNotificationStore store;

    /**
     * Sets an identifier for the service
     */
    public ReceiveTransitionsIntentService() {
        super("ReceiveTransitionsIntentService");
        beepHelper = new BeepHelper();
        store = new GeoNotificationStore(this);
        Logger.setLogger(new Logger(GeofencePlugin.TAG, this, false));
    }

    /**
     * Handles incoming intents
     *
     * @param intent
     *            The Intent sent by Location Services. This Intent is provided
     *            to Location Services (inside a PendingIntent) when you call
     *            addGeofences()
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        notifier = new GeoNotificationNotifier(
                (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE),
                this
        );

        Logger logger = Logger.getLogger();
        logger.log(Log.DEBUG, "ReceiveTransitionsIntentService - onHandleIntent");

        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null) {
            logger.log(Log.DEBUG, "ReceiveTransitionsIntentService - onHandleIntent - no geofencing event detected");
            return;
        }

        if (event.hasError()) {
            // Get the error code with a static method
            int errorCode = event.getErrorCode();
            // Log the error
            logger.log(Log.ERROR, "Location Services error: " + Integer.toString(errorCode));
        }
        else {
            // Get the type of transition (entry or exit)
            int transitionType = event.getGeofenceTransition();
            if ((transitionType == Geofence.GEOFENCE_TRANSITION_ENTER)
                    || (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT)) {

                logger.log(Log.DEBUG, "Geofence transition detected");

                List<Geofence> triggerList = event.getTriggeringGeofences();

                List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();

                for (Geofence fence : triggerList) {
                    String fenceId = fence.getRequestId();
                    GeoNotification geoNotification = store
                            .getGeoNotification(fenceId);

                    if (geoNotification != null) {
                        if (geoNotification.notification != null) {
                            notifier.notify(geoNotification.notification);
                        }
                        geoNotifications.add(geoNotification);
                    }
                }

                if (geoNotifications.size() > 0) {
                    GeofencePlugin.fireReceiveTransition(geoNotifications);
                }
            } else {
                logger.log(Log.ERROR, "Geofence transition error: "
                        + transitionType);
            }
        }
    }
}
