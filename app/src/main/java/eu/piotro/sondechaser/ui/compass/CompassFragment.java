package eu.piotro.sondechaser.ui.compass;

import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.osmdroid.util.GeoPoint;

import java.text.DecimalFormat;
import java.util.Objects;

import eu.piotro.sondechaser.MainActivity;
import eu.piotro.sondechaser.databinding.FragmentCompassBinding;

public class CompassFragment extends Fragment {

    private FragmentCompassBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCompassBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        try { ((MainActivity) this.requireActivity()).dataCollector.setCompassUpdater(this); } catch (Exception e) {e.printStackTrace();}
    }

    @Override
    public void onPause() {
        try { ((MainActivity) this.requireActivity()).dataCollector.setCompassUpdater(null); } catch (Exception e) {e.printStackTrace();}
        super.onPause();
    }
    public String getTarget() {
        if(binding == null)
            return "";
        return binding.spinner.getSelectedItem().toString();
    }
    public void update(Location myLocation, GeoPoint target, int target_alt, double compass_azimuth, int age) {
        if (binding == null)
            return;

        boolean use_gps_az = (myLocation != null) && myLocation.hasBearing() && myLocation.hasSpeed() && (myLocation.getSpeed() > (5f/3.6f));
        int bear = (((int)(Math.round(use_gps_az ? myLocation.getBearing() : compass_azimuth)))+360)%360;

        DecimalFormat distformat = new DecimalFormat("#.#");
        DecimalFormat cordformat = new DecimalFormat("#.#####");

        getActivity().runOnUiThread(()->{
            try {
                if (myLocation == null) {
                    binding.ctfycord.setText("N/A");
                    binding.ctfyalt.setText("N/A");
                    binding.ctfgpsa.setText("N/A");
                } else {
                    binding.ctfycord.setText(cordformat.format(myLocation.getLatitude()) + "° " + cordformat.format(myLocation.getLongitude()) + "° ");
                    binding.ctfyalt.setText(distformat.format(myLocation.getAltitude()) + "m");
                    binding.ctfgpsa.setText(myLocation.getAccuracy() + "m");
                }

                final String[] diro = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
                int idx = (((bear + (360 / 8 / 2))) % 360) / (360 / 8);
                binding.tvsource.setText(use_gps_az ? "GPS BEARING" : "COMPASS SENSOR");
                binding.ctftydir.setText(bear + "° (" + (diro[idx]) + ")");

                if (target != null) {
                    binding.ctftcord.setText(cordformat.format(target.getLatitude()) + "° " + cordformat.format(target.getLongitude()) + "° ");
                    binding.ctftalt.setText(distformat.format(target_alt) + "m");
                    binding.ctftage.setText(age == -1 ? "N/A" : age + "s");
                } else {
                    binding.ctftcord.setText("N/A");
                    binding.ctftalt.setText("N/A");
                    binding.ctftage.setText("N/A");
                }

                if (myLocation != null && target != null) {
                    binding.compcross.setVisibility(View.INVISIBLE);
                    double tbear = new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude()).bearingTo(target);
                    tbear -= bear;
                    int rtbear = ((int) Math.round(tbear) + 360) % 360;
                    double dist = new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude()).distanceToAsDouble(target);
                    binding.ctfdist.setText(distformat.format(dist) + "m");

                    double translation = Math.min(Math.log10(dist) * 120, 1000);
                    if (dist > 10000) {
                        binding.compdot.setVisibility(View.INVISIBLE);
                        binding.compdot.animate().y(-4 * 120).setDuration(200);
                    } else {
                        binding.compdot.setVisibility(View.VISIBLE);
                        binding.compdot.animate().y((float) -translation).setDuration(200);
                    }


                    binding.ctfbear.setText(rtbear + "°");
                    float rot = rtbear - binding.complay.getRotation();
                    if (rot > 180) {
                        rot = rot - 360;
                    } else if (rot < -180) {
                        rot += 360;
                    }
                    binding.complay.animate().rotationBy(rot).setDuration(200);
                } else {
                    binding.compcross.setVisibility(View.VISIBLE);
                    binding.ctfbear.setText("N/A");
                    binding.ctfdist.setText("N/A");
                }

                float rot = (360 - bear) - binding.compnorth.getRotation();
                if (rot > 180) {
                    rot = rot - 360;
                } else if (rot < -180) {
                    rot += 360;
                }
                binding.compnorth.animate().rotationBy(rot).setDuration(200);
            } catch (NullPointerException e) {e.printStackTrace();}
        });
    }
}