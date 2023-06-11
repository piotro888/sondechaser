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

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
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
                Thread.sleep(30000);
             }
        } catch (InterruptedException ignored) {}
    }
}
