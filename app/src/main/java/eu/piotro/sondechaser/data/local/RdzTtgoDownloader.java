package eu.piotro.sondechaser.data.local;

import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import eu.piotro.sondechaser.data.LocalServerCollector;
import eu.piotro.sondechaser.data.structs.Sonde;
import eu.piotro.sondechaser.data.SondeParser;

public class RdzTtgoDownloader implements LocalServerDownloader {
    private final String BASE_URL;

    public RdzTtgoDownloader(String ip) {
        BASE_URL = "http://" + ip + "/";
    }

    private long last_received;
    private long last_decoded;

    private Sonde lastSonde = null;

    private LocalServerCollector.Status mStatus = LocalServerCollector.Status.RED;

    private void downloadData(URL url, SondeParser parser) {
        try {
            System.err.println(url);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                System.err.println(conn.getResponseCode());
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder resp = new StringBuilder();
                    for (String line; (line = br.readLine()) != null; resp.append(line));
                    parser.parse(resp.toString());
                    last_received = new Date().getTime();
                }

            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class Parser implements SondeParser {
        public void parse(String data) {
            try {
                JSONObject json = new JSONObject(data);

                if (json.length() == 0)
                    return;

                json = json.getJSONObject("sonde");


                Sonde sonde = new Sonde();

                float lat = (float)json.getDouble("lat");
                float lon = (float)json.getDouble("lon");
                sonde.loc = new GeoPoint(lat, lon);

                sonde.alt = (int)Math.round(json.getDouble("alt"));

                sonde.time = json.getLong("time")*1000;

                sonde.vspeed = (float)json.getDouble("vs");

                // TODO: Add RSSI field + display and parse freq+sid also from local

                sonde.freq = null;
                sonde.sid = null;

                if (json.getInt("res") != 0) // status 0=ok
                    return;

                System.out.println("RDZ TTGO Server" + sonde.time);

                if (lastSonde == null || sonde.time != lastSonde.time)
                    last_decoded = new Date().getTime();

                lastSonde = sonde;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateStatus() {
        LocalServerCollector.Status lc = LocalServerCollector.Status.RED;
        if (new Date().getTime() - last_received < 10000) {
            lc = LocalServerCollector.Status.YELLOW;
            if (new Date().getTime() - last_decoded < 20000)
                lc = LocalServerCollector.Status.GREEN;
        }
        mStatus = lc;
    }

    @Override
    public void download() {
        try {
            updateStatus();
            URL url = new URL(BASE_URL + "live.json");
            downloadData(url, new Parser());
        } catch (Exception ignored) {}
    }

    @Override
    public Sonde getLastSonde() {
        return lastSonde;
    }

    @Override
    public long getLastDecoded() {
        return last_decoded;
    }

    @Override
    public LocalServerCollector.Status getStatus() {
        return mStatus;
    }

    @Override
    public void enable() {}
    @Override
    public void disable() {}
}
