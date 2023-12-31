package eu.piotro.sondechaser.ui.home;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

import eu.piotro.sondechaser.R;
import eu.piotro.sondechaser.data.LocalServerCollector;
import eu.piotro.sondechaser.data.structs.Point;
import eu.piotro.sondechaser.data.RadiosondyCollector;
import eu.piotro.sondechaser.data.structs.Sonde;
import eu.piotro.sondechaser.data.SondeHubCollector;

public class MapUpdater {
    private HomeFragment homeFragment;

    private Marker sondeMarker;

    private Marker rsPredMarker;
    private Marker rsLastMarker;
    private Polyline rsPathLine;
    private Polyline rsPredLine;

    private Marker shPredMarker;
    private Marker shLastMarker;
    private Polyline shPathLine;
    private Polyline shPredLine;

    private Marker localPredMarker;
    private Marker localLastMarker;
    private Polyline localPathLine;
    private String sondeMarkerSource = "";

    public GeoPoint last_pos = new GeoPoint(51.0, 17.0);
    public GeoPoint last_pred = new GeoPoint(51.0, 17.0);

    private final View view;

    public MapUpdater(HomeFragment homeFragment, View v, Context c) {
        this.homeFragment = homeFragment;
        view = v;

        sondeMarker = new Marker(homeFragment.mapView);
        sondeMarker.setVisible(false);
        sondeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        Drawable ballon = ContextCompat.getDrawable(c, R.drawable.baloon);
        Bitmap b = ((BitmapDrawable)ballon).getBitmap();
        Bitmap bitmapResized = Bitmap.createScaledBitmap(b, b.getWidth()/5, b.getHeight()/5, false);
        sondeMarker.setIcon(new BitmapDrawable(homeFragment.getResources(), bitmapResized));
        homeFragment.mapView.getOverlays().add(sondeMarker);

        rsPathLine = new Polyline(homeFragment.mapView);
        rsPathLine.setEnabled(true);
        rsPathLine.setColor(Color.CYAN);
        rsPathLine.setInfoWindow(null);
        homeFragment.mapView.getOverlays().add(rsPathLine);

        rsPredLine = new Polyline(homeFragment.mapView);
        rsPredLine.setEnabled(true);
        rsPredLine.setColor(Color.CYAN);
        rsPredLine.setWidth(5f);
        rsPredLine.setInfoWindow(null);
        homeFragment.mapView.getOverlays().add(rsPredLine);

        rsPredMarker = new Marker(homeFragment.mapView);
        rsPredMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        Bitmap rstgb = ((BitmapDrawable)ContextCompat.getDrawable(c, R.drawable.target_rs)).getBitmap();
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

        shLastMarker = new Marker(homeFragment.mapView);
        shLastMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        shLastMarker.setIcon(homeFragment.getResources().getDrawable(R.drawable.loclastsh));
        homeFragment.mapView.getOverlays().add(shLastMarker);

        shPathLine = new Polyline(homeFragment.mapView);
        shPathLine.setEnabled(true);
        shPathLine.setColor(Color.MAGENTA);
        shPathLine.setInfoWindow(null);
        homeFragment.mapView.getOverlays().add(shPathLine);

        shPredLine = new Polyline(homeFragment.mapView);
        shPredLine.setEnabled(true);
        shPredLine.setColor(Color.MAGENTA);
        shPredLine.setWidth(5f);
        shPredLine.setInfoWindow(null);
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
        localPathLine.setInfoWindow(null);
        homeFragment.mapView.getOverlays().add(localPathLine);
    }

    public void updatePosition(Sonde sonde, String source, boolean vs_ok, double posdist, double bearing, int elapi_alt) {
        homeFragment.requireActivity().runOnUiThread(()->view.findViewById(R.id.tvwait).setVisibility(View.GONE));
        if (sonde == null) {
            homeFragment.requireActivity().runOnUiThread(() -> {
                try {
                    ((TextView) view.findViewById(R.id.textsid)).setText("N/A");
                    ((TextView) view.findViewById(R.id.textfreq)).setText("N/A");
                    ((TextView) view.findViewById(R.id.textalt)).setText("NO DATA");
                    ((TextView) view.findViewById(R.id.textaog)).setText("N/A m");
                    ((TextView) view.findViewById(R.id.textvspeed)).setText("N/A m/s");
                    ((TextView) view.findViewById(R.id.textposage)).setText("N/A s");
                    ((TextView) view.findViewById(R.id.textposdist)).setText("N/A km");
                    ((TextView) view.findViewById(R.id.textposhdg)).setText("N/A");
                    ((TextView) view.findViewById(R.id.textpossrc)).setText("N/A");
                    sondeMarker.setVisible(false);
                } catch (Exception ignored) {}
            });
            return;
        }

        last_pos = sonde.loc;

        try {
            long data_age = (new Date().getTime()/1000 - sonde.time/1000);

            String posdist_km = "NO GPS";
            String bearing_str = "N/A";
            if (!Double.isNaN(posdist)) {
                DecimalFormat df = new DecimalFormat("#.##");
                posdist_km = df.format(posdist) + " km";
            }

            if (!Double.isNaN(bearing)) {
                bearing_str = Math.round(bearing) + "°";
            }

            String final_posdist_km = posdist_km;
            String final_bearing_str = bearing_str;

            sondeMarkerSource = source;

            try {
                homeFragment.requireActivity().runOnUiThread(() -> {
                    try {
                    ((TextView) view.findViewById(R.id.textsid)).setText(sonde.sid == null ? "N/A" : sonde.sid);
                    ((TextView) view.findViewById(R.id.textfreq)).setText(sonde.freq == null ? "N/A" : sonde.freq);
                    ((TextView) view.findViewById(R.id.textalt)).setText(sonde.alt + " m");
                    ((TextView) view.findViewById(R.id.textaog)).setText(sonde.alt - elapi_alt + " m");
                    ((TextView) view.findViewById(R.id.textvspeed)).setText(vs_ok ? sonde.vspeed + " m/s" :
                                    ((sonde.vspeed >= 0 ? "+?" : "-?") + " (r: "+Math.abs(sonde.vspeed)+")"));
                    view.findViewById(R.id.imagevsarrow).setRotation(180 * ((sonde.vspeed < 0) ? 1 : 0));
                    ((TextView) view.findViewById(R.id.textposage)).setText(data_age + "s");
                    ((TextView) view.findViewById(R.id.textposage)).setTextColor(data_age > 120 ? Color.RED : ResourcesCompat.getColor(homeFragment.getResources(), android.R.color.secondary_text_dark, null));
                    ((TextView) view.findViewById(R.id.textposdist)).setText(final_posdist_km);
                    ((TextView) view.findViewById(R.id.textposhdg)).setText(final_bearing_str);
                    ((TextView) view.findViewById(R.id.textpossrc)).setText("(" + source + ")");

                    boolean hide = (Objects.equals(sondeMarkerSource, "LOCAL") && (data_age > 20))  ||
                            ((Objects.equals(sondeMarkerSource, "SONDEHUB") || Objects.equals(sondeMarkerSource, "RADIOSONDY")) && (data_age > 120));
                        sondeMarker.setVisible(!hide);
                    sondeMarker.setPosition(sonde.loc);
                    sondeMarker.setTitle("POSITION\n" + (float)sonde.loc.getLatitude() + " " + (float)sonde.loc.getLongitude() + "\n" + sonde.alt + "m\n" + new Date(sonde.time) + "\n" + source + "\n");

                    } catch (Exception ignored){}
                });
            } catch (Exception ignored){}
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void updateTraces(RadiosondyCollector rs_col, SondeHubCollector sh_col, LocalServerCollector lc_col) {
        try {
            homeFragment.requireActivity().runOnUiThread(() -> {
                try {
                    rsPathLine.setPoints(rs_col.getSondeTrack());
                    rsPredLine.setPoints(rs_col.getPrediction());
                    shPathLine.setPoints(sh_col.getSondeTrack());
                    shPredLine.setPoints(sh_col.getPrediction());
                    localPathLine.setPoints(lc_col.getSondeTrack());
                } catch (Exception e) {
                    if (!(e instanceof NullPointerException))
                        e.printStackTrace();
                }
            });
        }catch (Exception ignored){}
    }

    public void updatePredictionData(Point point, String source, long start_elapsed, long time_to_end, long data_age, float posdist, float bearing) {
        SimpleDateFormat time_format = new SimpleDateFormat("HH:mm:ss");
        time_format.setTimeZone(TimeZone.getTimeZone("UTC"));
        String time_elapsed = (start_elapsed < 0 ? "-" : "") + time_format.format(new Date(Math.abs(start_elapsed)));
        String str_to_end = (time_to_end < 0 ? "-" : "") + time_format.format(new Date(Math.abs(time_to_end)));

        String posdist_km = "N/A";
        String bearing_str = "N/A";
        if (!Double.isNaN(posdist)) {
            DecimalFormat df = new DecimalFormat("#.##");
            posdist_km = df.format(posdist);
        }
        if (!Double.isNaN(bearing)) {
            bearing_str = Math.round(bearing) + "°";
        }

        if (point != null)
            last_pred = point.point;
        else
            last_pred = last_pos;

        if (point == null)
            posdist_km = "NO PREDICT";
        else if (Double.isNaN(posdist))
            posdist_km = "NO GPS";
        else
            posdist_km += " km";

        String final_posdist_km = posdist_km;
        String final_bearing_str = bearing_str;

        try{
            homeFragment.requireActivity().runOnUiThread(() -> {
                try {
                ((TextView) view.findViewById(R.id.textpostime)).setText(start_elapsed == 0 ? "N/A s" : time_elapsed);
                ((TextView) view.findViewById(R.id.textpredtime)).setText(point == null ? "N/A s" : str_to_end);
                ((TextView) view.findViewById(R.id.textpreddist)).setText(final_posdist_km);
                ((TextView) view.findViewById(R.id.textpredhdg)).setText(final_bearing_str);
                ((TextView) view.findViewById(R.id.textpredage)).setText(data_age != -1 ? data_age+"s" : "N/A");
                ((TextView) view.findViewById(R.id.textpredsrc)).setText(" ("+source+")");
                } catch (Exception ignored){}
            });
        } catch (Exception ignored){}
    }

    public void updatePredictionMarkers(Point rs_pred, Point sh_pred, Point lc_pred) {
        try{
            homeFragment.requireActivity().runOnUiThread(() -> {
                try{
                    rsPredMarker.setVisible(rs_pred != null);
                    if (rs_pred != null) {
                        rsPredMarker.setPosition(rs_pred.point);
                        rsPredMarker.setTitle("PREDICTION\n"+rs_pred.point.getLatitude() + "\n" + rs_pred.point.getLongitude() + "\n" + rs_pred.alt + "m\n" + new Date(rs_pred.time) + "\nRADIOSONDY");
                    }

                    shPredMarker.setVisible(sh_pred != null);
                    if (sh_pred != null) {
                        shPredMarker.setPosition(sh_pred.point);
                        shPredMarker.setTitle("PREDICTION\n"+sh_pred.point.getLatitude() + "\n" + sh_pred.point.getLongitude() + "\n" + sh_pred.alt + "m\n" + new Date(sh_pred.time) + "\nSONDEHUB");
                    }

                    if(lc_pred != null && lc_pred.alt < 10000) {
                        localPredMarker.setVisible(true);
                        localPredMarker.setPosition(lc_pred.point);
                        localPredMarker.setTitle("LOCAL PREDICTION INTERPOLATION\n"+lc_pred.point.getLatitude() + "\n" + lc_pred.point.getLongitude() + "\n" + lc_pred.alt);
                    } else
                        localPredMarker.setVisible(false);
                } catch (Exception e){e.printStackTrace();} // setting point position can be dangerous
            });
        } catch (Exception ignored){}
    }

    public void updateLastMarkers(Sonde rs, Sonde sh, Sonde lc) {
        try {
            homeFragment.requireActivity().runOnUiThread(() -> {
                try {
                    if (rs != null) {
                        long rs_data_age = (new Date().getTime() / 1000 - rs.time / 1000);
                        rsLastMarker.setVisible((rs_data_age > 120));
                        rsLastMarker.setPosition(rs.loc);
                        rsLastMarker.setTitle("RADIOSONDY LAST POSITION\n" + rs.loc.getLatitude() + " " + rs.loc.getLongitude() + "\n" + rs.alt + "m\n" + new Date(rs.time) + "\n");
                    } else {
                        rsLastMarker.setVisible(false);
                    }

                    if (lc != null) {
                        long lc_data_age = (new Date().getTime() / 1000 - lc.time / 1000);
                        localLastMarker.setVisible((lc_data_age > 20));
                        localLastMarker.setPosition(lc.loc);
                        localLastMarker.setTitle("LOCAL LAST POSITION\n" + lc.loc.getLatitude() + " " + lc.loc.getLongitude() + "\n" + lc.alt + "m\n" + new Date(lc.time) + "\n");
                    } else {
                        localLastMarker.setVisible(false);
                    }

                    if (sh != null) {
                        long sh_data_age = (new Date().getTime() / 1000 - sh.time / 1000);
                        shLastMarker.setVisible((sh_data_age > 120));
                        shLastMarker.setPosition(sh.loc);
                        shLastMarker.setTitle("SONDEHUB LAST POSITION\n" + sh.loc.getLatitude() + " " + sh.loc.getLongitude() + "\n" + sh.alt + "m\n" + new Date(sh.time) + "\n");
                    } else {
                        shLastMarker.setVisible(false);
                    }
                } catch (Exception e) {e.printStackTrace();}
            });
        } catch (Exception ignored){}
    }

    public void updateStatus(int rs_color, int sh_color, int lc_color) {
        try {
            homeFragment.requireActivity().runOnUiThread(() -> {
                try {
                ((TextView) view.findViewById(R.id.textstatl)).setTextColor(lc_color);
                ((TextView) view.findViewById(R.id.textstats)).setTextColor(sh_color);
                ((TextView) view.findViewById(R.id.textstatr)).setTextColor(rs_color);
                } catch (Exception ignored) {}
            });
        }catch (Exception ignored){}
    }

    public void updateSondeSet(boolean vis) {
        try {
            homeFragment.requireActivity().runOnUiThread(() -> {
                try {
                    homeFragment.requireView().findViewById(R.id.tvsondeset).setVisibility(vis ? View.VISIBLE : View.GONE);
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}