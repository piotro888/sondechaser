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


import eu.piotro.sondechaser.R;
import eu.piotro.sondechaser.ui.home.map.MapUpdater;

public class HomeFragment extends Fragment {
    public MapView mapView;
    public MyLocationNewOverlay locationOverlay;

    private MapUpdater updater;

    // HACK: Persist this Fragemnt and map markers and exceptions...
    private View view = null;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
//        if (view == null) {
        view = inflater.inflate(R.layout.fragment_home, container, false);
//        }
        return view;
    }
//    public boolean hasInitializedRootView = false;
//    private View rootView = null;

//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container , Bundle savedInstanceState) {
//        if (rootView == null) {
//            rootView = inflater.inflate(R.layout.fragment_home, container, false);
//        } else {
//            // Do not inflate the layout again.
//            // The returned View of onCreateView will be added into the fragment.
//            // However it is not allowed to be added twice even if the parent is same.
//            // So we must remove rootView from the existing parent view group
//            // (it will be added back).
//            //((ViewGroup)rootView.getParent()).removeView(rootView);
//        }
//        return rootView;
//    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        //System.out.println("VIEVCREATED");
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

        v.findViewById(R.id.northbtn).setOnClickListener((_v) -> mapView.setMapOrientation(0));
        v.findViewById(R.id.locbtn).setOnClickListener((_v) -> {
            mapView.getController().animateTo(locationOverlay.getMyLocation());
        });

//        if(!hasInitializedRootView) {
//            hasInitializedRootView = true;

            updater = new MapUpdater(this);
            Thread mut = new Thread(updater);
            mut.start();

            v.findViewById(R.id.posbtn).setOnClickListener((_v) -> {
                mapView.getController().animateTo(updater.last_pos);
            });
            v.findViewById(R.id.predbtn).setOnClickListener((_v) -> {
                mapView.getController().animateTo(updater.last_pred);
            });
        //}
    }

    @Override
    public void onPause() {
        mapView.onPause();
        updater.onPause();
        super.onPause();
    }

    @Override
    public void onResume() {
        mapView.onResume();
        updater.onResume();
        super.onResume();
    }
}