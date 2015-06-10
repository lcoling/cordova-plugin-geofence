//
//  GeofencePlugin.swift
//  ionic-geofence
//
//  Created by tomasz on 07/10/14.
//
//

import Foundation
import AudioToolbox

let TAG = "GeofencePlugin"
let iOS8 = floor(NSFoundationVersionNumber) > floor(NSFoundationVersionNumber_iOS_7_1)
let iOS7 = floor(NSFoundationVersionNumber) <= floor(NSFoundationVersionNumber_iOS_7_1)
let DefaultDelayInSeconds = 10

func log(message: String){
    NSLog("%@ - %@", TAG, message)
}

var savedTriggeredGeofences = [JSON]()
var GeofencePluginWebView: UIWebView?

@objc(HWPGeofencePlugin) class GeofencePlugin : CDVPlugin {
    let geoNotificationManager = GeoNotificationManager()
    let priority = DISPATCH_QUEUE_PRIORITY_DEFAULT
    
    override func pluginInitialize() {
        log("Plugin initialization")
        GeofencePluginWebView = self.webView
    }
    
    func initialize(command: CDVInvokedUrlCommand) {
        log("initialize method invoked")
        
        if (savedTriggeredGeofences.count > 0) {
            log("Firing saved transitions - transition count: \(savedTriggeredGeofences.count)")
            GeofencePlugin.fireReceiveTransition(savedTriggeredGeofences)
            savedTriggeredGeofences.removeAll(keepCapacity: true)
        }
        
        var pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.sendPluginResult(pluginResult, callbackId: command.callbackId)
    }

    func promptForNotificationPermission() {
        UIApplication.sharedApplication().registerUserNotificationSettings(UIUserNotificationSettings(
            forTypes: UIUserNotificationType.Sound | UIUserNotificationType.Alert | UIUserNotificationType.Badge,
            categories: nil
            )
        )
    }

    func deviceready(command: CDVInvokedUrlCommand) {
        var pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
        self.commandDelegate.sendPluginResult(pluginResult, callbackId: command.callbackId)
    }
    
    func addOrUpdate(command: CDVInvokedUrlCommand) {
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            // do some task
            for geo in command.arguments {
                self.geoNotificationManager.addOrUpdateGeoNotification(JSON(geo))
            }
            dispatch_async(dispatch_get_main_queue()) {
                var pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
                self.commandDelegate.sendPluginResult(pluginResult, callbackId: command.callbackId)
            }
        }
    }

    func getWatched(command: CDVInvokedUrlCommand) {
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            var watched = self.geoNotificationManager.getWatchedGeoNotifications()!
            let watchedJsonString = watched.description
            dispatch_async(dispatch_get_main_queue()) {
                var pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAsString: watchedJsonString)
                self.commandDelegate.sendPluginResult(pluginResult, callbackId: command.callbackId)
            }
        }
    }

    func remove(command: CDVInvokedUrlCommand) {
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            for id in command.arguments {
                self.geoNotificationManager.removeGeoNotification(id as! String)
            }
            dispatch_async(dispatch_get_main_queue()) {
                var pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
                self.commandDelegate.sendPluginResult(pluginResult, callbackId: command.callbackId)
            }
        }
    }

    func removeAll(command: CDVInvokedUrlCommand) {
        dispatch_async(dispatch_get_global_queue(priority, 0)) {
            self.geoNotificationManager.removeAllGeoNotifications()
            dispatch_async(dispatch_get_main_queue()) {
                var pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
                self.commandDelegate.sendPluginResult(pluginResult, callbackId: command.callbackId)
            }
        }
    }

    func fireGeofence(command: CDVInvokedUrlCommand) {
        var id: String = command.argumentAtIndex(0) as! String;
        self.geoNotificationManager.fireGeofence(id)
        dispatch_async(dispatch_get_main_queue()) {
            var pluginResult = CDVPluginResult(status: CDVCommandStatus_OK)
            self.commandDelegate.sendPluginResult(pluginResult, callbackId: command.callbackId)
        }
    }
    
    class func fireOrSaveTransition(geoNotification: JSON) {
        if (GeofencePluginWebView != nil) {
            GeofencePlugin.fireReceiveTransition(geoNotification)
        }
        else {
            savedTriggeredGeofences.append(geoNotification)
            log("GeofencePlugin - fireOrSaveTransition - saving transition event")
        }
    }
    
    class func fireReceiveTransition(geoNotifications: [JSON]!) {
        let js = "setTimeout('geofence.queueGeofencesForTransition(" + geoNotifications.description + ")',0)";
        if (GeofencePluginWebView != nil) {
            GeofencePluginWebView!.stringByEvaluatingJavaScriptFromString(js);
            log("GeofencePlugin - fireReceiveTransition")
        }
    }
    
    class func fireReceiveTransition(geoNotification: JSON) {
        var mustBeArray = [JSON]()
        mustBeArray.append(geoNotification)
        GeofencePlugin.fireReceiveTransition(mustBeArray)
    }
}

// class for faking crossing geofences
class GeofenceFaker {
    let priority = DISPATCH_QUEUE_PRIORITY_DEFAULT
    let geoNotificationManager: GeoNotificationManager

    init(manager: GeoNotificationManager) {
        geoNotificationManager = manager
    }

    func start() {
         dispatch_async(dispatch_get_global_queue(priority, 0)) {
            while (true) {
                log("FAKER")
                let notify = arc4random_uniform(4)
                if notify == 0 {
                    log("FAKER notify chosen, need to pick up some region")
                    var geos = self.geoNotificationManager.getWatchedGeoNotifications()!
                    if geos.count > 0 {
                        //WTF Swift??
                        let index = arc4random_uniform(UInt32(geos.count))
                        var geo = geos[Int(index)]
                        let id = geo["id"].asString!
                        dispatch_async(dispatch_get_main_queue()) {
                            if let region = self.geoNotificationManager.getMonitoredRegion(id) {
                                log("FAKER Trigger didEnterRegion")
                                self.geoNotificationManager.locationManager(
                                    self.geoNotificationManager.locationManager,
                                    didEnterRegion: region
                                )
                            }
                        }
                    }
                }
                NSThread.sleepForTimeInterval(3);
            }
         }
    }

    func stop() {

    }
}

class GeoNotificationManager : NSObject, CLLocationManagerDelegate {
    let locationManager = CLLocationManager()
    let store = GeoNotificationStore()
    var requestedPermission = false

    override init() {
        log("GeoNotificationManager init")
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        if (!CLLocationManager.locationServicesEnabled()) {
            log("Location services is not enabled")
        } else {
            log("Location services enabled")
        }

        if (!CLLocationManager.isMonitoringAvailableForClass(CLRegion)) {
            log("Geofencing not available")
        }
    }
    
    // Call this before modifying geofences.
    // We don't do this in init() as we want to delay the permission popup until
    // we've explained to the user why we need the permission in the first place.
    func requestPermission() {
        if (!requestedPermission) {
            requestedPermission = true
            if iOS8 {
                locationManager.requestAlwaysAuthorization()
            }
        }
    }

    func addOrUpdateGeoNotification(geoNotification: JSON) {
        log("GeoNotificationManager addOrUpdate")
        
        requestPermission()

        if (!CLLocationManager.locationServicesEnabled()) {
            log("Locationservices is not enabled")
        }

        var location = CLLocationCoordinate2DMake(
            geoNotification["latitude"].asDouble!,
            geoNotification["longitude"].asDouble!
        )
        log("AddOrUpdate geo: \(geoNotification)")
        var radius = geoNotification["radius"].asDouble! as CLLocationDistance
        //let uuid = NSUUID().UUIDString
        let id = geoNotification["id"].asString

        var region = CLCircularRegion(
            circularRegionWithCenter: location,
            radius: radius,
            identifier: id
        )

        var transitionType = 0
        if let i = geoNotification["transitionType"].asInt {
            transitionType = i
        }
        region.notifyOnEntry = 0 != transitionType & 1
        region.notifyOnExit = 0 != transitionType & 2

        //store
        store.addOrUpdate(geoNotification)
        locationManager.startMonitoringForRegion(region)
    }

    func getWatchedGeoNotifications() -> [JSON]? {
        return store.getAll()
    }

    func getMonitoredRegion(id: String) -> CLRegion? {
        requestPermission()
        
        for object in locationManager.monitoredRegions {
            if let region = object as? CLCircularRegion {
                if (region.identifier == id) {
                    return region
                }
            }
        }
        return nil
    }

    func removeGeoNotification(id: String) {
        requestPermission()
        
        store.remove(id)
        var region = getMonitoredRegion(id)
        if (region != nil) {
            log("Stoping monitoring region \(id)")
            locationManager.stopMonitoringForRegion(region)
        }
    }

    func removeAllGeoNotifications() {
        requestPermission()
        
        store.clear()
        for object in locationManager.monitoredRegions {
            if let region = object as? CLCircularRegion {
                log("Stoping monitoring region \(region.identifier)")
                locationManager.stopMonitoringForRegion(region)
            }
        }
    }

    func locationManager(manager: CLLocationManager!, didUpdateLocations locations: [AnyObject]!) {
        log("didUpdateLocations")
    }

    func locationManager(manager: CLLocationManager!, didFailWithError error: NSError!) {
        log("didFailWithError: \(error)")
    }

    func locationManager(manager: CLLocationManager!, didFinishDeferredUpdatesWithError error: NSError!) {
        log("didFinishDeferredUpdatesWithError - \(error)")
    }

    func locationManager(manager: CLLocationManager!, didEnterRegion region: CLRegion!) {
        if (region is CLCircularRegion) {
            log("didEnterRegion - \(region.identifier)")
        }
        
        handleTransition(region)
    }

    func locationManager(manager: CLLocationManager!, didExitRegion region: CLRegion!) {
        if (region is CLCircularRegion) {
            log("didExitRegion - \(region.identifier)")
        }
        handleTransition(region)
    }

    func locationManager(manager: CLLocationManager!, didStartMonitoringForRegion region: CLRegion!) {
        if let geofence = region as? CLCircularRegion {
            log("didStartMonitoringForRegion region - \(region) lat \(geofence.center.latitude) lng \(geofence.center.longitude)")
        }
        else {
            log("Started monitoring an iBeacon")
        }
    }

    func locationManager(manager: CLLocationManager, didDetermineState state: CLRegionState, forRegion region: CLRegion) {
        if (region is CLCircularRegion) {
            log("didDetermineState - \(region.identifier)")
        }
    }

    func locationManager(manager: CLLocationManager, monitoringDidFailForRegion region: CLRegion!, withError error: NSError!) {
        log("monitoringDidFailForRegion - " + region.identifier + " failed " + error.description)
    }

    func fireGeofence(id: String) {
        if let geo = store.findById(id) {
            if let notification = geo["notification"].asDictionary {
                notifyAbout(geo)
            }
            GeofencePlugin.fireOrSaveTransition(geo)
        }
    }
    
    func handleTransition(region: CLRegion!) {
        if let geofence = region as? CLCircularRegion {
            if let geo = store.findById(geofence.identifier) {
                if let notification = geo["notification"].asDictionary {
                    notifyAbout(geo)
                }
                GeofencePlugin.fireOrSaveTransition(geo)
            }
        }
        else {
            log("Ignoring transition for iBeacon")
        }
    }

    func notifyAbout(geo: JSON) {
        log("Creating notification")
        
        var delayInSeconds = DefaultDelayInSeconds;
        
        if let delay = geo["triggerDelay"].asInt {
            delayInSeconds = delay;
        }
        
        // for notification triggering, we add 5 seconds to current time to allow for plugin to do a JS callback
        let comps = NSDateComponents()
        comps.second = delayInSeconds;
        
        let cal = NSCalendar.currentCalendar()
        var fireDate = cal.dateByAddingComponents(comps, toDate: NSDate(), options: nil)
        
        var notification = UILocalNotification()
        notification.timeZone = NSTimeZone.defaultTimeZone()
        notification.fireDate = fireDate
        notification.soundName = UILocalNotificationDefaultSoundName
        notification.alertBody = geo["notification"]["text"].asString!
        notification.userInfo = buildUserInfoDictionary(geo)
        
        UIApplication.sharedApplication().scheduleLocalNotification(notification)

        if let vibrate = geo["notification"]["vibrate"].asArray {
            if (!vibrate.isEmpty && vibrate[0].asInt > 0) {
                AudioServicesPlayAlertSound(SystemSoundID(kSystemSoundID_Vibrate))
            }
        }
    }
    
    func buildUserInfoDictionary(geo: JSON) -> [NSObject:AnyObject] {
        var userInfo = NSMutableDictionary()

        userInfo.setObject(geo["notification"]["id"].asInt!, forKey:"id")
        userInfo.setObject(geo["notification"]["title"].asString!, forKey:"title")
        userInfo.setObject(geo["notification"]["text"].asString!, forKey:"text")
        userInfo.setObject(geo["notification"]["icon"].asString!, forKey:"icon")
        userInfo.setObject(geo["notification"]["smallIcon"].asString!, forKey:"smallIcon")
        
        var survey = NSMutableDictionary()
        survey.setObject(geo["notification"]["data"]["id"].asString!, forKey: "id")
        
        userInfo.setObject(survey, forKey:"data")
        
        return userInfo as [NSObject : AnyObject]
    }
}

class GeoNotificationStore {
    init() {
        createDBStructure()
    }

    func createDBStructure() {
        let (tables, err) = SD.existingTables()

        if (err != nil) {
            log("Cannot fetch sqlite tables: \(err)")
            return
        }

        if (tables.filter { $0 == "GeoNotifications" }.count == 0) {
            if let err = SD.executeChange("CREATE TABLE GeoNotifications (ID TEXT PRIMARY KEY, Data TEXT)") {
                //there was an error during this function, handle it here
                log("Error while creating GeoNotifications table: \(err)")
            } else {
                //no error, the table was created successfully
                log("GeoNotifications table was created successfully")
            }
        }
    }

    func addOrUpdate(geoNotification: JSON) {
        if (findById(geoNotification["id"].asString!) != nil) {
            update(geoNotification)
        }
        else {
            add(geoNotification)
        }
    }

    func add(geoNotification: JSON) {
        let id = geoNotification["id"].asString!
        let err = SD.executeChange("INSERT INTO GeoNotifications (Id, Data) VALUES(?, ?)",
            withArgs: [id, geoNotification.description])

        if err != nil {
            log("Error while adding \(id) GeoNotification: \(err)")
        }
    }

    func update(geoNotification: JSON) {
        let id = geoNotification["id"].asString!
        let err = SD.executeChange("UPDATE GeoNotifications SET Data = ? WHERE Id = ?",
            withArgs: [geoNotification.description, id])

        if err != nil {
            log("Error while adding \(id) GeoNotification: \(err)")
        }
    }

    func findById(id: String) -> JSON? {
        let (resultSet, err) = SD.executeQuery("SELECT * FROM GeoNotifications WHERE Id = ?", withArgs: [id])

        if err != nil {
            //there was an error during the query, handle it here
            log("Error while fetching \(id) GeoNotification table: \(err)")
            return nil
        } else {
            if (resultSet.count > 0) {
                return JSON(string: resultSet[0]["Data"]!.asString()!)
            }
            else {
                return nil
            }
        }
    }

    func getAll() -> [JSON]? {
        let (resultSet, err) = SD.executeQuery("SELECT * FROM GeoNotifications")

        if err != nil {
            //there was an error during the query, handle it here
            log("Error while fetching from GeoNotifications table: \(err)")
            return nil
        } else {
            var results = [JSON]()
            for row in resultSet {
                if let data = row["Data"]?.asString() {
                    results.append(JSON(string: data))
                }
            }
            return results
        }
    }

    func remove(id: String) {
        let err = SD.executeChange("DELETE FROM GeoNotifications WHERE Id = ?", withArgs: [id])

        if err != nil {
            log("Error while removing \(id) GeoNotification: \(err)")
        }
    }

    func clear() {
        let err = SD.executeChange("DELETE FROM GeoNotifications")

        if err != nil {
            log("Error while deleting all from GeoNotifications: \(err)")
        }
    }
}
