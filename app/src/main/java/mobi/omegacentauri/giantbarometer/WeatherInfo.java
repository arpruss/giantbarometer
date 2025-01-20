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

public abstract class WeatherInfo {
    double pressureAtSeaLevel;
    double temperature;
    long time;
    static final int readTimeout = 10000;
    static final int connectTimeout = 10000;

    abstract public boolean getData(Location location);
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