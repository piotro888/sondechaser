package eu.piotro.sondechaser.ui.home.map;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

import eu.piotro.sondechaser.R;
import eu.piotro.sondechaser.data.ElevationApi;
import eu.piotro.sondechaser.data.LocalServerCollector;
import eu.piotro.sondechaser.data.Point;
import eu.piotro.sondechaser.data.RadiosondyCollector;
import eu.piotro.sondechaser.data.Sonde;
import eu.piotro.sondechaser.data.SondeHubCollector;
import eu.piotro.sondechaser.data.SondeParser;
import eu.piotro.sondechaser.ui.home.HomeFragment;

public class MapUpdater implements Runnable {
    private HomeFragment homeFragment;

    private RadiosondyCollector rs_col;
    private SondeHubCollector sh_col;
    private LocalServerCollector lc_col;
    private ElevationApi elapi = new ElevationApi();

    private Marker sondeMarker;

    private Marker rsPredMarker;
    private Marker rsLastMarker;
    private Polyline rsPathLine;
    private Polyline rsPredLine;

    private Marker shPredMarker;
    private Polyline shPredLine;

    private Marker localPredMarker;
    private Marker localLastMarker;
    private Polyline localPathLine;

    private long pred_changed_time;
    private GeoPoint prev_pred_point;

    public GeoPoint last_pos = new GeoPoint(51.0, 17.0);
    public GeoPoint last_pred = new GeoPoint(51.0, 17.0);

    public MapUpdater(HomeFragment homeFragment) {
        this.homeFragment = homeFragment;

        sondeMarker = new Marker(homeFragment.mapView);
        sondeMarker.setVisible(false);
        sondeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        Drawable ballon = ContextCompat.getDrawable(homeFragment.getActivity(), R.drawable.baloon);
        Bitmap b = ((BitmapDrawable)ballon).getBitmap();
        Bitmap bitmapResized = Bitmap.createScaledBitmap(b, b.getWidth()/5, b.getHeight()/5, false);
        sondeMarker.setIcon(new BitmapDrawable(homeFragment.getResources(), bitmapResized));
        homeFragment.mapView.getOverlays().add(sondeMarker);

        rsPathLine = new Polyline(homeFragment.mapView);
        rsPathLine.setEnabled(true);
        rsPathLine.setColor(Color.CYAN);
        homeFragment.mapView.getOverlays().add(rsPathLine);

        rsPredLine = new Polyline(homeFragment.mapView);
        rsPredLine.setEnabled(true);
        rsPredLine.setColor(Color.CYAN);
        rsPredLine.setWidth(5f);
        homeFragment.mapView.getOverlays().add(rsPredLine);

        pred_changed_time = new Date().getTime();

        rsPredMarker = new Marker(homeFragment.mapView);
        rsPredMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        Bitmap rstgb = ((BitmapDrawable)ContextCompat.getDrawable(homeFragment.getActivity(), R.drawable.target_rs)).getBitmap();
        rstgb = Bitmap.createScaledBitmap(rstgb, rstgb.getWidth()/4, rstgb.getHeight()/4, false);
        rsPredMarker.setIcon(new BitmapDrawable(homeFragment.getResources(), rstgb));
        homeFragment.mapView.getOverlays().add(rsPredMarker);

        rsLastMarker = new Marker(homeFragment.mapView);
        rsLastMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        rsLastMarker.setIcon(homeFragment.getResources().getDrawable(R.drawable.loclastrs));
        homeFragment.mapView.getOverlays().add(rsLastMarker);

        shPredMarker = new Marker(homeFragment.mapView);
        shPredMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        Bitmap shtgb = ((BitmapDrawable)ContextCompat.getDrawable(homeFragment.getActivity(), R.drawable.target_sh)).getBitmap();
        shtgb = Bitmap.createScaledBitmap(shtgb, shtgb.getWidth()/4, shtgb.getHeight()/4, false);
        shPredMarker.setIcon(new BitmapDrawable(homeFragment.getResources(), shtgb));
        homeFragment.mapView.getOverlays().add(shPredMarker);

        shPredLine = new Polyline(homeFragment.mapView);
        shPredLine.setEnabled(true);
        shPredLine.setColor(Color.MAGENTA);
        shPredLine.setWidth(5f);
        homeFragment.mapView.getOverlays().add(shPredLine);

        localPredMarker = new Marker(homeFragment.mapView);
        localPredMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        Bitmap lcltgb = ((BitmapDrawable)ContextCompat.getDrawable(homeFragment.getActivity(), R.drawable.target_lcl)).getBitmap();
        lcltgb = Bitmap.createScaledBitmap(lcltgb, shtgb.getWidth()/4, shtgb.getHeight()/4, false);
        localPredMarker.setIcon(new BitmapDrawable(homeFragment.getResources(), lcltgb));
        homeFragment.mapView.getOverlays().add(localPredMarker);

        localLastMarker = new Marker(homeFragment.mapView);
        localLastMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        localLastMarker.setIcon(homeFragment.getResources().getDrawable(R.drawable.loclastlocal));
        homeFragment.mapView.getOverlays().add(localLastMarker);

        localPathLine = new Polyline(homeFragment.mapView);
        localPathLine.setColor(Color.RED);
        homeFragment.mapView.getOverlays().add(localPathLine);

    }

    long getUTC() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        return calendar.getTimeInMillis();
    }

    @Override
    public void run() {
        rs_col = new RadiosondyCollector();
        sh_col = new SondeHubCollector();

        Thread rs_thread = new Thread(rs_col);
        Thread sh_thread = new Thread(sh_col);

        Thread elapi_thread = new Thread(elapi);

        SharedPreferences sharedPref = homeFragment.getActivity().getSharedPreferences("eu.piotro.sondechaser.PSET", Context.MODE_PRIVATE);
        rs_col.setSondeName(sharedPref.getString("rsid",""));
        sh_col.setSondeName(sharedPref.getString("shid",""));

        lc_col = new LocalServerCollector(sharedPref.getString("lsip",""));
        Thread lc_thread = new Thread(lc_col);

        rs_thread.start();
        sh_thread.start();
        lc_thread.start();
        elapi_thread.start();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                draw();
                lc_col.updateTerrainAlt(elapi.alt);
                Thread.sleep(1000);
            }
        } catch (InterruptedException ignored) {}
    }

    void draw() {
        // Sonde marker and Position data
        Sonde rs_last_sonde = rs_col.getLastSonde();
        Sonde lc_last_sonde = lc_col.getLastSonde();
        if (lc_last_sonde != null && (rs_last_sonde == null || rs_last_sonde.time <= lc_last_sonde.time))
            updatePosition(lc_last_sonde, "LOCAL");
        else
            updatePosition(rs_last_sonde, "RADIOSONDY");

        // Draw predictions & paths
        updateTraces();

        // Prediction markers
        updatePredictionMarkers();

        // Prediction data
        if (sh_col.getPrediction() != null)
            updatePredictionData(sh_col.getPredictionPoint(), rs_col.getStartTime(), "SONDEHUB");
        else
            updatePredictionData(rs_col.getPredictionPoint(), rs_col.getStartTime(), "RADIOSONDY");
    
        // Last markers
        updateLastMarkers();
        updateStatus();
    }

    void updatePosition(Sonde sonde, String source) {
        if (sonde == null) {
            if(paused)
                return;
            homeFragment.requireActivity().runOnUiThread(() -> {
                        ((TextView) homeFragment.requireView().findViewById(R.id.textsid)).setText("N/A");
                        ((TextView) homeFragment.requireView().findViewById(R.id.textfreq)).setText("N/A");
                        ((TextView) homeFragment.requireView().findViewById(R.id.textalt)).setText("N/A m");
                        ((TextView) homeFragment.requireView().findViewById(R.id.textaog)).setText("N/A m");
                        ((TextView) homeFragment.requireView().findViewById(R.id.textvspeed)).setText("N/A m/s");
                        ((TextView) homeFragment.requireView().findViewById(R.id.textposage)).setText("N/A s");
                        ((TextView) homeFragment.requireView().findViewById(R.id.textposdist)).setText("N/A km");
                        ((TextView) homeFragment.requireView().findViewById(R.id.textposhdg)).setText("N/A");
                        ((TextView) homeFragment.requireView().findViewById(R.id.textpossrc)).setText("N/A");
                         sondeMarker.setVisible(false);
            });
            return;
        }

        last_pos = new GeoPoint(sonde.lat, sonde.lon);
        elapi.lat = sonde.lat; elapi.lon = sonde.lon;
        try {
            long data_age = (getUTC()/1000 - sonde.time/1000);

            GeoPoint location = homeFragment.locationOverlay.getMyLocation();
            String posdist_km = "N/A";
            String bearing_str = "N/A";
            if (location  != null) {
                GeoPoint sonde_loc = new GeoPoint(sonde.lat, sonde.lon);
                double posdist = sonde_loc.distanceToAsDouble(location);
                posdist /= 1000;
                DecimalFormat df = new DecimalFormat("#.##");
                posdist_km = df.format(posdist);

                double bearing = location.bearingTo(sonde_loc);
                bearing_str = Math.round(bearing) + "°";
            }
            String final_posdist_km = posdist_km;
            String final_bearing_str = bearing_str;

            if(paused)
                return;
            try {
                homeFragment.requireActivity().runOnUiThread(() -> {
                    try {
                    ((TextView) homeFragment.requireView().findViewById(R.id.textsid)).setText(sonde.sid == null ? "N/A" : sonde.sid);
                    ((TextView) homeFragment.requireView().findViewById(R.id.textfreq)).setText(sonde.sid == null ? "N/A" : sonde.freq);
                    ((TextView) homeFragment.requireView().findViewById(R.id.textalt)).setText(sonde.alt + " m");
                    ((TextView) homeFragment.requireView().findViewById(R.id.textaog)).setText(sonde.alt - elapi.alt + " m");
                    ((TextView) homeFragment.requireView().findViewById(R.id.textvspeed)).setText(sonde.vspeed + " m/s");
                    homeFragment.requireView().findViewById(R.id.imagevsarrow).setRotation(180 * ((sonde.vspeed < 0) ? 1 : 0));
                    ((TextView) homeFragment.requireView().findViewById(R.id.textposage)).setText(data_age + "s");
                    ((TextView) homeFragment.requireView().findViewById(R.id.textposage)).setTextColor(data_age > 120 ? Color.RED : Color.BLACK);
                    ((TextView) homeFragment.requireView().findViewById(R.id.textposdist)).setText(final_posdist_km + " km");
                    ((TextView) homeFragment.requireView().findViewById(R.id.textposhdg)).setText(final_bearing_str);
                    ((TextView) homeFragment.requireView().findViewById(R.id.textpossrc)).setText("(" + source + ")");

                    sondeMarker.setVisible(true);
                    sondeMarker.setPosition(new GeoPoint(sonde.lat, sonde.lon));
                    sondeMarker.setTitle("POSITION\n" + sonde.lat + " " + sonde.lon + "\n" + sonde.alt + "m\n" + new Date(sonde.time) + "\n" + source + "\n");

                    rsPathLine.setPoints(rs_col.getSondeTrack());
                    } catch (Exception ignored){}
                });
            } catch (Exception ignored){}
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    void updateTraces() {
        if(paused)
            return;
        try {
            homeFragment.requireActivity().runOnUiThread(() -> {
                rsPathLine.setPoints(rs_col.getSondeTrack());
                rsPredLine.setPoints(rs_col.getPrediction());
                shPredLine.setPoints(sh_col.getPrediction());
                localPathLine.setPoints(lc_col.getSondeTrack());
            });
        }catch (Exception ignored){}
    }

    void updatePredictionData(Point point, long start_time, String source) {
        if (point != null && (prev_pred_point == null || prev_pred_point.getLatitude() != point.point.getLatitude())) {
            prev_pred_point = point.point;
            pred_changed_time = getUTC();
        }


        long data_age = (getUTC()/1000 - pred_changed_time/1000);
        long start_elapsed = (getUTC() - start_time);
        long time_to_end = (point != null ? (point.time - getUTC()) : 0);
        System.out.println("utc"+getUTC()+" "+new Date().getTime());
        if (point != null)
            System.out.println("time" + time_to_end + " " + start_elapsed + "x" + point.time + " " + new Date().getTime());
        SimpleDateFormat time_format = new SimpleDateFormat("HH:mm:ss");
        time_format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String time_elapsed = (start_elapsed < 0 ? "-" : "") + time_format.format(new Date(Math.abs(start_elapsed)));
        String str_to_end = (time_to_end < 0 ? "-" : "") + time_format.format(new Date(Math.abs(time_to_end)));

        GeoPoint location = homeFragment.locationOverlay.getMyLocation();
        String posdist_km = "N/A";
        String bearing_str = "N/A";
        if (location != null && point != null) {
            double posdist = point.point.distanceToAsDouble(location);
            posdist /= 1000;
            DecimalFormat df = new DecimalFormat("#.##");
            posdist_km = df.format(posdist);

            double bearing = location.bearingTo(point.point);
            bearing_str = Math.round(bearing) + "°";
        }

        if (point != null)
            last_pred = point.point;
        else
            last_pred = last_pos;

        String final_posdist_km = posdist_km;
        String final_bearing_str = bearing_str;

        try{
            if(paused)
                return;
            homeFragment.requireActivity().runOnUiThread(() -> {
                try {
                ((TextView) homeFragment.requireView().findViewById(R.id.textpostime)).setText(start_time == 0 ? "N/A" : time_elapsed);
                ((TextView) homeFragment.requireView().findViewById(R.id.textpredtime)).setText(point == null ? "N/A" : str_to_end);
                ((TextView) homeFragment.requireView().findViewById(R.id.textpreddist)).setText(final_posdist_km + " km");
                ((TextView) homeFragment.requireView().findViewById(R.id.textpredhdg)).setText(final_bearing_str);
                ((TextView) homeFragment.requireView().findViewById(R.id.textpredage)).setText(data_age+"s");
                ((TextView) homeFragment.requireView().findViewById(R.id.textpredsrc)).setText(" ("+source+")");
                } catch (Exception ignored){}
            });
        } catch (Exception ignored){}
    }

    void updatePredictionMarkers() {
        Point rs_pred = rs_col.getPredictionPoint();
        Point sh_pred = sh_col.getPredictionPoint();
        if(paused)
            return;
        try{
            homeFragment.requireActivity().runOnUiThread(() -> {
                rsPredMarker.setVisible(rs_pred != null);
                if (rs_pred != null) {
                    rsPredMarker.setPosition(rs_pred.point);
                    SimpleDateFormat time_format = new SimpleDateFormat("HH:mm:ss");
                    rsPredMarker.setTitle("PREDICTION\n"+rs_pred.point.getLatitude() + "\n" + rs_pred.point.getLongitude() + "\n" + rs_pred.alt + "m\n" + new Date(rs_pred.time) + "\nRADIOSONDY");
                }

                shPredMarker.setVisible(sh_pred != null);
                if (sh_pred != null) {
                    shPredMarker.setPosition(sh_pred.point);
                    SimpleDateFormat time_format = new SimpleDateFormat("HH:mm:ss");
                    shPredMarker.setTitle("PREDICTION\n"+sh_pred.point.getLatitude() + "\n" + sh_pred.point.getLongitude() + "\n" + sh_pred.alt + "m\n" + new Date(sh_pred.time) + "\nSONDEHUB");
                }

                System.err.println("lcp" + lc_col.getPredictionPoint());
                if(lc_col.getPredictionPoint() != null) {
                    localPredMarker.setVisible(true);
                    localPredMarker.setPosition(lc_col.getPredictionPoint().point);
                    localPredMarker.setTitle("LOCAL PREDICTION INTERPOLATION\n"+lc_col.getPredictionPoint().point.getLatitude() + "\n" + lc_col.getPredictionPoint().point.getLongitude() + "\n" + lc_col.getPredictionPoint().alt);
                } else
                    localPredMarker.setVisible(false);
            });
        } catch (Exception ignored){}
    }

    void updateLastMarkers() {
        try{
            if(paused)
                return;
            homeFragment.requireActivity().runOnUiThread(() -> {
                if(rs_col.getLastSonde() != null) {
                    long rs_data_age = (new Date().getTime() / 1000 - rs_col.getLastSonde().time / 1000);
                    SimpleDateFormat time_format = new SimpleDateFormat("HH:mm:ss");

                        rsLastMarker.setVisible((rs_data_age > 120));
                        rsLastMarker.setPosition(new GeoPoint(rs_col.getLastSonde().lat, rs_col.getLastSonde().lon));
                        rsLastMarker.setTitle("RADIOSONDY LAST POSITION\n"+rs_col.getLastSonde().lat + " " + rs_col.getLastSonde().lon + "\n" + rs_col.getLastSonde().alt + "m\n" + new Date(rs_col.getLastSonde().time) + "\n");
                } else {
                    rsLastMarker.setVisible(false);
                }

                if(lc_col.getLastSonde() != null) {
                    long lc_data_age = (new Date().getTime() / 1000 - lc_col.getLastSonde().time / 1000);
                    SimpleDateFormat time_format = new SimpleDateFormat("HH:mm:ss");

                    localLastMarker.setVisible((lc_data_age > 20));
                    localLastMarker.setPosition(new GeoPoint(lc_col.getLastSonde().lat, lc_col.getLastSonde().lon));
                    localLastMarker.setTitle("LOCAL LAST POSITION\n"+lc_col.getLastSonde().lat + " " + lc_col.getLastSonde().lon + "\n" + lc_col.getLastSonde().alt + "m\n" + new Date(lc_col.getLastSonde().time) + "\n");

                } else {
                    localLastMarker.setVisible(false);
                }
            });
        } catch (Exception ignored){}
    }

    void updateStatus() {
        try {
            if(paused)
                return;
            homeFragment.requireActivity().runOnUiThread(() -> {
                long time = new Date().getTime();
                int lc = Color.RED;
                if (time - lc_col.last_success < 60000)
                    lc = Color.YELLOW;
                if (time - lc_col.last_decoded < 60000)
                    lc = Color.GREEN;

                ((TextView) homeFragment.requireView().findViewById(R.id.textstatl)).setTextColor(lc);
                ((TextView) homeFragment.requireView().findViewById(R.id.textstats)).setTextColor((time - sh_col.last_decoded < 600000)?Color.GREEN : Color.RED);
                ((TextView) homeFragment.requireView().findViewById(R.id.textstatr)).setTextColor((time - rs_col.last_decoded < 60000)?Color.GREEN : Color.RED);
            });
        }catch (Exception ignored){}
    }
    private boolean paused = false;
    public void onPause() {
        paused = true; // ui writes causing NPE :ccc
    }
    public void onResume() {
        paused = false;
    }
}