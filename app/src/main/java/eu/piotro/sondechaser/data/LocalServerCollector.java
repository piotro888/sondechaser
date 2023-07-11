package eu.piotro.sondechaser.data;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Date;

import eu.piotro.sondechaser.data.local.LocalServerDownloader;
import eu.piotro.sondechaser.data.local.MySondyDownloader;
import eu.piotro.sondechaser.data.local.PipeServerDownloader;
import eu.piotro.sondechaser.data.local.RdzTtgoDownloader;
import eu.piotro.sondechaser.data.structs.Point;
import eu.piotro.sondechaser.data.structs.Sonde;
import eu.piotro.sondechaser.handlers.BlueAdapter;

public class LocalServerCollector implements Runnable {

    private Sonde lastSonde;

    private ArrayList<GeoPoint> track;

    private ArrayList<Point> points;
    private final Object dataLock = new Object();
    private ArrayList<GeoPoint> prediction;
    private Point pred_point;
    private Status status = Status.RED;

    private long last_decoded;

    private int terrain_alt = 0;

    private volatile boolean stop = false;
    private volatile boolean refresh_flag;

    public LocalServerCollector() {}

    private enum Mode {
        NONE,
        PIPE,
        RDZ,
        MYSONDY,
    }

    public enum Status {
        RED,
        YELLOW,
        GREEN,
    }

    private Mode source;

    private LocalServerDownloader mDownloader;

    public void disable() {
        if(mDownloader != null)
            mDownloader.disable();
        source = Mode.NONE;
    }
    public void setPipeSource(String ip) {
        disable();
        mDownloader = new PipeServerDownloader(ip);
        source = Mode.PIPE;
    }

    public void setRdzSource(String ip) {
        disable();
        mDownloader = new RdzTtgoDownloader(ip);
        source = Mode.RDZ;
    }


    public void setMySondySource(BlueAdapter blueAdapter) {
        disable();
        mDownloader = new MySondyDownloader(blueAdapter);
        mDownloader.enable();
        source = Mode.MYSONDY;
    }

    @Override
    public void run() {
        System.out.println("lcstart");
        lastSonde = null;
        track = new ArrayList<>();
        points = new ArrayList<>();
        pred_point = null;
        prediction = new ArrayList<>();
        last_decoded = 0;
        status = Status.RED;
        refresh_flag = false;

        while (!stop) {
            refresh_flag = false;
            getData();
            generatePrediction();

            if (!refresh_flag) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }
            boolean ignored = Thread.interrupted();
        }
        System.out.println("lcexit");
    }

    private void getData() {
        if (source == Mode.NONE || mDownloader == null) {
            status = Status.RED;
            return;
        }

        mDownloader.download();

        Sonde last_sonde = mDownloader.getLastSonde();

        Point sonde_point = new Point();
        if (last_sonde != null) {
            if (last_sonde.time > new Date().getTime())
                last_sonde.time = new Date().getTime(); // sonde clocks tend to shift in time, correct for that

            sonde_point.point = last_sonde.loc;
            sonde_point.time = last_sonde.time;
            sonde_point.alt = last_sonde.alt;
            sonde_point = fixCoords(sonde_point);
        }

        synchronized (dataLock) {
            status = mDownloader.getStatus();

            if (last_sonde != null && sonde_point != null) {
                if (lastSonde == null || last_sonde.loc.getLongitude() != lastSonde.loc.getLongitude() || last_sonde.loc.getLatitude() != lastSonde.loc.getLatitude()) {
                    System.out.println("Local update");
                    track.add(sonde_point.point);
                    points.add(sonde_point);
                }

                lastSonde = last_sonde;
                last_decoded = mDownloader.getLastDecoded();
            }
        }
    }


    private Point fixCoords(Point point) {
        if (Math.abs(point.point.getLatitude()) > 85.051128)
            return null;

        if (point.point.getLongitude() < 0)
            point.point.setLongitude(point.point.getLongitude() + 360.0);
        if (point.point.getLongitude() > 360.0)
            point.point.setLongitude(point.point.getLongitude() - 360.0);

        if (point.point.getLongitude() < 0 || point.point.getLongitude() > 360)
            return null;

        return point;
    }

    private void generatePrediction() {
        if (points.size() < 2) {
            synchronized (dataLock) {
                pred_point = null;
                prediction.clear();
            }
            return;
        }

        Point last_point = points.get(points.size()-1);

        Point interpolating_element = points.get(track.size()-2);
        final int INTERPOLATION_TIME = 10_000;
        for (int i=points.size()-2; i >= 0; i--) {
            if (last_point.time - points.get(i).time > INTERPOLATION_TIME)
                break; // get the oldest element, but newer than interpolation time.
            interpolating_element = points.get(i);
        }

        float time_diff = (last_point.time - interpolating_element.time) / 1000.f;
        float lat_dev =  (float)(last_point.point.getLatitude() - interpolating_element.point.getLatitude()) /  time_diff;
        float lon_dev =  (float)(last_point.point.getLongitude() - interpolating_element.point.getLongitude()) / time_diff;
        float avg_vspeed = (last_point.alt - interpolating_element.alt) / time_diff;
        float time_to_ground = (last_point.alt - terrain_alt) / (avg_vspeed * -1);

//        if (time_to_ground < 0)
//            return null;

        float new_lat = (float)last_point.point.getLatitude() + (lat_dev*time_to_ground);
        float new_lon = (float)last_point.point.getLongitude() + (lon_dev*time_to_ground);

        Point pred = new Point();
        pred.point = new GeoPoint(new_lat, new_lon);
        pred.time = new Date().getTime() + (long)(time_to_ground * 1000.f);
        pred.alt = terrain_alt;

        synchronized (dataLock) {
            pred_point = fixCoords(pred);
            if(pred_point != null) {
                prediction = new ArrayList<>();
                prediction.add(last_point.point);
                prediction.add(pred_point.point);
            }
        }
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

    public Status getStatus() {
        return status;
    }

    public long getLastDecoded() {
        return last_decoded;
    }

    public void updateTerrainAlt(int alt) {
        terrain_alt = alt;
    }

    public void stop() {
        stop = true;
        disable();
    }

    public void refresh() {
        refresh_flag = true;
    }
}
