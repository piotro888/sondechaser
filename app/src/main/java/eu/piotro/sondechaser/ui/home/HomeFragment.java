package eu.piotro.sondechaser.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;


import eu.piotro.sondechaser.MainActivity;
import eu.piotro.sondechaser.R;

public class HomeFragment extends Fragment {
    public MapView mapView;
    public MyLocationNewOverlay locationOverlay;

    private MapUpdater updater;

    private View view = null;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);
        return view;
    }


    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        mapView = v.findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);

        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(v.getContext()), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        RotationGestureOverlay rotationOverlay = new RotationGestureOverlay(mapView);
        rotationOverlay.setEnabled(true);
        mapView.setMultiTouchControls(true);
        mapView.getOverlays().add(rotationOverlay);

        mapView.post(
                () -> mapView.zoomToBoundingBox(new BoundingBox(51, 17, 50, 16), false)
        );

        updater = new MapUpdater(this);

        v.findViewById(R.id.northbtn).setOnClickListener((_v) -> mapView.setMapOrientation(0));
        v.findViewById(R.id.locbtn).setOnClickListener((_v) -> {
            mapView.getController().animateTo(locationOverlay.getMyLocation());
        });

        v.findViewById(R.id.posbtn).setOnClickListener((_v) -> {
            mapView.getController().animateTo(updater.last_pos);
        });
        v.findViewById(R.id.predbtn).setOnClickListener((_v) -> {
            mapView.getController().animateTo(updater.last_pred);
        });
    }

    @Override
    public void onPause() {
        ((MainActivity)this.getActivity()).dataCollector.setMapUpdater(null);
        mapView.onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        mapView.onResume();
        ((MainActivity)this.getActivity()).dataCollector.setMapUpdater(updater);
        super.onResume();
    }
}