package mobi.omegacentauri.giantbarometer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BarometerService extends Service implements SensorEventListener, LocationListener {
    private static final String TAG = "GiantBarometer:service";
    private NotificationChannel mChannel;
    private SensorManager sensorManager;
    private Object calibration;
    private SharedPreferences options;
    private LocationManager locationManager;
    private long startTime;
    private long lastLocationTime = Long.MIN_VALUE;
    private long standardPressureTimeout = 5*60000;
    static final private long standardPressureTimeout_NWS = 5*60000;
    static final private long standardPressureTimeout_OM = 10*60000;
    private WeatherInfo lastStationData = null;
    private double pressureAtSeaLevel = BarometerActivity.standardPressureAtSeaLevel;
    private double temperature = 273.15 + 15;
    public static BarometerService self = null;
    public static BarometerActivity activity = null;
    public static boolean needStationData = false;
    private List<Analysis.TimedDatum<BarometerActivity.Observations>> buffer = new ArrayList<>();
    private boolean gpsAltitude;
    private LocationListener gpsListener = null;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        options = PreferenceManager.getDefaultSharedPreferences(this);
        sensorManager = (SensorManager) getSystemService(Service.SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

    }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStart");

        String channelId = "barometer_channel";
        Notification.Builder nb;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nb = new Notification.Builder(this, channelId);
            mChannel = new NotificationChannel(channelId, "Giant Barometer", NotificationManager.IMPORTANCE_LOW);
            // Configure the notification channel.
            mChannel.setDescription("Barometer monitoring");
            mChannel.enableLights(false);
            mChannel.setVibrationPattern(null);
            mNotificationManager.createNotificationChannel(mChannel);
            nb.setChannelId(channelId);
        }
        else {
            nb = new Notification.Builder(this);
        }
        nb.setOngoing(true);
        Intent activityIntent = new Intent(this, BarometerActivity.class);
        nb.setContentIntent(PendingIntent.getActivity(this, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE));
        nb.setContentText("Collecting barometer/altitude data");
        nb.setSmallIcon(R.drawable.updown);
        nb.setContentTitle("Giant Barometer");
        Notification notification = nb.build();
        if (notification == null) {
            Log.e(TAG, "null notification");
            // don't know what to do or how it can happen
        }
        if (Build.VERSION.SDK_INT >= 29) {
            Log.v("GiantBarometer", "service");
            startForeground(startId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        }
        else {
            startForeground(startId, notification);
        }

        gpsAltitude = BarometerActivity.locationPermission.contains("FINE") && options.getBoolean(Options.PREF_GPS_ALTITUDE, false);
        calibration = Options.getCalibration(this, options );
        if (calibration.equals("nws"))
            standardPressureTimeout = standardPressureTimeout_NWS;
        else
            standardPressureTimeout = standardPressureTimeout_OM;

        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                SensorManager.SENSOR_DELAY_NORMAL);

        if (gpsAltitude) {
            gpsListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    onGPSLocationChanged(location);
                }
            };

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    1000,
                    1, gpsListener);
        }

        startTime = System.currentTimeMillis();

        updateStandardPressure(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));

        if (calibration.equals("nws") || calibration.equals("om")) {
            requestCoarseLocation();
        }

        self = this;
        buffer.clear();

        return START_STICKY;
    }

    private void onGPSLocationChanged(Location location) {
        double alt = location.getAltitude();
        Log.v(TAG, "gps altitude "+alt);
        Analysis.TimedDatum<BarometerActivity.Observations> newObs =
                new Analysis.TimedDatum<>(System.currentTimeMillis(),
                        new BarometerActivity.Observations(alt));
        addObservation(newObs);
    }

    private void updateStandardPressure(Location location) {
        Log.v(TAG, "update standard pressure "+location);
    }

    @SuppressLint("MissingPermission")
    private void requestCoarseLocation() {
        if (calibration.equals("nws") || calibration.equals("om")) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    standardPressureTimeout / 3,
                    500, this);
        }
    }

    @Override
    public boolean stopService(Intent intent) {
        super.stopService(intent);
        onDestroy();
        return true;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy");
        super.onDestroy();
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this);
        if (gpsListener != null) {
            locationManager.removeUpdates(gpsListener);
            gpsListener = null;
        }
        self = null;
        stopForeground(true);
        stopSelf();
    }

    synchronized void requestLocationIfNeeded() {
        if (System.currentTimeMillis() >= lastLocationTime + standardPressureTimeout)
            requestCoarseLocation();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
//        Log.v(TAG, ""+event.values[0]);
        requestLocationIfNeeded();
        Analysis.TimedDatum<BarometerActivity.Observations> newObs =
                new Analysis.TimedDatum<>(System.currentTimeMillis(), new BarometerActivity.Observations(event.values[0], lastStationData));
        addObservation(newObs);
    }

    private void addObservation(Analysis.TimedDatum<BarometerActivity.Observations> newObs) {
        buffer.add(newObs);
        if (activity != null) {
            List<Analysis.TimedDatum<BarometerActivity.Observations>> toAdd = buffer;
            buffer = new ArrayList<>();
            activity.addObservations(toAdd);
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onLocationChanged(Location location) {
        Log.v(TAG, "location changed");
        setLastLocationTime(System.currentTimeMillis());
        locationManager.removeUpdates(this);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                Log.v(TAG, "getting nws data");
                WeatherInfo winfo = calibration.equals("nws") ? new NWS() : new OpenMeteo();
                if (winfo.getData(location)) {
                    setStationData(winfo);
                }
                else {
                    setLastLocationTime(System.currentTimeMillis()-standardPressureTimeout + 5000); // try again in 5 sec
                }
            }
        });
    }

    private synchronized void setLastLocationTime(long t) {
        lastLocationTime = t;
    }

    private synchronized void setStationData(WeatherInfo winfo) {
        lastStationData = winfo;
        pressureAtSeaLevel = winfo.pressureAtSeaLevel;
        temperature = winfo.temperature;
        Log.v(TAG, "station "+winfo.pressureAtSeaLevel+" "+winfo.temperature);
    }
}
