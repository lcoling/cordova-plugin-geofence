package com.cowbell.cordova.geofence;

import com.google.android.gms.location.Geofence;
import com.google.gson.annotations.Expose;

public class GeoNotification {

    public static final int MINIMUM_RADIUS = 100;

    @Expose public String id;
    @Expose public double latitude;
    @Expose public double longitude;
    @Expose public int radius;
    @Expose public int transitionType;

    @Expose public Notification notification;

    public GeoNotification() {
    }

    public Geofence toGeofence() {
        return new Geofence.Builder()
            .setRequestId(id)
            .setTransitionTypes(transitionType)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(Long.MAX_VALUE)
            .build();
    }

    public String toJson() {
        return Gson.get().toJson(this);
    }

    public static GeoNotification fromJson(String json) {
        if (json == null)
            return null;

        GeoNotification geo = Gson.get().fromJson(json, GeoNotification.class);

        // geofences in android are terribly inaccurate so we're implementing
        // a minimum radius of 100m
        if (geo.radius < MINIMUM_RADIUS) {
            geo.radius = MINIMUM_RADIUS;
        }

        return geo;
    }
}
