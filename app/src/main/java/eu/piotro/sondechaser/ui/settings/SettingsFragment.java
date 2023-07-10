package eu.piotro.sondechaser.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import eu.piotro.sondechaser.MainActivity;
import eu.piotro.sondechaser.R;
import eu.piotro.sondechaser.handlers.BlueAdapter;
import eu.piotro.sondechaser.data.RadiosondyCollector;
import eu.piotro.sondechaser.data.SondeHubCollector;
import eu.piotro.sondechaser.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
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

        ((CheckBox)v.findViewById(R.id.set_awake)).setChecked(sharedPref.getBoolean("awake", false));

        ((TextView)v.findViewById(R.id.sonde_addr)).setText(sharedPref.getString("bt_addr", ""));
        String sel = sharedPref.getString("bt_model", "1: RS41");
        int seln = Integer.parseInt(sel.substring(0, sel.indexOf(":")));
        ((Spinner)v.findViewById(R.id.bt_probe)).setSelection(seln-1);
        ((TextView)v.findViewById(R.id.sonde_freq)).setText(sharedPref.getString("bt_freq", ""));

        ((Spinner)v.findViewById(R.id.local_spinner)).setSelection(sharedPref.getInt("local_src", 0));

        binding.setallText.setVisibility((sharedPref.getInt("local_src", 0) == 0) && (sharedPref.getString("rsid", "").equals("") || sharedPref.getString("shid", "").equals("")) ? View.VISIBLE : View.GONE);


        PopupMenu rsPopupMenu = new PopupMenu(context, v.findViewById(R.id.tfrs));
        PopupMenu shPopupMenu = new PopupMenu(context, v.findViewById(R.id.tfsh));
        PopupMenu btPopupMenu = new PopupMenu(context, v.findViewById(R.id.sonde_addr));

        v.findViewById(R.id.savebtn).setOnClickListener((view) -> {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("rsid", ((TextView)v.findViewById(R.id.tfrs)).getText().toString());
            editor.putString("shid", ((TextView)v.findViewById(R.id.tfsh)).getText().toString());
            editor.putBoolean("awake", ((CheckBox)v.findViewById(R.id.set_awake)).isChecked());

            editor.putString("lsip", ((TextView)v.findViewById(R.id.tfip)).getText().toString());
            
            editor.putString("bt_addr", ((TextView)v.findViewById(R.id.sonde_addr)).getText().toString());
            editor.putString("bt_model", ((Spinner)v.findViewById(R.id.bt_probe)).getSelectedItem().toString());
            editor.putString("bt_freq", ((TextView)v.findViewById(R.id.sonde_freq)).getText().toString());

            editor.putInt("local_src", ((Spinner)v.findViewById(R.id.local_spinner)).getSelectedItemPosition());

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

        v.findViewById(R.id.searchbt).setOnClickListener((view) -> {
            btPopupMenu.getMenu().clear();
            new BlueAdapter(getActivity()).fillMenu(getActivity(), btPopupMenu);
            btPopupMenu.show();
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

        btPopupMenu.setOnMenuItemClickListener((item) -> {
            String entry = item.getTitle().toString();
            if(!entry.contains("("))
                return false;
            ((TextView)v.findViewById(R.id.sonde_addr)).setText(entry);
            return true;
        });

        ((Spinner)v.findViewById(R.id.local_spinner)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                showSelSettings(v);
                if(position != 0)
                    binding.setallText.setVisibility(View.INVISIBLE);
            }
            public void onNothingSelected(AdapterView<?> parentView) {}
        });
        showSelSettings(v);

        getIPAddress(v.findViewById(R.id.ap0ip));
    }

    private void showSelSettings(View v) {
        boolean pipe = ((Spinner)v.findViewById(R.id.local_spinner)).getSelectedItem().toString().startsWith("PIPE");
        boolean bt = ((Spinner)v.findViewById(R.id.local_spinner)).getSelectedItem().toString().startsWith("MYS");

        int pv = (pipe ? View.VISIBLE : View.GONE);
        int bv = (bt ? View.VISIBLE : View.GONE);

        v.findViewById(R.id.tfip).setVisibility(pv);
        v.findViewById(R.id.textView4).setVisibility(pv);
        v.findViewById(R.id.ap0ip).setVisibility(pv);

        v.findViewById(R.id.bt_probe).setVisibility(bv);
        v.findViewById(R.id.sonde_addr).setVisibility(bv);
        v.findViewById(R.id.sonde_freq).setVisibility(bv);
        v.findViewById(R.id.textView11).setVisibility(bv);
        v.findViewById(R.id.textView16).setVisibility(bv);
        v.findViewById(R.id.textView19).setVisibility(bv);
        v.findViewById(R.id.searchbt).setVisibility(bv);
    }

    public void getIPAddress(TextView v) {
        StringBuilder ap0_ip = new StringBuilder();
        StringBuilder wlan_ip = new StringBuilder();

        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());

                if (!(intf.toString().startsWith("name:wlan0") || intf.toString().startsWith("name:ap0")))
                    continue;


                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String saddr = addr.getHostAddress();

                        if(saddr.startsWith("fe80::")) // skip link-local ig
                            continue;

                        if(intf.toString().startsWith("name:ap0")) {
                            ap0_ip.append(saddr).append(" ");
                        } else {
                            wlan_ip.append(saddr).append(" ");
                        }
                    }
                }
            }

        } catch (Exception ignored) { }

        if(ap0_ip.length() == 0)
            ap0_ip.append("N/A");
        if(wlan_ip.length() == 0)
            wlan_ip.append("N/A");

        v.setText("Phone IP addresses:\nap0: " + ap0_ip + "\nwlan0: "+wlan_ip);
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