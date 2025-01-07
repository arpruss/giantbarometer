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
    public static final String PREF_DEVICE_ADDRESS = "address";
    public static final String PREF_SERVICE = "service";
    public static final String PREF_BIRTH_YEAR = "birthYear";
    public static final String PREF_ZONE = "zoneDisplay";
    public static final String PREF_FORMULA = "formula";
    public static final String PREF_FORMULA_FOX = "fox";
    public static final String PREF_FORMULA_TANAKA = "tanaka";
    public static final String PREF_FORMULA_HUNT = "hunt";
    public static final String PREF_WARN_MAXIMUM = "warnMaximum";
    public static final String PREF_USE_ADVERTISED = "useAdvertised";
    public static final String PREF_TX_POWER = "txPower";
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private SharedPreferences options;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.setDefaultValues(this, R.xml.options, true);

        options = PreferenceManager.getDefaultSharedPreferences(this);

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
        boolean needMax = options.getBoolean(PREF_ZONE, false) || options.getBoolean(PREF_WARN_MAXIMUM, false);
        ListPreference birthYear = (ListPreference)findPreference(PREF_BIRTH_YEAR);

        int now = Calendar.getInstance().get(Calendar.YEAR);
        CharSequence years[] = new CharSequence[120];
        for (int i = 0 ; i < 120 ; i++) {
            years[i] = "" + (now - 119 + i);
        }
        birthYear.setEntries(years);
        birthYear.setEntryValues(years);
        birthYear.setDefaultValue("1984");
        birthYear.setEnabled(needMax);
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
