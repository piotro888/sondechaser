package eu.piotro.sondechaser.handlers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Set;
import java.util.UUID;

public class BlueAdapter {
    private final Activity rootActivity;
    private final BluetoothAdapter bluetoothAdapter;

    private BluetoothSocket bluetoothSocket;

    private BufferedReader reader;
    private PrintWriter writer;

    private String freqString = "403.000";
    private String deviceAddress = "";
    private int type = 2;

    private boolean failed = true;
    private boolean device_invalidate = false;

    private static final UUID WELL_KNOWN_SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public BlueAdapter(Activity rootActivity) {
        this.rootActivity = rootActivity;

        BluetoothManager bluMgr = (BluetoothManager) rootActivity.getBaseContext().getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluMgr.getAdapter();
    }

    private boolean permissionCheck() {
        if (ActivityCompat.checkSelfPermission(rootActivity, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            if(ActivityCompat.shouldShowRequestPermissionRationale(rootActivity, Manifest.permission.BLUETOOTH)) {
                rootActivity.runOnUiThread(()-> Toast.makeText(rootActivity, "No bluetooth permission. Enable in system app settings.", Toast.LENGTH_LONG).show() );
            } else {
                ActivityCompat.requestPermissions(rootActivity, new String[]{Manifest.permission.BLUETOOTH}, 2);
            }
            return false;
        }
        return true;
    }

    public void setDeviceAddress(String menuEntry) {
        if (menuEntry.contains("("))
            deviceAddress = menuEntry.substring(menuEntry.lastIndexOf('(')+1, menuEntry.length()-1);

        // everthing needs a restart, but closing here crashes the app
        device_invalidate = true;
    }
    public void setFrequency(String freq) {
        freqString = freq.replace(',', '.');
        updateFreq();
    }
    public void setType(String typestr) {
        this.type = Integer.parseInt(typestr.substring(0, typestr.indexOf(':')));
        updateFreq();
    }

    @SuppressLint("MissingPermission")
    private boolean createDevice() {
        if (!permissionCheck())
            return false;

        failed = true;

        close();

        if(deviceAddress == null)
            return false;

        BluetoothDevice device = this.bluetoothAdapter.getRemoteDevice(deviceAddress);

        if(device == null)
            return false;

        try {
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(WELL_KNOWN_SERIAL_UUID);
            device_invalidate = false;
            return true;
        } catch (IOException e) {
            // TODO: Replace with status text, or ignore for now in settings is probably enough
            //rootActivity.runOnUiThread(()-> Toast.makeText(rootActivity, "Bluetooth is disabled", Toast.LENGTH_LONG).show() );
            try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    @SuppressLint("MissingPermission")
    public void connectDevice() {
        if (!permissionCheck())
            return;

        failed = true;

        if(bluetoothSocket.isConnected())
            close();

        try {
            bluetoothSocket.connect(); // this fails when device is offline - just silently ignore it
            reader = new BufferedReader(new InputStreamReader(bluetoothSocket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(bluetoothSocket.getOutputStream()));
            updateFreq();
        } catch (Exception ignored) {}
    }

    public String readLine() {
        if(reader == null)
            return null;

        try {
            String line = reader.readLine();
            failed = false; // fail also before first line is received - device should constantly report
            return line;
        } catch (IOException e) {
            failed = true;
            e.printStackTrace();
        }
        return null;
    }

    private void updateFreq() {
        if (bluetoothSocket == null || reader == null)
            return;

        String cmd = "o{f="+freqString+"/tipo="+type+"}o";
        writer.println(cmd);
        writer.flush(); // <<<
    }

    @SuppressLint("MissingPermission")
    public void fillMenu(Activity activity, PopupMenu menu) {
        if (!permissionCheck())
            return;

        if (!bluetoothAdapter.isEnabled()) {
            menu.getMenu().add("Bluetooth is disabled.");
            menu.getMenu().add("Enable in system settings.");
            return;
        }

        Set<BluetoothDevice> paired = this.bluetoothAdapter.getBondedDevices();
        activity.runOnUiThread(()-> {
            for (BluetoothDevice dev : paired) {
                menu.getMenu().add(dev.getName() + " (" + dev.getAddress() + ")");
            }
            menu.getMenu().add("IF NOT LISTED HERE, PAIR DEVICE");
            menu.getMenu().add("FROM SYSTEM SETTINGS FIRST");
        });
    }

    public class BlockedReaderThread implements Runnable {
        private String lastLine;
        private boolean new_line = false;
        private boolean stop = false;
        private Object lock = new Object();

        @Override
        public void run() {
            stop = false;
            new_line = false;

            System.out.println("BTHREAD: start ");

            while (!stop && !createDevice()) {}

            System.out.println("BTHREAD: device created");

            while (!stop && !device_invalidate) {
                System.out.println("BTHREAD: loop ");
                String line = readLine(); // this fails in all cases (device offline, closed transmission error)
                if (line == null) {
                    System.out.println("BTTHREAD: Reconnect");
                    connectDevice();
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                } else {
                    System.out.println("BTHREAD: received " + line);
                }

                synchronized (lock) {
                    lastLine = line;
                    new_line = true;
                }

                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
            System.out.println("BTHREAD: exit");

            close();
        }

        public String getLine() {
            String line;
            synchronized (lock) {
                if (new_line) {
                    new_line = false;
                    line = lastLine;
                } else {
                    line = null;
                }
            }
            return line;
        }

        public void stop() {
            stop = true;
        }
    }

    public void close() {
        if(reader != null)
            try { reader.close(); } catch (IOException ignored) {}
        if (writer != null)
            writer.close();
        if (bluetoothSocket != null) {
            try { bluetoothSocket.close(); } catch (IOException ignored) {}
        }
    }

    private BlockedReaderThread thread = null;

    public BlockedReaderThread getRunnable() {
        if (thread == null)
            thread = new BlockedReaderThread();
        return thread;
    }

    public boolean isConnected() {
        if (bluetoothSocket == null)
            return false;
        return bluetoothSocket.isConnected() && !failed;
    }

    // NOTE: whaaaaaaaaa? Disabling BT on phone does not dissconect BtSocket, and it is *even* able to reconnect and continue working
    // after BT is DISABLED. I don't understand that. Streams will close but if socket will remain able to reconnect
}
