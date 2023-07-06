package eu.piotro.sondechaser.data;

import android.app.Activity;
import android.widget.PopupMenu;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

import eu.piotro.sondechaser.data.structs.Point;
import eu.piotro.sondechaser.data.structs.Sonde;

public class SondeHubCollector implements Runnable {
    private static final String BASE_URL = "https://api.v2.sondehub.org/";
    private Sonde lastSonde;
    private ArrayList<GeoPoint> track;
    private ArrayList<GeoPoint> prediction;
    private Point pred_point;
    //long start_time = 0;
    public volatile long last_decoded;
    private final Object dataLock = new Object();

    private String sondeName = null;
    public void setSondeName(String name) {
        sondeName = name;
    }

    private ArrayList<String> sonde_entries = null;
    private volatile boolean stop = false;


    @Override
    public void run() {
        prediction = new ArrayList<>();
        pred_point = null;
        lastSonde = null;
        track = new ArrayList<>();

        while (!stop) {
            downloadPrediction();
            try {
                Thread.sleep(30000);
            } catch (InterruptedException ignored) {}
            boolean ignored = Thread.interrupted();
        }
    }

    private void downloadData(URL url, SondeParser parser) {
        try {
            System.err.println(url.toString());
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            try {
                System.err.println(conn.getResponseCode());
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder resp = new StringBuilder();
                    for (String line; (line = br.readLine()) != null; resp.append(line));
                    parser.parse(resp.toString());
                }

            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class ParsePredict implements SondeParser {
        public void parse(String data) {
            try {
                JSONArray json = new JSONArray(data);
                if (json.length() == 0) {
                    return;
                }

                Sonde sonde = new Sonde();

                JSONObject curr = json.getJSONObject(0);

                float lat = (float)curr.getDouble("latitude");
                float lon = (float)curr.getDouble("longitude");
                sonde.loc = new GeoPoint(lat, lon);

                sonde.alt = (int)Math.round(curr.getDouble("altitude"));

                sonde.vspeed = curr.getInt("descending") == 0 ? (float)curr.getDouble("ascent_rate") : (float)-curr.getDouble("descent_rate");

                String time_str = curr.getString("time");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                sonde.time = sdf.parse(time_str.substring(0, time_str.length() - 1)).getTime();
                System.out.println("Sondehub time" + sonde.time);

                String path = curr.getString("data");
                JSONArray pathobj = new JSONArray(path);
                ArrayList<GeoPoint> gps = new ArrayList<>();
                for (int i=0; i<pathobj.length(); i++) {
                    JSONObject entry = pathobj.getJSONObject(i);
                    gps.add(new GeoPoint(entry.getDouble("lat"), entry.getDouble("lon")));
                }

                if (pathobj.length() == 0)
                    return;

                JSONObject last = pathobj.getJSONObject(pathobj.length()-1);
                Point point = new Point();
                point.point = new GeoPoint(last.getDouble("lat"), last.getDouble("lon"));
                point.alt = (int)Math.round(last.getDouble("alt"));
                point.time = last.getLong("time")*1000;

                synchronized (dataLock) {
                    lastSonde = sonde;
                    pred_point = point;
                    prediction = gps;
                    if (track.size() == 0 || (track.get(track.size()-1).getLatitude() != sonde.loc.getLatitude() ||
                                             track.get(track.size()-1).getLongitude() != sonde.loc.getLongitude()))
                        track.add(sonde.loc);
                }
                last_decoded = new Date().getTime();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void downloadPrediction() {
        try {
            URL url = new URL(BASE_URL + "predictions?vehicles="+sondeName);
            downloadData(url, new ParsePredict());
        } catch (Exception ignored) {}
    }

    public ArrayList<GeoPoint> getPrediction() {
        synchronized (dataLock) {return prediction;}
    }

    public Point getPredictionPoint() {
        synchronized (dataLock) {return pred_point;}
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

    public void stop() {
        stop = true;
    }

    private class ParseSondeList implements SondeParser {
        public void parse(String data) {
            sonde_entries = new ArrayList<>();
            sonde_entries.add("Sondehub:");
            try {
                JSONObject json = new JSONObject(data);
                JSONArray names = json.names();
                System.out.println(names);
                for (int i=0; i<names.length(); i++) {
                    String name = names.getString(i);
                    sonde_entries.add(name);
                }
            } catch (Exception e) {
                sonde_entries.add("Error fetching sonde list");
                e.printStackTrace();
            }
        }
    }
    public void fillMenu(Activity activity, PopupMenu menu) {
        Thread updateThread = new Thread(()-> {
            try {
                URL url = new URL(BASE_URL + "sondes/telemetry?duration=1h");
                downloadData(url, new ParseSondeList());

                activity.runOnUiThread(()-> {
                    menu.dismiss();
                    menu.getMenu().clear();
                    for (String s : sonde_entries)
                        menu.getMenu().add(s);
                    menu.show();
                });

            } catch (Exception ignored) {}
        });
        updateThread.start();
    }

}
