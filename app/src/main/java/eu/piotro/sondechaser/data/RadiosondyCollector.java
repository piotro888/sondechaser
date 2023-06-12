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
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class RadiosondyCollector implements Runnable {
    private static final String BASE_URL = "https://radiosondy.info/";
    private Sonde lastSonde;
    private ArrayList<GeoPoint> track;
    private ArrayList<GeoPoint> prediction;
    private Point pred_point;
    long start_time = 0;

    private boolean archive;
    private final Object dataLock = new Object();

    public long last_decoded;


    private String sondeName = null;
    public void setSondeName(String name) {
        sondeName = name;
    }


    @Override
    public void run() {
        lastSonde = null;
        archive = false;
        start_time = 0;
        track = new ArrayList<>();
        prediction = new ArrayList<>();
        pred_point = null;

        try {
            int i = 0;
            while (!Thread.interrupted()) {
                if (!archive)
                    downloadFlyingMapData();
                if ((i++)%3 == 0) {
                    downloadPrediction();
                    if (archive)
                        downloadArchive();
                }
                Thread.sleep(15000);
            }
        } catch (InterruptedException ignored) {}
    }

    private void downloadData(URL url, SondeParser parser) {
        try {
            System.err.println(url);
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

    private class ParseFlyingMapData implements SondeParser {
        public void parse(String data) {
            try {
                JSONObject json = new JSONObject(data);
                JSONArray feat = json.getJSONArray("features");

                if (feat.length() == 0) {
                    archive = true;
                    return;
                }

                JSONObject curr = feat.getJSONObject(1).getJSONObject("properties");
                Sonde sonde = new Sonde();
                sonde.lon = Float.parseFloat(curr.getString("longitude"));
                sonde.lat = Float.parseFloat(curr.getString("latitude"));
                sonde.sid = curr.getString("id");

                String alt_str = curr.getString("altitude");
                sonde.alt = Integer.parseInt(alt_str.substring(0, alt_str.length() - 2));

                String vs_str = curr.getString("climbing");
                sonde.vspeed = Float.parseFloat(vs_str.substring(0, vs_str.length() - 3));

                sonde.freq = curr.getString("frequency");

                String time_str = curr.getString("report");
                System.err.println(time_str.substring(0, time_str.length() - 1));
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                sonde.time = sdf.parse(time_str.substring(0, time_str.length() - 1)).getTime();

                synchronized (dataLock) {
                    lastSonde = sonde;
                }

                JSONArray path = feat.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                ArrayList<GeoPoint> gps = new ArrayList<>();
                for (int i=path.length()-1; i>=0; i-=10) {
                    JSONArray entry = path.getJSONArray(i);
                    gps.add(new GeoPoint(entry.getDouble(1), entry.getDouble(0)));
                    System.out.println(entry.getDouble(0));
                }
                synchronized (dataLock) {
                    track = gps;
                }
                last_decoded = new Date().getTime();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class ParsePredict implements SondeParser {
        private long timeFromDescr(Node n) throws Exception {
            String decsr = n.getChildNodes().item(1).getTextContent();
            String time_str = decsr.substring(decsr.lastIndexOf(' ')+1, decsr.length()-2);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(time_str.substring(0, time_str.length() - 1)).getTime();
        }
        public void parse(String data) {
            try {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document doc = builder.parse(new InputSource(new StringReader(data)));
                Node coords = doc.getElementsByTagName("coordinates").item(0);
                String cstr = coords.getTextContent();
                int cidx = 0;
                ArrayList<GeoPoint> points = new ArrayList<>();
                int last_alt = 0;

                while (cidx<cstr.length()) {
                    System.out.println(cidx);
                    int fsti = cstr.indexOf(",", cidx);
                    float lon = Float.parseFloat(cstr.substring(cidx, fsti-1));
                    cidx = fsti+1;
                    int sndi = cstr.indexOf(",", cidx);
                    float lat = Float.parseFloat(cstr.substring(cidx, sndi-1));
                    cidx = sndi+1;
                    int thirdi = cstr.indexOf(".", cidx);
                    float alt = Float.parseFloat(cstr.substring(cidx, thirdi+1));
                    last_alt = Math.round(alt);
                    cidx = thirdi+2;
                    points.add(new GeoPoint(lat, lon));
                }
                System.out.println(points.size());

                Node start_el = doc.getElementsByTagName("Placemark").item(1);
                Node end_el = doc.getElementsByTagName("Placemark").item(3);

                synchronized (dataLock) {
                    prediction = points;
                    if (points.size() > 0) {
                        pred_point = new Point();
                        pred_point.point = points.get(points.size()-1);
                        pred_point.alt = last_alt;
                        pred_point.time = timeFromDescr(end_el);
                        start_time = timeFromDescr(start_el);
                        System.err.println(start_time);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class ParseArchive implements SondeParser {
        public void parse(String data) {
            try {
                JSONObject json = new JSONObject(data);
                JSONArray feat = json.getJSONArray("features");

                if (feat.length() == 0) {
                    archive = true;
                    return;
                }

                JSONObject curr = feat.getJSONObject(3);
                Sonde sonde = new Sonde();
                sonde.lon = (float)curr.getJSONObject("geometry").getJSONArray("coordinates").getDouble(0);
                sonde.lat = (float)curr.getJSONObject("geometry").getJSONArray("coordinates").getDouble(1);

                sonde.alt = curr.getJSONObject("geometry").getJSONArray("coordinates").getInt(2);

                //sonde.vspeed = 0;

                String time_str = curr.getJSONObject("properties").getString("description");
                System.err.println(time_str.substring(11, 11+19));
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                sonde.time = sdf.parse(time_str.substring(11, 11+19)).getTime();

                synchronized (dataLock) {
                    lastSonde = sonde;
                }

                JSONArray path = feat.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates");
                ArrayList<GeoPoint> gps = new ArrayList<>();
                for (int i=path.length()-1; i>=0; i-=10) {
                    JSONArray entry = path.getJSONArray(i);
                    gps.add(new GeoPoint(entry.getDouble(1), entry.getDouble(0)));
                    System.out.println(entry.getDouble(0));
                }
                synchronized (dataLock) {
                    track = gps;
                }
                last_decoded = new Date().getTime();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void downloadFlyingMapData() {
        try {
            URL url = new URL(BASE_URL + "export/export_map.php?sonde_map=1&sondenumber=" + sondeName);
            downloadData(url, new ParseFlyingMapData());
        } catch (Exception ignored) {}
    }

    private void downloadPrediction() {
        try {
            URL url = new URL(BASE_URL + "mail_reports/PREDICT/"+sondeName+"_predict.kml");
            downloadData(url, new ParsePredict());
        } catch (Exception ignored) {}
    }

    private void downloadArchive() {
        try {
            URL url = new URL(BASE_URL + "sonde-data/GeoJSON/M/"+sondeName+".json");
            downloadData(url, new ParseArchive());
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

    public long getStartTime() {
        synchronized (dataLock) {return start_time;}
    }

}
