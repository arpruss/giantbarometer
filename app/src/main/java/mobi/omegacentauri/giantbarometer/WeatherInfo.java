package mobi.omegacentauri.giantbarometer;

import android.location.Location;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

public abstract class WeatherInfo {
    double pressureAtSeaLevel;
    double temperature;
    long time;
    static final int readTimeout = 10000;
    static final int connectTimeout = 10000;

    abstract public boolean getData(Location location);
    static final String TAG = "GiantBarometer.WInfo";

    protected void setTime(String timeStamp) {
        //2025-04-20T14:55:00+00:00 [25]
        //2025-04-20T15:45 [16]

        Log.v(TAG, timeStamp);

        time = System.currentTimeMillis();

        if (timeStamp.length() == 16)
            timeStamp += ":00";
        if (timeStamp.length() == 19)
            timeStamp += "+0000";
        if (timeStamp.length() == 25 && timeStamp.charAt(22) == ':'
                && (timeStamp.charAt(19) == '+' || timeStamp.charAt(19) == '-')) {
            timeStamp = timeStamp.substring(0, 22) + timeStamp.substring(23); // omit colon
        }
        if (timeStamp.length() != 24)
            return;

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

        try {
            long t = df.parse(timeStamp).getTime();
            if (t < time) {
                time = t;
            }
        } catch (ParseException e) {
        }

    }
    public static JSONObject fetchJSON(String address) {
        try {
            URL url = new URL(address);
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setReadTimeout(readTimeout);
            c.setConnectTimeout(connectTimeout);
            c.setRequestProperty("User-Agent", "mobi.omegacentauri.giantbarometer by omegacentaurisoftware@gmail.com");
            c.connect();
            InputStream is = c.getInputStream();
            InputStreamReader irs = new InputStreamReader(is);
            BufferedReader in = new BufferedReader(irs);
            StringBuilder data = new StringBuilder();

            while (true) {
                String line = in.readLine();
                if (line == null)
                    break;
                data.append(line);
            }
            in.close();
            return new JSONObject(data.toString());
        } catch (MalformedURLException e) {
            return null;
        } catch (IOException e) {
            Log.e("GiantBarometer", "io error "+e);
            return null;
        } catch (JSONException e) {
            Log.e("GiantBarometer", "json error "+e);
            return null;
        }
    }
}