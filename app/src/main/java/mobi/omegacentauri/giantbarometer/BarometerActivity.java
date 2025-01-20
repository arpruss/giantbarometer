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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class BarometerActivity extends Activity {
	private final static String TAG = "GiantBarometer:activity";

    private static final long WAIT_TIME = 6000; // only show invalid value after this amount of waiting
	double pressureAtSeaLevel = -1;
	private long lastLapCountTime = -1;
	private static final long LAP_COUNT_TIME = 2000;
	private long altitudeTimeSpacing = 500;
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

	List<Analysis.TimedDatum<Double>> altitudeData = new ArrayList<>();
//	List<Analysis.TimedDatum<Observations>> observations = new ArrayList<>();
	Analysis.RecentData recentPressures = new Analysis.RecentData(maximumKeep);
	private GraphView graphView;
	private SharedPreferences options;
	private double temperature = -1;
	private long startTime;
	private boolean showPressure;
	private boolean showAltitude;
	private boolean showGraph;
	private String pressureUnits;
	private String altitudeUnits;
	private String calibration;
	public static final double standardPressureAtSeaLevel = 1013.25;
	double zeroedPressure = standardPressureAtSeaLevel;
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
		graphView = (GraphView) findViewById(R.id.graph);
		backgroundCheckbox = (CheckBox) findViewById(R.id.background);
		backgroundCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				Log.v("GiantBarometer", "checkbox "+isChecked);
				background = isChecked;
			}
		});
		works = false;
//		toolbarView =findViewById(R.id.toolbar);
		buttonHideHandler = new Handler();
//		showButtons();
		altitudeData.clear();
		recentPressures.clear();
	}

//	@SuppressLint("MissingPermission")
//	private void requestLocation() {
//		Log.v("GiantBarometer", "request");
//		locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
//				standardPressureTimeout / 3,
//				500, this);
//	}

	public synchronized void setStationData(WeatherInfo winfo) {
		if (winfo == null)
			return;
		lastStationData = winfo;
		pressureAtSeaLevel = winfo.pressureAtSeaLevel;
		temperature = winfo.temperature;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		showButtons();
	}

	void showButtons() {
//		toolbarView.setVisibility(View.VISIBLE);
		findViewById(R.id.resetGraph).setVisibility(showGraph ? View.VISIBLE : View.GONE);
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
		boolean updateAltitudes = false;
		double lastAltitude = Double.NaN;
		long systemTime = System.currentTimeMillis();
		for (Analysis.TimedDatum<Observations> o: os) {
			if (! Double.isNaN(o.value.pressure)) {
				lastPressure = o.value.pressure;
				Analysis.TimedDatum<Double> datum = new Analysis.TimedDatum<>(o.time, o.value.pressure);
				recentPressures.add(datum);
			}
//			observations.add(o);
			int n = altitudeData.size();
			if (o.value.stationData != null)
				setStationData(o.value.stationData);
			if ((n == 0 || altitudeData.get(n - 1).time + altitudeTimeSpacing <= o.time) &&
					(!(calibration.equals("nws") || calibration.equals("om")) || pressureAtSeaLevel >= 0 || systemTime > startTime + 10000)) {
				boolean haveAltitude = false;
				if (! Double.isNaN(o.value.altitude)) {
					lastAltitude = o.value.altitude;
					haveAltitude = true;
				}
				else if (! gpsAltitude && ! Double.isNaN(o.value.pressure)) {
					if (smoothing.equals("none"))
						lastAltitude = calculateAltitude(o.value.pressure);
					else
						lastAltitude = calculateAltitude(recentPressures.smooth(smoothing));
					haveAltitude = true;
				}
				if (haveAltitude)
					altitudeData.add(new Analysis.TimedDatum(o.time, lastAltitude));
			}
		}
		if (showPressure)
			pressureText.setText(formatPressure(lastPressure));
		if (showAltitude && !Double.isNaN(lastAltitude))
			altitudeText.setText(formatAltitude(lastAltitude));
		if (showGraph)
			graphView.setData(altitudeData, true);
		if (showLapCount && lastLapCountTime + LAP_COUNT_TIME <= systemTime) {
			lapCountText.setText("" + (new Analysis(altitudeData).countLaps()));
			lastLapCountTime = systemTime;
		}
	}

	public void showValue(long tMillis, double pressure) {
		if (pressure > 0) {
			Analysis.TimedDatum datum = new Analysis.TimedDatum(tMillis, pressure);
			lastPressure = pressure;
			recentPressures.add(datum);
//			observations.add(new Analysis.TimedDatum<Observations>(tMillis, new Observations(pressure, lastStationData)));
			if (showPressure)
				pressureText.setText(formatPressure(pressure));
			int n = altitudeData.size();
			if ((n == 0 || altitudeData.get(n-1).time + altitudeTimeSpacing <= tMillis) &&
					(!(calibration.equals("nws") || calibration.equals("om")) || pressureAtSeaLevel >= 0 || System.currentTimeMillis() > startTime+10000)) {
				double alt;
				if (smoothing.equals("none"))
					alt = calculateAltitude(pressure);
				else
					alt = calculateAltitude(recentPressures.smooth(smoothing));
				if (showAltitude)
					altitudeText.setText(formatAltitude(alt));
				altitudeData.add(new Analysis.TimedDatum(tMillis, alt));
				if (showGraph)
					graphView.setData(altitudeData, true);
			}
			if (showLapCount && lastLapCountTime + LAP_COUNT_TIME <= tMillis) {
				lapCountText.setText(""+(new Analysis(altitudeData).countLaps()));
				lastLapCountTime = tMillis;

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
		double p0 = (!(calibration.equals("nws") || calibration.equals("om")) || pressureAtSeaLevel < 0) ? standardPressureAtSeaLevel : pressureAtSeaLevel;

		// todo: temperature
		// https://physics.stackexchange.com/questions/333475/how-to-calculate-altitude-from-current-temperature-and-pressure
		double alt;
		if (calibration.equals("nws"))
			alt = (Math.pow(p0/pressure,1/5.257)-1)*temperature / 0.0065;
		else
			alt = 44330 * (1 - Math.pow(pressure/p0, 1./5.255));
		if (calibration.equals("relative")) {
			alt -= 44330 * (1 - Math.pow(zeroedPressure/p0, 1./5.255));
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

//		try {
//			locationManager.removeUpdates(this);
//		} catch (Exception e) {
//		}
		if (calibration.equals("relative"))
			options.edit().putFloat(Options.PREF_ZEROED_PRESSURE, (float) zeroedPressure).apply();
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

		Log.v("GiantBarometer", "resuming with permissions");

		zeroedPressure = options.getFloat(Options.PREF_ZEROED_PRESSURE, (float) standardPressureAtSeaLevel);
		showPressure = options.getBoolean(Options.PREF_SHOW_PRESSURE, true);
		showAltitude = options.getBoolean(Options.PREF_SHOW_ALTITUDE, true);
		showGraph = options.getBoolean(Options.PREF_SHOW_GRAPH, true);
		showLapCount = options.getBoolean(Options.PREF_LAP_COUNT, false);
		calibration = Options.getCalibration(this, options );
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
			graphView.setValueScale(1.);
		else // feet
			graphView.setValueScale(1./3.280839895);

		s = Options.getCalibration(this, options);
		if (!s.equals(calibration)) {
			invalidateData = true;
			calibration = s;
		}
		if (invalidateData) {
			altitudeData.clear();
			stopBarometerService();
		}

		smoothing = options.getString(Options.PREF_SMOOTHING, "med2000");

		pressureText.setVisibility(showPressure ? View.VISIBLE : View.GONE);
		altitudeText.setVisibility(showAltitude ? View.VISIBLE : View.GONE);
		lapCountText.setVisibility(showLapCount ? View.VISIBLE : View.GONE);
		graphView.setVisibility(showGraph ? View.VISIBLE : View.GONE);

		setOrientation();
		setFullScreen();
		showButtons();

        showValue(0,-1);

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

	public void onSettingsClick(View view) {
		final Intent i = new Intent();
		i.setClass(this, Options.class);
		startActivity(i);
	}

    public void onResetGraph(View view) {
		altitudeData.clear();
//		observations.clear();
		graphView.setData(altitudeData, true);
    }


	public void onZero(View view) {
		zeroedPressure = lastPressure;
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

