package eu.piotro.sondechaser;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import org.osmdroid.config.Configuration;

import eu.piotro.sondechaser.data.DataCollector;
import eu.piotro.sondechaser.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    public DataCollector dataCollector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO: Main activity get destroyed on task-switch.
        //  It probably should be there (but where?), we need further separation of tasks and UI (could help loading times also)
        //  It should be created in app constructor (notification about running in backgroud?) and non re-crated on UI crate!!!!
        // main activy is destroyed and then recreated - kill all other threads on destroy
        dataCollector = new DataCollector(this);

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_guide)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        View headerView = navigationView.getHeaderView(0);
        TextView navUsername =  headerView.findViewById(R.id.nav_version);
        FrameLayout nav_badge =  headerView.findViewById(R.id.nav_badge);
        navUsername.setText("v"+BuildConfig.VERSION_NAME + BuildConfig.VERSION_SUFF + " " + getString(R.string.nav_header_subtitle));
        if(BuildConfig.GPLAY) {
            nav_badge.setVisibility(View.VISIBLE);
            setTheme(R.style.Theme_SondeChaser_GPLAY);
        }

        // Map configurations
        Configuration.getInstance().setUserAgentValue(getApplicationContext().getPackageName());

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this).setTitle("Location Permission").setMessage("Location Permission is needed for Sonde Chaser to run").setPositiveButton("OK", null).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
            }
        }
        Thread dataCollectorThread = new Thread(dataCollector);
        dataCollectorThread.start();
    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        if(BuildConfig.GPLAY)
            theme.applyStyle(R.style.Theme_SondeChaser_GPLAY, true);
        return theme;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        dataCollector.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        dataCollector.onResume();
        // DESIGN: Should collectors be paused???? (loosing track)
    }

    @Override
    protected void onPause() {
        super.onPause();
        dataCollector.onPause();
    }

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
}