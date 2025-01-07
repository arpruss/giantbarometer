package mobi.omegacentauri.giantbarometer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.sql.Time;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;


public class BarometerActivity extends Activity implements SensorEventListener {
	private final static String TAG = BarometerActivity.class
			.getSimpleName();

    private static final long WAIT_TIME = 6000; // only show invalid value after this amount of waiting
	double pressureAtSeaLevel = -1;
	long lastPressureUpdate = -1;
    static public long lastValidTime = -WAIT_TIME;
//	Handler timeoutHandler;
	Handler buttonHideHandler;
	static final long initialTimeout = 40000;
	static final long periodicTimeout = 20000;

	static final long maximumKeep = 20*1000;
	boolean works;


	static final long standardPressureTimeout = 5*60000;

	private BigTextView pressureText;
	private BigTextView altitudeText;
	private Runnable periodicTimeoutRunnable;
	private View toolbarView;
	private Runnable buttonHideRunnable;
	private static final long buttonHideTime = 8000;

	List<TimedDatum> altitudeData = new ArrayList<>();
	RecentData recentPressures = new RecentData(maximumKeep);
	private GraphView graphView;
	private SharedPreferences options;
	private SensorManager sensorManager;
	private Runnable standardPressureUpdateTimeout;
	private Handler standardPressureHandler;
	private Runnable standardPressureTimeoutRunnable;
	private LocationListener locationListener;
	private LocationManager locationManager;
	private NOAA noaa;
	private double temperature = -1;
	private long startTime;
	private boolean showPressure;
	private boolean showAltitude;
	private boolean showGraph;
	private String pressureUnits;
	private String altitudeUnits;
	private String calibration;
	private static final double standardPressureAtSeaLevel = 1013.25;
	double zeroedPressure = standardPressureAtSeaLevel;
	double lastPressure = standardPressureAtSeaLevel;
	private String smoothing = "none";

	boolean haveLocationPermission() {
		if(true) return true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			return PackageManager.PERMISSION_GRANTED == checkSelfPermission("android.permission.ACCESS_FINE_LOCATION");
		}
		else {
			return true;
		}
	}

	boolean haveConnectPermission() {
		if (Build.VERSION.SDK_INT >= 31) {
			return PackageManager.PERMISSION_GRANTED == checkSelfPermission("android.permission.BLUETOOTH_CONNECT");
		}
		else {
			return true;
		}
	}

	boolean haveAllPermissions() {
		return haveLocationPermission();
	}

	@Override
	public void onRequestPermissionsResult (int requestCode,
											String[] permissions,
											int[] grantResults) {
		boolean haveAll = true;
		for (int i=0; i<permissions.length; i++) {
			if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					finishAffinity();
				} else {
					finish();
				}
			}
		}
	}
	public boolean assurePermissions() {
		if (true) return true;

		boolean l = haveLocationPermission();
		if (!l) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				ArrayList<String> permissions = new ArrayList<>();
				if (!l)
					permissions.add("android.permission.ACCESS_FINE_LOCATION");
				String[] pp = new String[permissions.size()];
				permissions.toArray(pp);
				for (String p : pp)
					Log.v("GiantBarometer", "requesting permissions "+p);
				requestPermissions(pp, 0);
				return false;
			}
		}

		getActionBar().setTitle("Giant Barometer");
		return true;
	}

	@SuppressLint("MissingPermission")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		options = PreferenceManager.getDefaultSharedPreferences(this);

		noaa = new NOAA();

		if (! getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)) {
			Toast.makeText(this, "Device has no barometer", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		if (!assurePermissions())
			return;

		setContentView(R.layout.heart);
		getActionBar().hide();
		if (options.getBoolean(Options.PREF_SCREEN_ON, false))
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		pressureText = (BigTextView) findViewById(R.id.pressure);
		altitudeText = (BigTextView) findViewById(R.id.altitude);
		graphView = (GraphView) findViewById(R.id.graph);
		works = false;
		toolbarView =findViewById(R.id.toolbar);
		buttonHideHandler = new Handler();
//		showButtons();
		altitudeData.clear();
		recentPressures.clear();

		sensorManager = (SensorManager) getSystemService(Service.SENSOR_SERVICE);
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE),
				SensorManager.SENSOR_DELAY_NORMAL);

//		timeoutHandler = new Handler();
//		periodicTimeoutRunnable = new Runnable() {
//			@Override
//			public void run() {
//				pressureText.setText(" ? ");
//				timeoutHandler.postDelayed(periodicTimeoutRunnable, periodicTimeout);
//			}
//		};
		/*
		standardPressureHandler = new Handler();
		standardPressureTimeoutRunnable = new Runnable() {
			@Override
			public void run() {
				AsyncTask.execute(new Runnable() {
					@Override
					public void run() {
						updateStandardPressure();
					}
				});
				standardPressureHandler.postDelayed(standardPressureTimeoutRunnable, standardPressureTimeout);
			}
		};
		standardPressureHandler.postDelayed(standardPressureTimeoutRunnable, 0); */

	}

	private void updateStandardPressure(Location location) {
		long t = System.currentTimeMillis();
//		Log.v("GiantBarometer", ""+location+" "+lastPressureUpdate+" "+t);

		if (lastPressureUpdate < 0 || t >= lastPressureUpdate + standardPressureTimeout) {
//			Log.v("GiantBarometer", "updating");
			lastPressureUpdate = System.currentTimeMillis();
			AsyncTask.execute(new Runnable() {
				@Override
				public void run() {
					if (noaa.getData(location)) {
						setPressureAtSeaLevel(noaa.pressureAtSeaLevel);
						setTemperature(noaa.temperature);
					}
				}
			});
		}
		else Log.v("GiantBarometer", "skipping location update");

	}

	private synchronized void setPressureAtSeaLevel(double pressure) {
		pressureAtSeaLevel = pressure;
	}

	private synchronized void setTemperature(double t) {
		temperature = t;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		showButtons();
	}

	void showButtons() {
		toolbarView.setVisibility(View.VISIBLE);
		findViewById(R.id.resetGraph).setVisibility(showGraph ? View.VISIBLE : View.GONE);
		findViewById(R.id.zero).setVisibility(calibration.equals("relative") ? View.VISIBLE : View.GONE);
		if (!isTV()) {
			if (buttonHideRunnable == null)
				buttonHideRunnable = new Runnable() {
					@Override
					public void run() {
						toolbarView.setVisibility(View.GONE);
						buttonHideHandler.postDelayed(periodicTimeoutRunnable, buttonHideTime);
					}
				};
			buttonHideHandler.removeCallbacksAndMessages(null);
			buttonHideHandler.postDelayed(buttonHideRunnable, buttonHideTime);
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent me) {
		showButtons();
		return super.dispatchTouchEvent(me);
	}

	public boolean isTV() {
		if (Build.MODEL.startsWith("AFT")) {
			Application app = getApplication();
			String installerName = null;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ECLAIR) {
				installerName = app.getPackageManager().getInstallerPackageName(app.getPackageName());
			}
			if (installerName != null && installerName.equals("com.amazon.venezia"))
				return true;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
			return getPackageManager().hasSystemFeature("android.hardware.type.television");
		}
		else {
			return false;
		}
	}

	void setOrientation() {
		if (isTV())
			return;
		String o = options.getString(Options.PREF_ORIENTATION, "automatic");
		try {
			if (o.equals("landscape"))
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			else if (o.equals("portrait"))
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			else {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
				}
				else {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				}
			}
		} catch(Exception e) {
			try {
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			}
			catch(Exception e2) {}
		}
	}

	protected void setFullScreen() {
		boolean fs = options.getBoolean(Options.PREF_FULLSCREEN, true);
		Window w = getWindow();
		WindowManager.LayoutParams attrs = w.getAttributes();

		if (fs) {
			attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
		} else {
			attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
		}

		w.setAttributes(attrs);

		View dv = w.getDecorView();

		if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
			dv.setSystemUiVisibility(fs ? View.GONE : View.VISIBLE);
		} else if (Build.VERSION.SDK_INT >= 19) {
			int flags = dv.getSystemUiVisibility();
			if (fs) {
				flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
			}
			else {
				flags &= ~(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
			}
			dv.setSystemUiVisibility(flags);
		}
	}

    public void showValue(long tMillis, double pressure) {
		if (pressure > 0) {
			TimedDatum datum = new TimedDatum(tMillis, pressure);
			lastPressure = pressure;
			recentPressures.add(datum);
			if (showPressure)
				pressureText.setText(formatPressure(pressure));
			if (!calibration.equals("nws") || pressureAtSeaLevel >= 0 || System.currentTimeMillis() > startTime+10000) {
				double alt;
				if (smoothing.equals("none"))
					alt = calculateAltitude(pressure);
				else
					alt = calculateAltitude(recentPressures.smooth(smoothing));
				if (showAltitude)
					altitudeText.setText(formatAltitude(alt));
				altitudeData.add(new TimedDatum(tMillis, alt));
				if (showGraph)
					graphView.setData(altitudeData, true);
			}
//			Log.v("GiantBarometer", ""+pressure+" "+alt);
		}
		else {
			pressureText.setText(" ? ");
			altitudeText.setText(" ? ");
		}
    }

	private String formatAltitude(double alt) {
		if (altitudeUnits.equals("meters"))
			return String.format("%.1f", alt);
		else if (altitudeUnits.equals("feet"))
			return String.format("%.0f", alt * 3.280839895);
		else
			return " ? ";
	}

	private String formatPressure(double pressure) {
		if (pressureUnits.equals("hPa"))
			return String.format("%.1f", pressure);
		else if (pressureUnits.equals("kPa"))
			return String.format("%.2f", pressure/10.);
		else if (pressureUnits.equals("inHg"))
			return String.format("%.1f", pressure/1.33322387415);
		else
			return " ? ";
	}

	private synchronized double calculateAltitude(double pressure) {
		double p0 = (!calibration.equals("nws") || pressureAtSeaLevel < 0) ? standardPressureAtSeaLevel : pressureAtSeaLevel;

		// todo: temperature
		// https://physics.stackexchange.com/questions/333475/how-to-calculate-altitude-from-current-temperature-and-pressure
		double alt = 44330 * (1 - Math.pow(pressure/p0, 1./5.255));
		if (calibration.equals("relative")) {
			alt -= 44330 * (1 - Math.pow(zeroedPressure/p0, 1./5.255));
			Log.v("GiantBarometer", ""+zeroedPressure+" "+alt);
		}
		return alt;

//		return (Math.pow(p0/pressure,1/5.257)-1)*temperature / 0.0065;
	}


	public void onDataReceived(long tMillis, double distance) {
		if (distance >= 0) {
			lastValidTime = System.currentTimeMillis();
//			timeoutHandler.removeCallbacksAndMessages(null);
//			timeoutHandler.postDelayed(periodicTimeoutRunnable, periodicTimeout);
			showValue(tMillis, distance);
		}
		else if (System.currentTimeMillis() > lastValidTime + WAIT_TIME) {
			showValue(0,-1);
		}
	}


	private void setColor(int back, int fore) {
		pressureText.setBackColor(back);
		pressureText.setTextColor(fore);
	}

	@Override
	public void onBackPressed() {
//		updateCache(false);
		super.onBackPressed();
	}

	@Override
	public void onPause() {
		super.onPause();

		if (calibration.equals("relative"))
			options.edit().putFloat(Options.PREF_ZEROED_PRESSURE, (float) zeroedPressure).apply();
		if (locationListener != null) {
			locationManager.removeUpdates(locationListener);
			locationListener = null;
		}
//		timeoutHandler.removeCallbacksAndMessages(null);
	}

	@SuppressLint("MissingPermission")
	@Override
	public void onResume() {
		super.onResume();

		if (!haveAllPermissions())
			return;

		zeroedPressure = options.getFloat(Options.PREF_ZEROED_PRESSURE, (float) standardPressureAtSeaLevel);
		showPressure = options.getBoolean(Options.PREF_SHOW_PRESSURE, true);
		showAltitude = options.getBoolean(Options.PREF_SHOW_ALTITUDE, true);
		showGraph = options.getBoolean(Options.PREF_SHOW_GRAPH, true);

		boolean invalidateData = false;
		String s = options.getString(Options.PREF_PRESSURE_UNITS, "hPa");
		if (!s.equals(pressureUnits)) {
			invalidateData = true;
			pressureUnits = s;
		}
		s = options.getString(Options.PREF_ALTITUDE_UNITS, "meters");
		if (!s.equals(altitudeUnits)) {
			invalidateData = true;
			altitudeUnits = s;
		}
		s = options.getString(Options.PREF_CALIBRATION, "nws");
		if (!s.equals(calibration)) {
			invalidateData = true;
			calibration = s;
		}
		if (invalidateData) {
			altitudeData.clear();
		}
		smoothing = options.getString(Options.PREF_SMOOTHING, "med2000");

		pressureText.setVisibility(showPressure ? View.VISIBLE : View.GONE);
		altitudeText.setVisibility(showAltitude ? View.VISIBLE : View.GONE);
		graphView.setVisibility(showGraph ? View.VISIBLE : View.GONE);

		setOrientation();
		setFullScreen();
		showButtons();

//		timeoutHandler.removeCallbacksAndMessages(null);
//		timeoutHandler.postDelayed(new Runnable() {
//			@Override
//			public void run() {
////				updateCache(false);
//				Log.e("hrshow", "cannot connect");
//				finish();
//			}
//		}, initialTimeout);
        showValue(0,-1);
        lastValidTime = -WAIT_TIME;
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		startTime = System.currentTimeMillis();
		if (calibration.equals("nws")) {
			locationListener = new LocationListener() {
				@Override
				public void onLocationChanged(Location location) {
					updateStandardPressure(location);
				}
			};
			updateStandardPressure(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
					standardPressureTimeout / 4,
					500, locationListener);
		}
		else {
			locationListener = null;
		}
	}

	public void onSettingsClick(View view) {
//		updateCache(false);
		final Intent i = new Intent();
		i.setClass(this, Options.class);
		startActivity(i);
	}

    public void onResetGraph(View view) {
		altitudeData.clear();
		graphView.setData(altitudeData, true);
//		updateCache(false);
//		finish();
//		final Intent i = new Intent();
//		i.setClass(this, DeviceScanActivity.class);
//		startActivity(i);
    }

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
			float v = event.values[0];
			if (v >= 0)
				onDataReceived(System.currentTimeMillis(), v);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	public void onZero(View view) {
		zeroedPressure = lastPressure;
		onResetGraph(view);
	}


	static final class RecentData {
		LinkedList<TimedDatum> recent = new LinkedList<>(); // kept sorted by value for medians
		long timeToKeep = 100*1000;

		RecentData(long keep) {
			timeToKeep = keep;
		}

		void clear() {
			recent.clear();
		}

		TimedDatum latest() {
			if (recent.isEmpty())
				return null;

			long latest = Long.MIN_VALUE;
			TimedDatum best = null;
			ListIterator<TimedDatum> iterator = recent.listIterator();
			while (iterator.hasNext()) {
				TimedDatum node = iterator.next();
				if (node.time > latest) {
					best = node;
					latest = node.time;
				}
			}
			return best;
		}

		void add(TimedDatum datum) {
			long now = datum.time;
			ListIterator<TimedDatum> iterator = recent.listIterator();
			long cutoff = now - timeToKeep;
			boolean added = false;
			while (iterator.hasNext()) {
				TimedDatum node = iterator.next();
				if (node.time < cutoff)
					iterator.remove();
				else if (!added && datum.value <= node.value) {
					iterator.previous();
					iterator.add(datum);
					added = true;
					break;
				}
			}
			if (!added)
				iterator.add(datum);
		}

		public double smooth(String smoothing) {
			TimedDatum latest = latest();
			if (latest == null)
				return Double.NaN;
			if (smoothing.startsWith("med")) {
				long cutoff = latest.time - Long.parseLong(smoothing.substring(3));
				ArrayList<Double> selected = new ArrayList<>();
				for (TimedDatum datum : recent) {
					if (datum.time >= cutoff)
						selected.add(datum.value);
				}
				int n = selected.size();
				if (n % 2 != 0) {
					return selected.get(n/2);
				}
				else {
					return (selected.get(n/2-1) + selected.get(n/2)) / 2.;
				}
			}
			else if (smoothing.startsWith("avg")) {
				long cutoff = latest.time - Long.parseLong(smoothing.substring(3));
				double sum = 0;
				long count = 0;
				ListIterator<TimedDatum> iterator = recent.listIterator();
				while (iterator.hasNext()) {
					TimedDatum node = iterator.next();
					if (node.time >= cutoff) {
						sum += node.value;
						count ++;
					}
				}
				return sum/count;
			}
			else {
				return latest.value;
			}
		}
	}

	static final class TimedDatum {
		long time;

		double value;

		public TimedDatum(long t, double y) {
			time = t;
			value = y;
		}

	}
 }

