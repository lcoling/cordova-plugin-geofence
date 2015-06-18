package com.cowbell.cordova.geofence;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;

public class GeofencePlugin extends CordovaPlugin {
    public static final String TAG = "GeofencePlugin";
    private GeoNotificationManager geoNotificationManager;
    private Context context;
    protected static Boolean isInBackground = true;
    private static CordovaWebView webView = null;
    private LocationManager locationManager = null;
    private LocationListener locationListener = null;
    private SimpleDateFormat dt = new SimpleDateFormat("yyyyMMddhhmmss");
    private int pid = android.os.Process.myPid();

    /**
     * @param cordova
     *            The context of the main Activity.
     * @param webView
     *            The associated CordovaWebView.
     */
    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        GeofencePlugin.webView = webView;
        context = this.cordova.getActivity().getApplicationContext();
        Logger.setLogger(new Logger(TAG, context, false));
        geoNotificationManager = new GeoNotificationManager(context);
        locationManager = (LocationManager)this.context.getSystemService(Context.LOCATION_SERVICE);

        //configureLocationPinger();
    }

    @Override
    public boolean execute(final String action, final JSONArray args,
            final CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "GeofencePlugin execute action: " + action + " args: "
                + args.toString());

        Runnable run = new Runnable() {

            @Override
            public void run() {
                try {
                    if (action.equals("addOrUpdate")) {
                        List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();
                        for (int i = 0; i < args.length(); i++) {
                            GeoNotification not = parseFromJSONObject(args.getJSONObject(i));
                            if (not != null) {
                                geoNotifications.add(not);
                            }
                        }
                        geoNotificationManager.addGeoNotifications(geoNotifications,
                                callbackContext);
                    } else if (action.equals("fireGeofence")) {
                        String id = args.getString(0);
                        geoNotificationManager.fireGeofence(id, callbackContext);
                        // fire geofence
                    } else if (action.equals("remove")) {
                        List<String> ids = new ArrayList<String>();
                        for (int i = 0; i < args.length(); i++) {
                            ids.add(args.getString(i));
                        }
                        geoNotificationManager.removeGeoNotifications(ids, callbackContext);
                    } else if (action.equals("removeAll")) {
                        geoNotificationManager.removeAllGeoNotifications(callbackContext);
                    } else if (action.equals("getWatched")) {
                        List<GeoNotification> geoNotifications = geoNotificationManager
                                .getWatched();
                        callbackContext.success(Gson.get().toJson(geoNotifications));
                    } else if (action.equals("initialize")) {
                        callbackContext.success();
                    } else if (action.equals("deviceready")) {
                        deviceReady();
                        callbackContext.success();
                    } else {
                        callbackContext.error("Invalid action specified");
                    }
                }
                catch (JSONException jsonEx) {
                    callbackContext.error("Error parsing JSON parameters provided:" + jsonEx.getMessage());
                }
            }
        };

        cordova.getThreadPool().execute(run);

        return true;

    }

    private GeoNotification parseFromJSONObject(JSONObject object) {
        GeoNotification geo = null;
        geo = GeoNotification.fromJson(object.toString());
        return geo;
    }

    public static void fireReceiveTransition(List<GeoNotification> notifications) {
        String js = "setTimeout('geofence.queueGeofencesForTransition("
                + Gson.get().toJson(notifications) + ")',0)";
        if (webView == null) {
            Log.d(TAG, "Webview is null");
        } else {
            webView.sendJavascript(js);
        }
    }
    
    private void deviceReady() {
    	
    	Intent intent = cordova.getActivity().getIntent();
    	
    	String data = intent.getStringExtra("geofence.notification.data");
    	
        String js = "setTimeout('geofence.onNotificationClicked("
                + data + ")',0)";
        if (data == null) {
            Log.d(TAG, "No notifications clicked.");
        } else {
            webView.sendJavascript(js);
        }

        /*
        
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate
                (new Runnable() {
                    public void run() {
                        saveLogcatLogs();
                    }
                }, 1, 2, TimeUnit.MINUTES);

        */
    }

    private void saveLogcatLogs() {
        try {
            String filename = File.separator + "console_" + dt.format(new Date()) + ".log";

            File logFile = new File(context.getExternalFilesDir(null), filename);
            if (!logFile.exists())
                logFile.createNewFile();

            Log.d(TAG, "Writing log to path: " + logFile.getAbsolutePath());

            Process process = Runtime.getRuntime().exec("logcat -d -v threadtime | grep " + Integer.toString(pid));
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String newLine = System.getProperty("line.separator");
            if (newLine == null) {
                newLine = "\n";
            }

            StringBuilder log = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                log.append(line);
                log.append(newLine);
            }

            FileOutputStream outStream = new FileOutputStream(logFile, true);
            byte[] buffer = log.toString().getBytes();

            outStream.write(buffer);
            outStream.close();

            Runtime.getRuntime().exec("logcat -c");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void configureLocationPinger() {
        // calling this just for kicks
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        locationListener = new NoOpLocationListener();
        int interval = 2 * 60 * 1000; // 2 minutes
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, interval, 20, locationListener);

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);

    }

    private class NoOpLocationListenerIntentService extends IntentService {

        private LocationManager locationManager = null;

        public NoOpLocationListenerIntentService() {
            super("NoOpLocationListenerIntentService");

            locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        }

        @Override
        protected void onHandleIntent(Intent intent) {


            //Log.d(TAG, "Current location is - lat: " + Double.toString(location.getLatitude()) + " long: " + Double.toString(location.getLongitude()));
        }
    }
    private class NoOpLocationListener implements LocationListener {


        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, "Current location is - lat: " + Double.toString(location.getLatitude()) + " long: " + Double.toString(location.getLongitude()));
        }

        @Override
        public void onProviderDisabled(String provider) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }
    }

}
