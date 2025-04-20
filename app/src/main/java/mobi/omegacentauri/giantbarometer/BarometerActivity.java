package mobi.omegacentauri.giantbarometer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class BarometerActivity extends Activity {
	private final static String TAG = "GiantBarometer:activity";
    private static final long WAIT_TIME = 6000; // only show invalid value after this amount of waiting
	double pressureAtSeaLevel = -1;
	private long lastLapCountTime = -1;
	private static final long LAP_COUNT_TIME = 2000;
	private long dataTimeSpacing = 500;
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

	ArrayList<Analysis.TimedDatum<Double>> altitudeData = new ArrayList<>();
	List<Analysis.TimedDatum<Double>> pressureData = new ArrayList<>();
	List<Analysis.TimedDatum<Double>> smoothedPressureData = new ArrayList<>();
//	List<Analysis.TimedDatum<Observations>> observations = new ArrayList<>();
	Analysis.RecentData recentPressures = new Analysis.RecentData(maximumKeep);
	private GraphView altGraphView;
	private GraphView pressureGraphView;
	private SharedPreferences options;
	private double temperature = -1;
	private long startTime;
	private boolean showPressure;
	private boolean showAltitude;
	private boolean showAltGraph;
	private String pressureUnits;
	private String altitudeUnits;
	private String calibration;
	public static final double standardPressureAtSeaLevel = 1013.25;
	double lastPressure = standardPressureAtSeaLevel;
	private String smoothing = "none";
	private WeatherInfo lastStationData = null;
	private boolean showLapCount;
	private BigTextView lapCountText;
	private boolean barometerService;

	boolean ready = false;
	private boolean initialized = false;
	private long lastLocationTime = Long.MIN_VALUE;

	public static final String locationPermission = "android.permission.ACCESS_COARSE_LOCATION";//"android.permission.ACCESS_FINE_LOCATION";

	private boolean background = false;
	private CheckBox backgroundCheckbox;
	private boolean gpsAltitude = false;
	private double zeroedAlt = 0;
	private String lapMode;
	private long dataStartTime = -1;
	private boolean showPressureGraph;

	boolean haveLocationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			return PackageManager.PERMISSION_GRANTED == checkSelfPermission(locationPermission);
		}
		else {
			return true;
		}
	}

	boolean haveAllPermissions() {
		return haveLocationPermission();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
											String[] permissions,
											int[] grantResults) {
		boolean haveAll = true;
		Log.v("GiantBarometer", "permissions request result");
		for (int i=0; i<permissions.length; i++) {
			if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
				haveAll = false;
				Log.e("GiantBarometer", "permissions denied");
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
					finishAffinity();
				} else {
					finish();
				}
			}
		}
	}
	public boolean assurePermissions() {
//		if (true) return true;

		boolean l = haveLocationPermission();
		if (!l) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				ArrayList<String> permissions = new ArrayList<>();
				if (!l)
					permissions.add(locationPermission);
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

	void checkBackgroundPermissions() {
		PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
		String packageName = getPackageName();
		if (pm.isIgnoringBatteryOptimizations(packageName) ||
			options.getBoolean(Options.PREF_DONT_ASK_BATTERY, false))
			return;
		AlertDialog.Builder ab = new AlertDialog.Builder(this);
		ab.setTitle("Disable battery optimization");
		ab.setMessage("To gather data reliably in the background, you should turn off battery optimization for this app.");
		ab.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Intent intent = new Intent();
				intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
				intent.setData(Uri.parse("package:" + packageName));
				startActivity(intent);
			}
		});
		ab.setCancelable(true);
		ab.setNegativeButton("Never", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				options.edit().putBoolean(Options.PREF_DONT_ASK_BATTERY, true).apply();
				finish();
			}
		});
		ab.create().show();
	}

	@SuppressLint("MissingPermission")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		options = PreferenceManager.getDefaultSharedPreferences(this);

		setContentView(R.layout.barometer);
		getActionBar().hide();
		if (options.getBoolean(Options.PREF_SCREEN_ON, false))
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		pressureText = (BigTextView) findViewById(R.id.pressure);
		altitudeText = (BigTextView) findViewById(R.id.altitude);
		lapCountText = (BigTextView) findViewById(R.id.laps);
		altGraphView = (GraphView) findViewById(R.id.alt_graph);
		pressureGraphView = (GraphView) findViewById(R.id.pressure_graph);
		backgroundCheckbox = (CheckBox) findViewById(R.id.background);
		backgroundCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Log.v("GiantBarometer", "checkbox "+isChecked);
				background = isChecked;
				if (background)
					checkBackgroundPermissions();
			}
		});
		works = false;
//		toolbarView =findViewById(R.id.toolbar);
		buttonHideHandler = new Handler();
//		showButtons();
		clearData();
	}

//	@SuppressLint("MissingPermission")
//	private void requestLocation() {
//		Log.v("GiantBarometer", "request");
//		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
//				standardPressureTimeout / 3,
//				500, this);
//	}

	public synchronized void setStationData(WeatherInfo winfo) {
		if (winfo == null || winfo == lastStationData)
			return;
		if (lastStationData != null && lastStationData.time > winfo.time)
			return;

		Log.v(TAG, "changing station data");

		// add linear interpolation between previous station data and new station data,
		// or if this is the first calibration datum, just fill in the missing
		// altitude data.
		long previousStationTime = lastStationData == null ? Long.MIN_VALUE : lastStationData.time;
		if (previousStationTime < winfo.time) {
			double range = winfo.time - previousStationTime;
			for (int i = 0; i < altitudeData.size(); i++) {
				if (altitudeData.get(i).time > previousStationTime) {
					Log.v(TAG, "deleting old altitude data");
					altitudeData.subList(i, altitudeData.size()).clear();
					break;
				}
			}
			for (Analysis.TimedDatum<Double> pdata : smoothedPressureData) {
				if (pdata.time > previousStationTime) {
					double calibrationPressure;
					double calibrationTemperature;
					if (lastStationData == null || pdata.time >= winfo.time) {
						calibrationPressure = winfo.pressureAtSeaLevel;
						calibrationTemperature = winfo.temperature;
					} else {
						double t = (pdata.time - previousStationTime) / range;
						calibrationPressure = lastStationData.pressureAtSeaLevel * (1-t) + winfo.pressureAtSeaLevel * t;
						calibrationTemperature = lastStationData.temperature * (1-t) + winfo.temperature * t;
					}
					double alt = calculateAltitude(pdata.value, calibrationPressure, calibrationTemperature);
					altitudeData.add(new Analysis.TimedDatum(pdata.time, alt));
				}
			}
		}

		pressureAtSeaLevel = winfo.pressureAtSeaLevel;
		temperature = winfo.temperature;
		lastStationData = winfo;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		showButtons();
	}

	void showButtons() {
//		toolbarView.setVisibility(View.VISIBLE);
		findViewById(R.id.resetGraph).setVisibility((showAltGraph || showLapCount || showPressureGraph) ? View.VISIBLE : View.GONE);
		findViewById(R.id.zero).setVisibility(calibration.equals("relative") ? View.VISIBLE : View.GONE);
//		if (!isTV()) {
//			if (buttonHideRunnable == null)
//				buttonHideRunnable = new Runnable() {
//					@Override
//					public void run() {
//						toolbarView.setVisibility(View.GONE);
//						buttonHideHandler.postDelayed(periodicTimeoutRunnable, buttonHideTime);
//					}
//				};
//			buttonHideHandler.removeCallbacksAndMessages(null);
//			buttonHideHandler.postDelayed(buttonHideRunnable, buttonHideTime);
//		}
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
		boolean fs = options.getBoolean(Options.PREF_FULLSCREEN, false);
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

	public void addObservations(List<Analysis.TimedDatum<Observations>> os) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_addObservations(os);
			}
		});
	}
	public void _addObservations(List<Analysis.TimedDatum<Observations>> os) {
		if (os.isEmpty())
			return;
		double lastAltitude = Double.NaN;
		long systemTime = System.currentTimeMillis();
		for (Analysis.TimedDatum<Observations> o: os) {
			if (! Double.isNaN(o.value.pressure)) {
				lastPressure = o.value.pressure;
				Analysis.TimedDatum<Double> datum = new Analysis.TimedDatum<>(o.time, o.value.pressure);
				recentPressures.add(datum);
			}
//			observations.add(o);
			int n = pressureData.size();
			if (o.value.stationData != null) {
				setStationData(o.value.stationData);
			}
			if ((n == 0 || pressureData.get(n - 1).time + dataTimeSpacing <= o.time)) {
				if (! Double.isNaN(o.value.pressure)) {
					pressureData.add(new Analysis.TimedDatum<Double>(o.time, o.value.pressure));
					double smoothedPressure;
					if (smoothing.equals("none")) {
						smoothedPressure = o.value.pressure;
					}
					else {
						smoothedPressure = recentPressures.smooth(smoothing);
					}
					smoothedPressureData.add(new Analysis.TimedDatum<Double>(o.time, smoothedPressure));
					if (!(calibration.equals("nws") || calibration.equals("om")) || pressureAtSeaLevel >= 0 || systemTime > startTime + 10000)
					{
						boolean haveAltitude = false;
						if (!Double.isNaN(o.value.altitude)) {
							lastAltitude = o.value.altitude;
							haveAltitude = true;
						} else if (!gpsAltitude && !Double.isNaN(o.value.pressure)) {
							lastAltitude = calculateAltitude(smoothedPressure);
							haveAltitude = true;
						}
						if (haveAltitude)
							altitudeData.add(new Analysis.TimedDatum(o.time, lastAltitude));
					}
				}
			}
		}
		if (showPressure)
			pressureText.setText(formatPressure(lastPressure));
		if (showAltitude && !Double.isNaN(lastAltitude))
			altitudeText.setText(formatAltitude(lastAltitude));
		updateAltitudeGraph();
		updatePressureGraph();
		if (showLapCount && lastLapCountTime + LAP_COUNT_TIME <= systemTime) {
			lapCountText.setText("" + (new Analysis(altitudeData).countLaps(lapMode)));
			lastLapCountTime = systemTime;
		}
	}

	private void updatePressureGraph() {
		if (showPressureGraph) {
			pressureGraphView.setData(pressureData, getDataStartTime(), getDataEndTime(), true);
		}
	}

	private double getDataStartTime() {
		double t;
		int n = pressureData.size();
		if (n>0)
			t = pressureData.get(0).time;
		else
			t = 1.e20;
		n = altitudeData.size();
		if (n>0)
			t = Math.min(altitudeData.get(0).time, t);
		return t;
	}
	private double getDataEndTime() {
		double t;
		int n = pressureData.size();
		if (n>0)
			t = pressureData.get(n-1).time;
		else
			t = 0;
		n = altitudeData.size();
		if (n>0)
			t = Math.max(altitudeData.get(n-1).time, t);
		return t;
	}

	private void updateAltitudeGraph() {
		if (showAltGraph) {
			altGraphView.setData(altitudeData, getDataStartTime(), getDataEndTime(), true);
		}
	}

	public void showInvalidValue() {
		pressureText.setText(" ? ");
		altitudeText.setText(" ? ");
    }

	private double parseAltitude(String s) {
		double a = Double.parseDouble(s);
		if (altitudeUnits.equals("meters"))
			return a;
		else
			return a / 3.280839895;

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

	private double calculatePressureWithoutTemperature(double pressure, double pressureAtSeaLevel) {
		return  44330 * (1 - Math.pow(pressure/pressureAtSeaLevel, 1./5.255));
	}

	private double calculateAltitude(double pressure, double p0, double temperature) {
		// https://physics.stackexchange.com/questions/333475/how-to-calculate-altitude-from-current-temperature-and-pressure
		if (calibration.equals("nws"))
			return (Math.pow(p0/pressure,1/5.257)-1)*temperature / 0.0065;
		else
			return calculatePressureWithoutTemperature(pressure, p0);
	}

	private synchronized double calculateAltitude(double pressure) {
		double p0 = (!(calibration.equals("nws") || calibration.equals("om")) || pressureAtSeaLevel < 0) ? standardPressureAtSeaLevel : pressureAtSeaLevel;

		double alt = calculateAltitude(pressure, p0, temperature);
		if (calibration.equals("relative")) {
			return alt - zeroedAlt;
		}
		return alt;
	}


	@Override
	public void onBackPressed() {
//		updateCache(false);
		super.onBackPressed();
	}

	@Override
	public void onPause() {
		super.onPause();

		Log.v("GiantBarometer", "onpause");

		BarometerService.activity = null;
		if (!background)
			stopBarometerService();

		if (!initialized)
			return;

		if (calibration.equals("relative"))
			options.edit().putFloat(Options.PREF_ZEROED_ALTITUDE, (float)zeroedAlt).apply();
	}

	private void stopBarometerService() {
		BarometerService.activity = null;
		BarometerService.self = null;
		Log.v("GiantBarometer", "stopping service");
		Intent i = new Intent(this, BarometerService.class);
		stopService(i);
	}

	@SuppressLint("MissingPermission")
	@Override
	public void onResume() {
		super.onResume();

		initialized = false;

		if (! getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER)) {
			Toast.makeText(this, "Device has no barometer", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		if (!assurePermissions()) {
			return;
		}


		if (!haveAllPermissions()) {
			Log.v("GiantBarometer", "missing permissions");
//			finish();
			return;
		}

		findViewById(R.id.zero).setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				return onLongZero(v);
			}
		});

		Log.v("GiantBarometer", "resuming with permissions");

		zeroedAlt = options.getFloat(Options.PREF_ZEROED_ALTITUDE, (float) standardPressureAtSeaLevel);
		showPressure = options.getBoolean(Options.PREF_SHOW_PRESSURE, true);
		showAltitude = options.getBoolean(Options.PREF_SHOW_ALTITUDE, true);
		showAltGraph = options.getBoolean(Options.PREF_SHOW_ALT_GRAPH, true);
		showPressureGraph = options.getBoolean(Options.PREF_SHOW_PRESSURE_GRAPH, false);
		showLapCount = options.getBoolean(Options.PREF_LAP_COUNT, false);
		lapMode = options.getString(Options.PREF_LAP_MODE, "pairs");
		gpsAltitude = locationPermission.contains("FINE") && options.getBoolean(Options.PREF_GPS_ALTITUDE, false);
		Log.v(TAG, "gpsAltitude "+gpsAltitude);

		boolean invalidateData = false;
		String s = options.getString(Options.PREF_PRESSURE_UNITS, "hPa");
		if (!s.equals(pressureUnits)) {
			invalidateData = true;
			pressureUnits = s;
		}
		altitudeUnits = options.getString(Options.PREF_ALTITUDE_UNITS, "meters");
		if (altitudeUnits.equals("meters"))
			altGraphView.setValueScale(1.);
		else // feet
			altGraphView.setValueScale(1./3.280839895);

		s = Options.getCalibration(this, options);
		if (!s.equals(calibration)) {
			invalidateData = true;
			calibration = s;
		}
		if (invalidateData) {
			clearData();
			stopBarometerService();
		}

		smoothing = options.getString(Options.PREF_SMOOTHING, "med2000");

		pressureText.setVisibility(showPressure ? View.VISIBLE : View.GONE);
		altitudeText.setVisibility(showAltitude ? View.VISIBLE : View.GONE);
		lapCountText.setVisibility(showLapCount ? View.VISIBLE : View.GONE);
		altGraphView.setVisibility(showAltGraph ? View.VISIBLE : View.GONE);
		pressureGraphView.setVisibility(showPressureGraph ? View.VISIBLE : View.GONE);

		setOrientation();
		setFullScreen();
		showButtons();

        showInvalidValue();

		// normal: 200ms foreground

		lastValidTime = -WAIT_TIME;
		startTime = System.currentTimeMillis();

//		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//		if (calibration.equals("nws")) {
//			updateStandardPressure(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
//			requestLocation();
//		}

		if (BarometerService.self == null) {
			Log.v("GiantBarometer", "starting service");
			Intent i = new Intent(this, BarometerService.class);
			if ( Build.VERSION.SDK_INT >= 26) {
				startForegroundService(i);
			}
			else {
				startService(i);
			}
			barometerService = true;
			background = false;
		}
		else {
			background = true;
		}
		backgroundCheckbox.setChecked(background);
		BarometerService.activity = this;

		Log.v("GiantBarometer", "running");
		initialized = true;
	}

	private void clearData() {
		pressureData.clear();
		altitudeData.clear();
		smoothedPressureData.clear();
		recentPressures.clear();
		dataStartTime = System.currentTimeMillis();
	}

	public void onSettingsClick(View view) {
		final Intent i = new Intent();
		i.setClass(this, Options.class);
		startActivity(i);
	}

    public void onResetGraph(View view) {
		clearData();
		updateAltitudeGraph();
		updatePressureGraph();
		dataStartTime = System.currentTimeMillis();
    }


	public boolean onLongZero(View view) {
		AlertDialog.Builder ab = new AlertDialog.Builder(this);
		ab.setTitle("Current altitude ("+altitudeUnits+")");
		final double alt = calculateAltitude(lastPressure);
		String s = String.format("%.1f", alt);
		final EditText value = new EditText(this);
		value.setText(s);
		ab.setView(value);
		ab.setCancelable(true);
		ab.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				try {
					zeroedAlt = zeroedAlt - ( parseAltitude(value.getText().toString()) - alt );
				}
				catch (Exception e) {
					Toast.makeText(BarometerActivity.this, "Invalid number", Toast.LENGTH_LONG).show();
				}
			}
		});
		ab.create().show();
		value.setFocusableInTouchMode(true);
		value.setFocusable(true);
		value.setFocusedByDefault(true);
		value.selectAll();
		return true;
	}
	public void onZero(View view) {
		zeroedAlt =  calculatePressureWithoutTemperature(lastPressure, standardPressureAtSeaLevel);
		onResetGraph(view);
	}

//	@Override
//	public void onLocationChanged(Location location) {
//		Log.v(TAG, "location changed");
//		lastLocationTime = System.currentTimeMillis();
//		locationManager.removeUpdates(this);
//		updateStandardPressure(location);
//	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopBarometerService();
	}



	static final class Observations {
		double pressure;
		double altitude;
		WeatherInfo stationData;

		Observations(double _pressure, WeatherInfo _stationData) {
			pressure = _pressure;
			altitude = Double.NaN;
			stationData = _stationData;
		}

		Observations(double _altitude) {
			altitude = _altitude;
			pressure = Double.NaN;
			stationData = null;
		}
	}

}

