package eu.piotro.sondechaser.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

public class LocalServerCollector implements Runnable {
    private final String BASE_URL;
    private Sonde lastSonde;
    private ArrayList<GeoPoint> track;
    private final Object dataLock = new Object();
    private ArrayList<GeoPoint> prediction;
    private Point pred_point;
    public long last_success;
    public long last_decoded;

    private int terrain_alt = 0;

    private volatile boolean stop = false;

    public LocalServerCollector(String ip) {
        BASE_URL = "http://" + ip + "/";
    }
    @Override
    public void run() {
        lastSonde = null;
        track = new ArrayList<>();
        pred_point = null;
        prediction = new ArrayList<>();
        last_success = 0;

        while (!stop) {
            download();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
            boolean ignored = Thread.interrupted();
        }
    }

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
                    last_success = new Date().getTime();
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
                if (json.length() == 0 || !json.getBoolean("valid")) {
                    return;
                }

                Sonde sonde = new Sonde();

                float lat = (float)json.getDouble("lat");
                float lon = (float)json.getDouble("lon");
                sonde.loc = new GeoPoint(lat, lon);

                sonde.alt = (int)Math.round(json.getDouble("alt"));

                sonde.time = json.getLong("time")*1000;

                sonde.vspeed = (float)json.getDouble("vs");

                sonde.freq = null;
                sonde.sid = null;

                System.out.println(sonde.alt);
                if (lastSonde != null && sonde.time == lastSonde.time)
                    return;

                if(sonde.time > new Date().getTime() && sonde.time - new Date().getTime() < 600_000) {
                    // Sonde clocks tend to shift in time
                    sonde.time = new Date().getTime();
                }

                if (lastSonde != null) {
                    float timedev = (sonde.time - lastSonde.time) / 1000.f;
                    float latdev = (float)(sonde.loc.getLatitude() - lastSonde.loc.getLatitude()) / timedev;
                    float londev = (float)(sonde.loc.getLongitude() - lastSonde.loc.getLongitude()) / timedev;
                    float tgalt = terrain_alt;
                    float nextlat = (float)sonde.loc.getLatitude() + (latdev * ((sonde.alt - tgalt) / (sonde.vspeed * -1)));
                    float nextlon = (float)sonde.loc.getLongitude() + (londev * ((sonde.alt - tgalt) / (sonde.vspeed * -1)));

                    synchronized (dataLock) {
                        pred_point = new Point();
                        pred_point.point = new GeoPoint(nextlat, nextlon);
                        pred_point.alt = (int) tgalt;
                        pred_point.time = 0;
                        prediction = new ArrayList<>();
                        prediction.add(sonde.loc);
                        prediction.add(pred_point.point);
                    }
                }

                synchronized (dataLock) {
                    System.out.println("localupd");
                    lastSonde = sonde;
                    track.add(sonde.loc);
                }
                last_decoded = new Date().getTime();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void download() {
        try {
            URL url = new URL(BASE_URL + "get");
            downloadData(url, new Parser());
        } catch (Exception ignored) {}
    }

    public Sonde getLastSonde() {
        synchronized (dataLock) {
            return lastSonde;
        }
    }

    public ArrayList<GeoPoint> getSondeTrack() {
        synchronized (dataLock) {
            return track;
        }
    }

    public ArrayList<GeoPoint> getPrediction() {
        synchronized (dataLock) {return prediction;}
    }

    public Point getPredictionPoint() {
        synchronized (dataLock) {return pred_point;}
    }

    public void updateTerrainAlt(int alt) {
        terrain_alt = alt;
    }

    public void stop() {
        stop = true;
    }
}
