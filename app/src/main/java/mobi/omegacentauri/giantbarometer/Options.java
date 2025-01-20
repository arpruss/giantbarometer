package mobi.omegacentauri.giantbarometer;

import mobi.omegacentauri.giantbarometer.R;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Year;
import java.util.Calendar;

public class Options extends PreferenceActivity {
    public static final String PREF_SCREEN_ON = "screenOn";
    public static final String PREF_ORIENTATION = "orientation";
    public static final String PREF_FULLSCREEN = "fullscreen";

    public static final String PREF_PRESSURE_UNITS = "pressureUnits";
    public static final String PREF_ALTITUDE_UNITS = "altitudeUnits";
    public static final String PREF_SHOW_PRESSURE = "showPressure";
    public static final String PREF_SHOW_ALTITUDE = "showAltitude";
    public static final String PREF_SHOW_GRAPH = "showGraph";
    public static final String PREF_ZEROED_ALTITUDE = "zeroedAlt";
    private static final String PREF_CALIBRATION = "calibration";
    public static final String PREF_SMOOTHING = "smoothing";
    public static final String PREF_LAP_COUNT = "lapCount";
    public static final String PREF_GPS_ALTITUDE = "gpsAltitude";
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private SharedPreferences options;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        options = PreferenceManager.getDefaultSharedPreferences(this);

        if (options.getString(Options.PREF_CALIBRATION, null) == null) {
            String locale = getResources().getConfiguration().locale.getCountry();
            if (locale.equals("US")) {
                options.edit().putString(Options.PREF_CALIBRATION, "nws").apply();
            }
            else {
                options.edit().putString(Options.PREF_CALIBRATION, "relative").apply();
            }
        }

        PreferenceManager.setDefaultValues(this, R.xml.options, true);

        listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
                customizeDisplay();
            }
        };

        addPreferencesFromResource(R.xml.options);
        customizeDisplay();

        Preference lb = (Preference) findPreference("license");
        lb.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showLicenses();

                return false;
            }
        });
    }

    static String getCalibration(Context c, SharedPreferences o) {
        String locale = c.getResources().getConfiguration().locale.getCountry();
        Log.v("GiantBarometer", "locale " + locale);
        return o.getString(PREF_CALIBRATION, locale.equals("US") ? "nws" : "om");
    }

    @Override
    public void onResume() {
        super.onResume();

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
        customizeDisplay();
    }

    static public String getAssetFile(Context context, String assetName) {
        try {
            return getStreamFile(context.getAssets().open(assetName));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            return "";
        }
    }

    static private String getStreamFile(InputStream stream) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(stream));

            String text = "";
            String line;
            while (null != (line=reader.readLine()))
                text = text + line;
            return text;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            return "";
        }
    }

    public void showLicenses() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Licenses and copyrights");
        alertDialog.setMessage(Html.fromHtml(getAssetFile(this, "license.html")));
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {} });
        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {} });
        alertDialog.show();
    }

    private void scanPreferences(PreferenceGroup group) {
        int n = group.getPreferenceCount();
        for (int i=0; i <n; i++) {
            Preference p = group.getPreference(i);
            if (p instanceof PreferenceGroup) {
                scanPreferences((PreferenceGroup)p);
            }
            else {
                if (p instanceof ListPreference) {
                    setSummary((ListPreference)p);
                }
            }
        }
    }

    private void customizeDisplay() {
        scanPreferences(getPreferenceScreen());
    }

    public void setSummary(ListPreference p) {
        try {
            p.setSummary(p.getEntry().toString().replace("%", "\uFF05")); // fullwidth percent symbol, won't be interpreted as formatting
        }
        catch(Exception e) {
            p.setSummary("");
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getScanCode() == 513 || event.getScanCode() == 595) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
                finish();
            }
            else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}
