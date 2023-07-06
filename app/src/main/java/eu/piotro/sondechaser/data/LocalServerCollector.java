package eu.piotro.sondechaser.data;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.Date;

import eu.piotro.sondechaser.data.local.LocalServerDownloader;
import eu.piotro.sondechaser.data.local.MySondyDownloader;
import eu.piotro.sondechaser.data.local.PipeServerDownloader;
import eu.piotro.sondechaser.data.structs.Point;
import eu.piotro.sondechaser.data.structs.Sonde;
import eu.piotro.sondechaser.handlers.BlueAdapter;

public class LocalServerCollector implements Runnable {

    private Sonde lastSonde;
    private Sonde prevSonde;

    private ArrayList<GeoPoint> track;
    private final Object dataLock = new Object();
    private ArrayList<GeoPoint> prediction;
    private Point pred_point;
    private Status status = Status.RED;

    private long last_decoded;

    private int terrain_alt = 0;

    private volatile boolean stop = false;

    public LocalServerCollector() {}

    private enum Mode {
        NONE,
        PIPE,
        MYSONDY,
    }

    public enum Status {
        RED,
        YELLOW,
        GREEN,
    }

    private Mode source;

    private PipeServerDownloader pipeDownloader;
    private MySondyDownloader mySondyDownloader;

    public void disable() {
        // pipe downloader does not need disabling
        if (mySondyDownloader != null)
            mySondyDownloader.disable();

        source = Mode.NONE;
    }
    public void setPipeSource(String ip) {
        disable();
        pipeDownloader = new PipeServerDownloader(ip);
        source = Mode.PIPE;
    }

    public void setMySondySource(BlueAdapter blueAdapter) {
        disable();
        mySondyDownloader = new MySondyDownloader(blueAdapter);
        mySondyDownloader.enable();
        source = Mode.MYSONDY;
    }

    @Override
    public void run() {
        lastSonde = null;
        track = new ArrayList<>();
        pred_point = null;
        prediction = new ArrayList<>();
        last_decoded = 0;
        status = Status.RED;

        while (!stop) {
            getData();
            generatePrediction();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
            boolean ignored = Thread.interrupted();
        }
        disable();
    }

    private void getData() {
        if (source == Mode.NONE) {
            status = Status.RED;
            return;
        }

        LocalServerDownloader downloader = (source == Mode.PIPE ? pipeDownloader : mySondyDownloader);
        downloader.download();

        prevSonde = lastSonde;
        synchronized (dataLock) {
            lastSonde = downloader.getLastSonde();
            last_decoded = downloader.getLastDecoded();
            status = downloader.getStatus();
        }
    }

    private void generatePrediction() {
        Sonde sonde = lastSonde;
        {
            Sonde lastSonde = prevSonde; // shadow
            if (sonde == null)
                return;
            if (sonde.time > new Date().getTime() && sonde.time - new Date().getTime() < 600_000) {
                // Sonde clocks tend to shift in time
                sonde.time = new Date().getTime();
            }
            if (lastSonde != null) {
                float timedev = (sonde.time - lastSonde.time) / 1000.f;
                float latdev = (float) (sonde.loc.getLatitude() - lastSonde.loc.getLatitude()) / timedev;
                float londev = (float) (sonde.loc.getLongitude() - lastSonde.loc.getLongitude()) / timedev;
                float tgalt = terrain_alt;
                float nextlat = (float) sonde.loc.getLatitude() + (latdev * ((sonde.alt - tgalt) / (sonde.vspeed * -1)));
                float nextlon = (float) sonde.loc.getLongitude() + (londev * ((sonde.alt - tgalt) / (sonde.vspeed * -1)));

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
    }
}
