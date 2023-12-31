package eu.piotro.sondechaser.data;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.view.WindowManager;
import android.widget.CheckBox;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;

import java.util.Date;
import java.util.Objects;

import eu.piotro.sondechaser.R;
import eu.piotro.sondechaser.data.structs.Point;
import eu.piotro.sondechaser.data.structs.Sonde;
import eu.piotro.sondechaser.handlers.BlueAdapter;
import eu.piotro.sondechaser.handlers.Orientation;
import eu.piotro.sondechaser.ui.home.MapUpdater;
import eu.piotro.sondechaser.ui.compass.CompassFragment;


public class DataCollector implements Runnable {
    private RadiosondyCollector rs_col;
    private SondeHubCollector sh_col;
    private LocalServerCollector lc_col;

    private Thread rs_col_thread;
    private Thread sh_col_thread;
    private Thread lc_col_thread;
    private Thread elapi_thread;

    public GpsMyLocationProvider locationProvider;
    public Orientation orientationProvider;

    private final ElevationApi elapi = new ElevationApi();

    private long pred_changed_time;
    private GeoPoint prev_pred_point;

    private final Activity rootActivity;

    private MapUpdater mapUpdater = null;
    private CompassFragment compassUpdater = null;
    private boolean stop = false;

    private BlueAdapter blueAdapter;

    public boolean showSondeSet = false;

    public DataCollector(Activity rootActivity) {
        this.rootActivity = rootActivity;

        locationProvider = new GpsMyLocationProvider(rootActivity);
        locationProvider.setLocationUpdateMinTime(2000);
        locationProvider.setLocationUpdateMinDistance(2f);
        locationProvider.startLocationProvider(null);

        orientationProvider = new Orientation(rootActivity);

        blueAdapter = new BlueAdapter(rootActivity);
    }

    public void setMapUpdater(MapUpdater mapUpdater) {
        this.mapUpdater = mapUpdater;
    }

    public void setCompassUpdater(CompassFragment compassUpdater) {
        this.compassUpdater = compassUpdater;
    }

    public GpsMyLocationProvider getLocationProvider() {
        return locationProvider;
    }

    private GeoPoint toGeoPoint(Location l) {
        if (l == null)
            return null;
        if (new Date().getTime() - l.getTime() > 20000)
            return null;
        return new GeoPoint(l.getLatitude(), l.getLongitude());
    }

    @Override
    public void run() {
        initCollectors();

        elapi_thread = new Thread(elapi);
        elapi_thread.start();

        try {
            int it = 0;
            while (!stop) {
                update();

                Thread.sleep(200);

                if((++it)%10 == 0 && toGeoPoint(locationProvider.getLastKnownLocation()) == null) {
                    rootActivity.runOnUiThread(() -> {
                        locationProvider.startLocationProvider(null);
                    });
                }
            }
        } catch (InterruptedException ignored) {}
    }
    private void stopCollectors() {
        if(rs_col != null)
            rs_col.stop();
        if(sh_col != null)
            sh_col.stop();
        if(lc_col != null)
            lc_col.stop();
    }
    public void initCollectors() {
        System.out.println("INITCOLSRC"+lc_col);
        stopCollectors();

        refresh();

        rs_col = new RadiosondyCollector();
        sh_col = new SondeHubCollector();

        SharedPreferences sharedPref = rootActivity.getSharedPreferences("eu.piotro.sondechaser.PSET", Context.MODE_PRIVATE);
        rs_col.setSondeName(sharedPref.getString("rsid",""));
        sh_col.setSondeName(sharedPref.getString("shid",""));

        lc_col = new LocalServerCollector();

        int src_sel = sharedPref.getInt("local_src", 0);
        System.out.println("SRC" + src_sel);
        if (src_sel == 0)
            lc_col.disable();
        else if(src_sel == 1)
            lc_col.setPipeSource(sharedPref.getString("lsip",""));
        else if(src_sel == 2)
            lc_col.setRdzSource(sharedPref.getString("rdzip", ""));
        else if (src_sel == 3) {
            blueAdapter.setDeviceAddress(sharedPref.getString("bt_addr", "nodev (98:F4:AB:6D:2B:5E)"));
            blueAdapter.setType(sharedPref.getString("bt_model", "1: RS41"));
            blueAdapter.setFrequency(sharedPref.getString("bt_freq", "403.000"));
            lc_col.setMySondySource(blueAdapter);
        }

        rs_col_thread = new Thread(rs_col, "rscol");
        sh_col_thread = new Thread(sh_col, "shcol");
        lc_col_thread = new Thread(lc_col, "lccol");

        showSondeSet = true;
        if(!sharedPref.getString("rsid", "").equals("")) {
            rs_col_thread.start();
            showSondeSet = false;
        }

        if(!sharedPref.getString("shid", "").equals("")) {
            sh_col_thread.start();
            showSondeSet = false;
        }

        if(src_sel != 0) {
            lc_col_thread.start();
            showSondeSet = false;
        }

        if(sharedPref.getBoolean("awake", false))
            rootActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            rootActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        if (sh_col.getPredictionPoint() != null)
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
        if(mapUpdater != null && showSondeSet)
            mapUpdater.updateSondeSet(showSondeSet);

        updateCompass();

        lc_col.updateTerrainAlt(elapi.alt);
    }

    void updatePosition(Sonde sonde, String source) {
        if (sonde == null) {
            if (mapUpdater != null) {;
                mapUpdater.updatePosition(null, "?", false, 0,0, 0);
            }
            return;
        }

        elapi.lat = (float)sonde.loc.getLatitude(); elapi.lon = (float)sonde.loc.getLongitude();

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
            GeoPoint sonde_loc = sonde.loc;
            posdist = sonde_loc.distanceToAsDouble(location);
            posdist /= 1000;

            bearing = location.bearingTo(sonde_loc);
        }

        if (mapUpdater != null) {;
            mapUpdater.updatePosition(sonde, source, vs_ok, posdist, bearing, elapi.alt);
        }
    }

    void updatePredictionData(Point point, long start_time, String source) {
        if (point != null && (prev_pred_point == null || prev_pred_point.getLatitude() != point.point.getLatitude())) {
            prev_pred_point = point.point;
            pred_changed_time = new Date().getTime();
        }


        long data_age = pred_changed_time > 0 ? (new Date().getTime()/1000 - pred_changed_time/1000) : -1;
        long start_elapsed = start_time>0 ? (new Date().getTime() - start_time) : 0;
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
        int lc = lc_col.getStatus() == LocalServerCollector.Status.RED ? Color.RED :
                (lc_col.getStatus() ==  LocalServerCollector.Status.YELLOW ? Color.YELLOW : Color.GREEN);
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
                trg = lc_col.getLastSonde().loc;
                alt = lc_col.getLastSonde().alt;
                age = (int) (new Date().getTime() / 1000 - lc_col.getLastSonde().time / 1000);
            }
        } else if (compassUpdater.getTarget().equals("POSITION RADIOSONDY")) {
            if(rs_col.getLastSonde() != null) {
                trg = rs_col.getLastSonde().loc;
                alt = rs_col.getLastSonde().alt;
                age = (int) (new Date().getTime() / 1000 - rs_col.getLastSonde().time / 1000);
            }
        } else if (compassUpdater.getTarget().equals("POSITION SONDEHUB")) {
            if(sh_col.getLastSonde() != null) {
                trg = sh_col.getLastSonde().loc;
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

    public void refresh() {
        if(rs_col_thread != null) {
            rs_col.refresh();
            rs_col_thread.interrupt();
        }
        if(sh_col_thread != null) {
            sh_col.refresh();
            sh_col_thread.interrupt();
        }
        if(lc_col_thread != null) {
            lc_col.refresh();
            lc_col_thread.interrupt();
        }
        if(elapi_thread != null) {
            elapi.refresh();
            elapi_thread.interrupt();
        }
    }

    public void onResume() {
        locationProvider.startLocationProvider(null);
        elapi.pause = false;
        refresh();
    }

    public void onPause() {
        elapi.pause = true;
        locationProvider.stopLocationProvider();
    }

    public void onDestroy() {
        locationProvider.stopLocationProvider();
        mapUpdater = null;
        compassUpdater = null;
        stop = true;
        stopCollectors();
    }
}