package eu.piotro.sondechaser.data;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

import eu.piotro.sondechaser.ui.home.MapUpdater;
import eu.piotro.sondechaser.ui.slideshow.SlideshowFragment;


public class DataCollector implements Runnable {
    private RadiosondyCollector rs_col;
    private SondeHubCollector sh_col;
    private LocalServerCollector lc_col;

    public GpsMyLocationProvider locationProvider;
    public Orientation orientationProvider;

    private final ElevationApi elapi = new ElevationApi();

    private long pred_changed_time;
    private GeoPoint prev_pred_point;

    private final Activity rootActivity;

    private MapUpdater mapUpdater = null;
    private SlideshowFragment compassUpdater = null;
    private boolean stop = false;

    public DataCollector(Activity rootActivity) {
        this.rootActivity = rootActivity;

        locationProvider = new GpsMyLocationProvider(rootActivity);
        locationProvider.setLocationUpdateMinTime(2000);
        locationProvider.setLocationUpdateMinDistance(2f);
        locationProvider.startLocationProvider((l,a)->{
            System.out.println("CONSUME"+l);
        });

        orientationProvider = new Orientation(rootActivity);
    }

    public void setMapUpdater(MapUpdater mapUpdater) {
        this.mapUpdater = mapUpdater;
    }

    public void setCompassUpdater(SlideshowFragment compassUpdater) {
        this.compassUpdater = compassUpdater;
    }

    public GpsMyLocationProvider getLocationProvider() {
        return locationProvider;
    }

    private GeoPoint toGeoPoint(Location l) {
        if (l == null)
            return null;
        return new GeoPoint(l.getLatitude(), l.getLongitude());
    }

    @Override
    public void run() {
        rs_col = new RadiosondyCollector();
        sh_col = new SondeHubCollector();

        Thread rs_thread = new Thread(rs_col);
        Thread sh_thread = new Thread(sh_col);

        Thread elapi_thread = new Thread(elapi);

        SharedPreferences sharedPref = rootActivity.getSharedPreferences("eu.piotro.sondechaser.PSET", Context.MODE_PRIVATE);
        rs_col.setSondeName(sharedPref.getString("rsid",""));
        sh_col.setSondeName(sharedPref.getString("shid",""));

        lc_col = new LocalServerCollector(sharedPref.getString("lsip",""));
        Thread lc_thread = new Thread(lc_col);

        rs_thread.start();
        sh_thread.start();
        lc_thread.start();
        elapi_thread.start();

        try {
            while (!stop) {
                update();

                Thread.sleep(200);
            }
        } catch (InterruptedException ignored) {}
    }

    void update() {
        // Sonde marker and Position data
        Sonde rs_last_sonde = rs_col.getLastSonde();
        Sonde lc_last_sonde = lc_col.getLastSonde();
        Sonde sh_last_sonde = sh_col.getLastSonde();


        if (lc_last_sonde != null && (rs_last_sonde == null || rs_last_sonde.time <= lc_last_sonde.time))
            updatePosition(lc_last_sonde, "LOCAL");
        else if (rs_last_sonde != null && (sh_last_sonde == null || sh_last_sonde.time <= rs_last_sonde.time))
            updatePosition(rs_last_sonde, "RADIOSONDY");
        else
            updatePosition(sh_last_sonde, "SONDEHUB");

        // Prediction data
        if (sh_col.getPrediction() != null)
            updatePredictionData(sh_col.getPredictionPoint(), rs_col.getStartTime(), "SONDEHUB");
        else
            updatePredictionData(rs_col.getPredictionPoint(), rs_col.getStartTime(), "RADIOSONDY");

        updateStatus();

        if (mapUpdater != null)
            mapUpdater.updateLastMarkers(rs_last_sonde, sh_last_sonde, lc_last_sonde);
        if (mapUpdater != null)
            mapUpdater.updatePredictionMarkers(rs_col.getPredictionPoint(), sh_col.getPredictionPoint(), lc_col.getPredictionPoint());
        if (mapUpdater != null)
            mapUpdater.updateTraces(rs_col, sh_col, lc_col);

        updateCompass();

        lc_col.updateTerrainAlt(elapi.alt);
    }

    void updatePosition(Sonde sonde, String source) {
        if (sonde == null) {
            return;
        }

        elapi.lat = sonde.lat; elapi.lon = sonde.lon;

        // override unknown data
        boolean vs_ok = !Objects.equals(source, "SONDEHUB");
        if(rs_col.getLastSonde() != null) {
            if (sonde.sid == null)
                sonde.sid = rs_col.getLastSonde().sid;
            if (sonde.freq == null)
                sonde.freq = rs_col.getLastSonde().freq;
            if (Objects.equals(source, "SONDEHUB") &&
                    new Date().getTime() - rs_col.getLastSonde().time < 120_000 &&
                    sonde.vspeed * rs_col.getLastSonde().vspeed >= 0) {
                sonde.vspeed = rs_col.getLastSonde().vspeed;
                vs_ok = true;
            }
        }


        long data_age = (new Date().getTime()/1000 - sonde.time/1000);

        GeoPoint location = toGeoPoint(locationProvider.getLastKnownLocation());
        double posdist = Double.NaN;
        double bearing = Double.NaN;
        if (location  != null) {
            GeoPoint sonde_loc = new GeoPoint(sonde.lat, sonde.lon);
            posdist = sonde_loc.distanceToAsDouble(location);
            posdist /= 1000;

            bearing = location.bearingTo(sonde_loc);
        }

        if (mapUpdater != null) {;
            mapUpdater.updatePosition(sonde, source, vs_ok, posdist, bearing);
        }
    }

    void updatePredictionData(Point point, long start_time, String source) {
        if (point != null && (prev_pred_point == null || prev_pred_point.getLatitude() != point.point.getLatitude())) {
            prev_pred_point = point.point;
            pred_changed_time = new Date().getTime();
        }


        long data_age = (new Date().getTime()/1000 - pred_changed_time/1000);
        long start_elapsed = (new Date().getTime() - start_time);
        long time_to_end = (point != null ? (point.time - new Date().getTime()) : 0);

        GeoPoint location = toGeoPoint(locationProvider.getLastKnownLocation());
        double posdist = Double.NaN;
        double bearing = Double.NaN;
        if (location != null && point != null) {
            posdist = point.point.distanceToAsDouble(location);
            posdist /= 1000;

            bearing = location.bearingTo(point.point);
        }

        if (mapUpdater != null)
            mapUpdater.updatePredictionData(point, source, start_elapsed, time_to_end, data_age, (float)posdist, (float)bearing);
    }

    void updateStatus() {
        long time = new Date().getTime();
        int lc = Color.RED;
        if (time - lc_col.last_success < 10000) {
            lc = Color.YELLOW;
            if (time - lc_col.last_decoded < 20000)
                lc = Color.GREEN;
        }

        int scol = (time - sh_col.last_decoded < 60000) ? Color.GREEN : Color.RED;
        int rcol = (time - rs_col.last_decoded < 60000) ? Color.GREEN : Color.RED;

        if (mapUpdater != null)
            mapUpdater.updateStatus(rcol, scol, lc);
    }

    public void updateCompass() {
        if (compassUpdater == null)
            return;
        GeoPoint trg = null;
        int alt = 0;
        int age = -1;
        if (compassUpdater.getTarget().equals("POSITION LOCAL")) {
            if(lc_col.getLastSonde() != null) {
                trg = new GeoPoint(lc_col.getLastSonde().lat, lc_col.getLastSonde().lon);
                alt = lc_col.getLastSonde().alt;
                age = (int) (new Date().getTime() / 1000 - lc_col.getLastSonde().time / 1000);
            }
        } else if (compassUpdater.getTarget().equals("POSITION RADIOSONDY")) {
            if(rs_col.getLastSonde() != null) {
                trg = new GeoPoint(rs_col.getLastSonde().lat, rs_col.getLastSonde().lon);
                alt = rs_col.getLastSonde().alt;
                age = (int) (new Date().getTime() / 1000 - rs_col.getLastSonde().time / 1000);
            }
        } else if (compassUpdater.getTarget().equals("POSITION SONDEHUB")) {
            if(sh_col.getLastSonde() != null) {
                trg = new GeoPoint(sh_col.getLastSonde().lat, sh_col.getLastSonde().lon);
                alt = sh_col.getLastSonde().alt;
                age = (int) (new Date().getTime() / 1000 - sh_col.getLastSonde().time / 1000);
            }
        } else if (compassUpdater.getTarget().equals("PREDICTION LOCAL")) {
            if (lc_col.getPredictionPoint() != null) {
                trg = lc_col.getPredictionPoint().point;
                alt = lc_col.getPredictionPoint().alt;
                if (lc_col.getLastSonde() != null)
                    age = (int) (new Date().getTime() / 1000 - lc_col.getLastSonde().time / 1000);
            }
        } else if (compassUpdater.getTarget().equals("PREDICTION RADIOSONDY")) {
            if (rs_col.getPredictionPoint() != null) {
                trg = rs_col.getPredictionPoint().point;
                alt = rs_col.getPredictionPoint().alt;
            }
        } else if (compassUpdater.getTarget().equals("PREDICTION SONDEHUB")) {
            if (sh_col.getPredictionPoint() != null) {
                trg = sh_col.getPredictionPoint().point;
                alt = sh_col.getPredictionPoint().alt;
                age = (int) (new Date().getTime() / 1000 - pred_changed_time / 1000);
            }
        }
        compassUpdater.update(locationProvider.getLastKnownLocation(), trg, alt, orientationProvider.getAzimuth(), age);
    }

    public void onResume() {
        locationProvider.startLocationProvider(null);
    }

    public void onDestroy() {
        locationProvider.stopLocationProvider();
        mapUpdater = null;
        compassUpdater = null;
        stop = true;
    }
}