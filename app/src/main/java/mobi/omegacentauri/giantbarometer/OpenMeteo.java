package mobi.omegacentauri.giantbarometer;

import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class OpenMeteo extends WeatherInfo {

    private static final String TAG = "GiantBarometer:OM";
    
    public boolean getData(Location location) {
        try {
            JSONObject pointsData = fetchJSON(String.format("https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=pressure_msl,temperature",location.getLatitude(),location.getLongitude()));

            if (pointsData == null)
                return false;

            JSONObject current = (JSONObject)pointsData.get("current");

            pressureAtSeaLevel = current.getDouble("pressure_msl");
            temperature = 273.15 + current.getDouble("temperature");

            return true;
        } catch (JSONException e) {
            Log.e(TAG, "json error "+e);
        }

        return false;
    }
}
