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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NWS extends WeatherInfo {

    static final String pressureToUse = "seaLevelPressure"; // or: "barometricPressure"
    private static final String TAG = "GiantBarometer:NWS";

    public boolean getData(Location location) {
        try {
            JSONObject pointsData = fetchJSON(String.format("https://api.weather.gov/points/%.4f,%.4f",location.getLatitude(),location.getLongitude()));

            if (pointsData == null)
                return false;

            JSONObject properties = (JSONObject)pointsData.get("properties");
            String stationsURL = properties.getString("observationStations");
            JSONObject stationsList = fetchJSON(stationsURL);
            JSONArray features = stationsList.getJSONArray("features");
            boolean success = false;
            double altitude = 0;

            for (int i=0;i<features.length();i++) {
                try {
                    JSONObject feature = (JSONObject) features.get(i);
                    JSONObject featureProperties = (JSONObject) feature.get("properties");
                    //                    JSONObject elevation = (JSONObject) featureProperties.get("elevation");
                    //                    altitude = elevation.getDouble("value");
                    String id = featureProperties.getString("stationIdentifier");
                }
                catch (Exception e) {
                    Log.v(TAG, "error "+e);
                }
            }

            for (int i = 0 ; i < features.length() ; i++ ) {
                try {
                    JSONObject feature = (JSONObject) features.get(i);
                    String observationsURL = feature.get("id") + "/observations/latest";
                    JSONObject observationProperties = fetchJSON(observationsURL).getJSONObject("properties");
                    pressureAtSeaLevel = observationProperties.getJSONObject(pressureToUse).getDouble("value")/100.;
                    temperature = 273.15 + observationProperties.getJSONObject("temperature").getDouble("value");
                    Log.v(TAG, "t "+temperature+" "+pressureToUse+" "+pressureAtSeaLevel);
                    time = System.currentTimeMillis();
                    return true;
                }
                catch (JSONException e) {
                }
                catch (NullPointerException e) {
                }
            }
            return false;
        } catch (JSONException e) {
            Log.e("GiantBarometer", "json error "+e);
        }

        return false;
    }

    public double calculateDistance(double lat1, double long1, double lat2, double long2) {
        return Math.abs(lat1-lat2)+Math.abs(long1-long2);
    }

}
