package eu.piotro.sondechaser.data;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class ElevationApi implements Runnable {
    public float lat = 0;
    public float lon = 0;
    public int alt = 0;

    public volatile boolean pause = false;
    public volatile boolean refresh = false;

    @Override
    public void run() {
        while (true) {
            try {
                refresh = false;
                URL url = new URL("https://api.open-meteo.com/v1/elevation?latitude=" + lat + "&longitude=" + lon);
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                try {
                    System.err.println(conn.getResponseCode());
                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder resp = new StringBuilder();
                        for (String line; (line = br.readLine()) != null; resp.append(line));
                        String data = resp.toString();

                        JSONObject json = new JSONObject(data);
                        alt = json.getJSONArray("elevation").getInt(0);
                        System.out.println("alt"+alt);
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (!refresh) {
                    do {
                        Thread.sleep(30000);
                    } while (pause);
                }
            } catch (InterruptedException ignored) {}
            boolean ignored = Thread.interrupted();
         }
    }
    public void refresh() {
        refresh = true;
    }
}
