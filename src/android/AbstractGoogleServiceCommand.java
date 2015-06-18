package com.cowbell.cordova.geofence;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.GeofencingApi;

public abstract class AbstractGoogleServiceCommand implements
        ConnectionCallbacks, OnConnectionFailedListener {

    protected Logger logger;
    protected boolean connectionInProgress = false;
    protected List<IGoogleServiceCommandListener> listeners;
    protected Context context;
    protected GoogleApiClient googleApiClient = null;

    public AbstractGoogleServiceCommand(Context context) {
        this.context = context;
        logger = Logger.getLogger();
        listeners = new ArrayList<IGoogleServiceCommandListener>();
        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    private void connectToGoogleServices() {

        if (!googleApiClient.isConnected() || !googleApiClient.isConnecting()
                && !connectionInProgress) {
            connectionInProgress = true;
            logger.log(Log.DEBUG, "Connecting location client");
            googleApiClient.connect();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        connectionInProgress = false;
        logger.log(Log.DEBUG, "Connecting to google services fail - "
                + connectionResult.toString());
    }

    @Override
    public void onConnected(Bundle arg0) {
        // TODO Auto-generated method stub
        logger.log(Log.DEBUG, "Google play services connected");

        if (!connectionInProgress)
            connectionInProgress = true;

        ExecuteCustomCode();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        logger.log(Log.DEBUG, "Google play services suspended");
        connectionInProgress = false;
    }

    public void addListener(IGoogleServiceCommandListener listener) {
        listeners.add(listener);
    }

    public void Execute() {
        connectToGoogleServices();
    }

    protected void CommandExecuted() {
        // Turn off the in progress flag and disconnect the client
        connectionInProgress = false;
        googleApiClient.disconnect();
        for (IGoogleServiceCommandListener listener : listeners) {
            listener.onCommandExecuted();
        }
    }

    protected abstract void ExecuteCustomCode();

}
