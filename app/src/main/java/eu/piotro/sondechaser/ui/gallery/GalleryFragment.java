package eu.piotro.sondechaser.ui.gallery;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import eu.piotro.sondechaser.R;
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

        v.findViewById(R.id.savebtn).setOnClickListener((view) -> {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("rsid", ((TextView)v.findViewById(R.id.tfrs)).getText().toString());
            editor.putString("shid", ((TextView)v.findViewById(R.id.tfsh)).getText().toString());
            editor.putString("lsip", ((TextView)v.findViewById(R.id.tfip)).getText().toString());
            editor.apply();
        });

    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}