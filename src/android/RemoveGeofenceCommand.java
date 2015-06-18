package com.cowbell.cordova.geofence;

import java.util.List;

import android.app.PendingIntent;
import android.content.Context;
import android.util.Log;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationServices;

public class RemoveGeofenceCommand extends AbstractGoogleServiceCommand {
    private PendingIntent pendingIntent;
    private List<String> geofencesIds;

    public RemoveGeofenceCommand(Context context, PendingIntent pendingIntent) {
        super(context);
        this.pendingIntent = pendingIntent;
    }

    public RemoveGeofenceCommand(Context context, List<String> geofencesIds) {
        super(context);
        this.geofencesIds = geofencesIds;
    }

    @Override
    protected void ExecuteCustomCode() {

        PendingResult<Status> status = null;

        if (pendingIntent != null) {
            status = LocationServices.GeofencingApi.removeGeofences(googleApiClient, pendingIntent);
            status.setResultCallback(new RemoveResultCallback("All Geofences removed", "Removing all geofences failed"));
        //for some reason an exception is thrown when clearing an empty set of geofences
        } else if (geofencesIds != null && geofencesIds.size() > 0) {
            try {
                status = LocationServices.GeofencingApi.removeGeofences(googleApiClient, this.geofencesIds);
                status.setResultCallback(new RemoveResultCallback("Geofences removed", "Removing geofences failed"));
            }
            catch (Exception ex) {
                logger.log(Log.DEBUG, ex.getMessage());
            }
        } else {
            logger.log(Log.DEBUG, "Tried to remove Geofences when there were none");
            CommandExecuted();
        }
    }

    private class RemoveResultCallback implements ResultCallback<Status> {

        private String successMessage = null;
        private String failMessage = null;

        public RemoveResultCallback(String successMessage, String failMessage) {
            this.successMessage = successMessage;
            this.failMessage = failMessage;
        }

        @Override
        public void onResult(Status status) {
            if (status.isSuccess()) {
                // Successfully registered
                logger.log(Log.DEBUG, successMessage);
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
                logger.log(Log.DEBUG, failMessage);
            }
            CommandExecuted();
        }
    }
}
