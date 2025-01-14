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

public class NWS {
    double pressureAtSeaLevel;
    double temperature;

    static final String pressureToUse = "seaLevelPressure"; // or: "barometricPressure"

    long time;
    static final int readTimeout = 10000;
    static final int connectTimeout = 10000;
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
                    if (observationProperties == null)
                        continue;
//                    Log.v("GiantBarometer:NWS", "alt "+altitude);
//                    double barometricPressure = observationProperties.getJSONObject("barometricPressure").getDouble("value")/100.;
                    pressureAtSeaLevel = observationProperties.getJSONObject(pressureToUse).getDouble("value")/100.;
                    temperature = 273.15 + observationProperties.getJSONObject("temperature").getDouble("value");
                    Log.v(TAG, "t "+temperature+" "+pressureToUse+" "+pressureAtSeaLevel);
                    time = System.currentTimeMillis();
                    return true;
                }
                catch (JSONException e) {
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

    private JSONObject fetchJSON(String address) {
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
