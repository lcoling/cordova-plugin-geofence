package com.cowbell.cordova.geofence;

import java.util.List;

import android.app.PendingIntent;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;


public class AddGeofenceCommand extends AbstractGoogleServiceCommand {
    private List<Geofence> geofencesToAdd;
    private PendingIntent pendingIntent;

    public AddGeofenceCommand(Context context, PendingIntent pendingIntent,
            List<Geofence> geofencesToAdd) {
        super(context);
        this.geofencesToAdd = geofencesToAdd;
        this.pendingIntent = pendingIntent;
    }

    @Override
    public void ExecuteCustomCode() {
        // TODO Auto-generated method stub
        logger.log(Log.DEBUG, "Adding new geofences");

        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .addGeofences(geofencesToAdd)
                .build();

        PendingResult<Status> result = LocationServices.GeofencingApi
                .addGeofences(googleApiClient, geofencingRequest, pendingIntent);

        result.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    // Successfully registered
                    logger.log(Log.DEBUG, "Geofences successfully added");
                } else if (status.hasResolution()) {
                    // Google provides a way to fix the issue
                    /*
                    status.startResolutionForResult(
                            mContext,     // your current activity used to receive the result
                            RESULT_CODE); // the result code you'll look for in your
                    // onActivityResult method to retry registering
                    */
                } else {
                    // No recovery. Weep softly or inform the user.
                    logger.log(Log.DEBUG, "Adding geofences failed");
                }

                CommandExecuted();
            }
        });
    }
}
