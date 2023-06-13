package eu.piotro.sondechaser.ui.gallery;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.time.Duration;

import eu.piotro.sondechaser.MainActivity;
import eu.piotro.sondechaser.R;
import eu.piotro.sondechaser.data.RadiosondyCollector;
import eu.piotro.sondechaser.data.SondeHubCollector;
import eu.piotro.sondechaser.databinding.FragmentGalleryBinding;

public class GalleryFragment extends Fragment {

    private FragmentGalleryBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentGalleryBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        Context context = getActivity();
        SharedPreferences sharedPref = context.getSharedPreferences("eu.piotro.sondechaser.PSET", Context.MODE_PRIVATE);

        ((TextView)v.findViewById(R.id.tfrs)).setText(sharedPref.getString("rsid", ""));

        ((TextView)v.findViewById(R.id.tfsh)).setText(sharedPref.getString("shid", ""));

        ((TextView)v.findViewById(R.id.tfip)).setText(sharedPref.getString("lsip", ""));

        PopupMenu rsPopupMenu = new PopupMenu(context, v.findViewById(R.id.tfrs));
        PopupMenu shPopupMenu = new PopupMenu(context, v.findViewById(R.id.tfsh));

        v.findViewById(R.id.savebtn).setOnClickListener((view) -> {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("rsid", ((TextView)v.findViewById(R.id.tfrs)).getText().toString());
            editor.putString("shid", ((TextView)v.findViewById(R.id.tfsh)).getText().toString());
            editor.putString("lsip", ((TextView)v.findViewById(R.id.tfip)).getText().toString());
            editor.apply();
            ((MainActivity)getActivity()).dataCollector.initCollectors();
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show();
        });

        v.findViewById(R.id.searchrs).setOnClickListener((view) -> {
            rsPopupMenu.getMenu().clear();
            rsPopupMenu.getMenu().add("Fetching...");
            new RadiosondyCollector().fillMenu(getActivity(), rsPopupMenu);

            rsPopupMenu.show();
        });

        v.findViewById(R.id.searchsh).setOnClickListener((view) -> {
            shPopupMenu.getMenu().clear();
            shPopupMenu.getMenu().add("Fetching...");
            new SondeHubCollector().fillMenu(getActivity(), shPopupMenu);

            shPopupMenu.show();
        });

        rsPopupMenu.setOnMenuItemClickListener((item)->{
            String entry = item.getTitle().toString();
            if(entry.indexOf('(') == -1)
                return false;
            entry = entry.substring(0, entry.indexOf('(')-1);
            ((TextView)v.findViewById(R.id.tfrs)).setText(entry);
            return true;
        });

        shPopupMenu.setOnMenuItemClickListener((item)->{
            String entry = item.getTitle().toString();
            if(entry.equals("Sondehub:"))
                return false;
            ((TextView)v.findViewById(R.id.tfsh)).setText(entry);
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        Context context = getActivity();
        View v = getView();
        if (context == null || v == null)
            return;
        SharedPreferences sharedPref = context.getSharedPreferences("eu.piotro.sondechaser.PSET", Context.MODE_PRIVATE);

        ((TextView)v.findViewById(R.id.tfrs)).setText(sharedPref.getString("rsid", ""));

        ((TextView)v.findViewById(R.id.tfsh)).setText(sharedPref.getString("shid", ""));

        ((TextView)v.findViewById(R.id.tfip)).setText(sharedPref.getString("lsip", ""));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}