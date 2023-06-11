package eu.piotro.sondechaser.data;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class SondeHubCollector implements Runnable {
    private static final String BASE_URL = "https://api.v2.sondehub.org/";
    //private Sonde lastSonde;
    //private ArrayList<GeoPoint> track;
    private ArrayList<GeoPoint> prediction;
    private Point pred_point;
    //long start_time = 0;
    public long last_decoded;
    private final Object dataLock = new Object();

    private String sondeName = null;
    public void setSondeName(String name) {
        sondeName = name;
    }


    @Override
    public void run() {
        prediction = new ArrayList<>();
        pred_point = null;

        try {
            while (!Thread.interrupted()) {
                downloadPrediction();
                Thread.sleep(30000);
            }
        } catch (InterruptedException ignored) {}
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

                sonde.lat = (float)curr.getDouble("latitude");
                sonde.lon = (float)curr.getDouble("longitude");

                sonde.alt = (int)Math.round(curr.getDouble("altitude"));

                String time_str = curr.getString("time");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                sonde.time = sdf.parse(time_str.substring(0, time_str.length() - 1)).getTime();
                System.out.println("Sondehub time" + sonde.time);

                // TODO: use it?

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
                    pred_point = point;
                    prediction = gps;
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
}
