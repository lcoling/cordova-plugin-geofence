package com.cowbell.cordova.geofence;

import java.util.ArrayList;
import java.util.List;

import org.apache.cordova.CallbackContext;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.Geofence;

public class GeoNotificationManager {
    private Context context;
    private GeoNotificationStore geoNotificationStore;
    private Logger logger;
    private List<Geofence> geoFences;
    private PendingIntent pendingIntent;
    private GoogleServiceCommandExecutor googleServiceCommandExecutor;
    private GeoNotificationNotifier notifier;

    public GeoNotificationManager(Context context) {
        this.context = context;
        geoNotificationStore = new GeoNotificationStore(context);
        logger = Logger.getLogger();
        googleServiceCommandExecutor = new GoogleServiceCommandExecutor();
        pendingIntent = getTransitionPendingIntent();
        if (areGoogleServicesAvailable()) {
            logger.log(Log.DEBUG, "Google play services available");
        } else {
            logger.log(Log.DEBUG, "Google play services not available");
        }

        notifier = new GeoNotificationNotifier(
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE),
                context);
    }

    public void loadFromStorageAndInitializeGeofences() {
        List<GeoNotification> geoNotifications = geoNotificationStore.getAll();
        geoFences = new ArrayList<Geofence>();
        for (GeoNotification geo : geoNotifications) {
            geoFences.add(geo.toGeofence());
        }
        if (!geoFences.isEmpty()) {
        	googleServiceCommandExecutor.QueueToExecute(new AddGeofenceCommand(
        			context, pendingIntent, geoFences));
        }	
    }

    public List<GeoNotification> getWatched() {
        List<GeoNotification> geoNotifications = geoNotificationStore.getAll();
        return geoNotifications;
    }

    private boolean areGoogleServicesAvailable() {
        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(context);

        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            return true;
        } else {
            return false;
        }
    }

    public void addGeoNotifications(List<GeoNotification> geoNotifications,
            final CallbackContext callback) {
        List<Geofence> newGeofences = new ArrayList<Geofence>();
        for (GeoNotification geo : geoNotifications) {
            geoNotificationStore.setGeoNotification(geo);
            newGeofences.add(geo.toGeofence());
        }
        AddGeofenceCommand geoFenceCmd = new AddGeofenceCommand(context,
                pendingIntent, newGeofences);
        if (callback != null) {
            geoFenceCmd.addListener(new IGoogleServiceCommandListener() {
                @Override
                public void onCommandExecuted() {
                    callback.success();
                }
            });
        }
        googleServiceCommandExecutor.QueueToExecute(geoFenceCmd);
    }

    public void removeGeoNotification(String id, final CallbackContext callback) {
        List<String> ids = new ArrayList<String>();
        ids.add(id);
        removeGeoNotifications(ids, callback);
    }

    public void removeGeoNotifications(List<String> ids,
            final CallbackContext callback) {
        RemoveGeofenceCommand cmd = new RemoveGeofenceCommand(context, ids);
        if (callback != null) {
            cmd.addListener(new IGoogleServiceCommandListener() {
                @Override
                public void onCommandExecuted() {
                    callback.success();
                }
            });
        }
        for (String id : ids) {
            geoNotificationStore.remove(id);
        }
        googleServiceCommandExecutor.QueueToExecute(cmd);
    }

    public void removeAllGeoNotifications(final CallbackContext callback) {
        List<GeoNotification> geoNotifications = geoNotificationStore.getAll();
        List<String> geoNotificationsIds = new ArrayList<String>();
        for (GeoNotification geo : geoNotifications) {
            geoNotificationsIds.add(geo.id);
        }
        removeGeoNotifications(geoNotificationsIds, callback);
    }

    public void fireGeofence(String id, final CallbackContext callback) {

        List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();

        GeoNotification geoNotification = geoNotificationStore
                .getGeoNotification(id);

        if (geoNotification != null) {
            if (geoNotification.notification != null) {
                notifier.notify(geoNotification.notification);
            }
            geoNotifications.add(geoNotification);
            GeofencePlugin.fireReceiveTransition(geoNotifications);
        }
        callback.success();
    }

    /*
     * Create a PendingIntent that triggers an IntentService in your app when a
     * geofence transition occurs.
     */
    private PendingIntent getTransitionPendingIntent() {
        // Create an explicit Intent
        Intent intent = new Intent(context,
                ReceiveTransitionsIntentService.class);
        /*
         * Return the PendingIntent
         */
        return PendingIntent.getService(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

}
