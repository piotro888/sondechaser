package eu.piotro.sondechaser.data.local;

import org.osmdroid.util.GeoPoint;

import java.util.Date;

import eu.piotro.sondechaser.handlers.BlueAdapter;
import eu.piotro.sondechaser.data.LocalServerCollector;
import eu.piotro.sondechaser.data.structs.Sonde;

public class MySondyDownloader implements LocalServerDownloader {
    private final BlueAdapter blueAdapter;

    private BlueAdapter.BlockedReaderThread bt_runnable;
    private Thread bt_thread;

    private Sonde lastSonde;
    private long last_decoded;

    private LocalServerCollector.Status mStatus = LocalServerCollector.Status.RED;

    public MySondyDownloader(BlueAdapter blueAdapter) {
        this.blueAdapter = blueAdapter;
    }

    @Override
    public void enable() {
        // Address and mode should be configured already in passed object
        bt_runnable = blueAdapter.getRunnable();
        bt_thread = new Thread(bt_runnable);
        bt_thread.start();
    }

    @Override
    public void disable() {
        System.out.println("disable mysondy");
        // close is handled by bt thread
        if (bt_thread != null) {
            bt_runnable.stop();
            bt_thread.interrupt();

            bt_runnable = null;
            bt_thread = null;
        }
    }

    @Override
    public void download() {
        try {
            if (!blueAdapter.isConnected())
                mStatus = LocalServerCollector.Status.RED;
            else if (new Date().getTime() - last_decoded < 10_000)
                mStatus = LocalServerCollector.Status.GREEN;
            else
                mStatus = LocalServerCollector.Status.YELLOW;

            String line = bt_runnable.getLine();

            if (line == null || line.length() == 0)
                return;

            if (line.charAt(0) != '1') // 1 prefix is position available
                return;

            String[] el = line.split("/");
            float lat = Float.parseFloat(el[4]);
            float lon = Float.parseFloat(el[5]);
            float alt = Float.parseFloat(el[6]);
            float vs = Float.parseFloat(el[7]);

            Sonde sonde = new Sonde();
            sonde.loc = new GeoPoint(lat, lon);
            sonde.alt = (int) Math.round(alt);
            sonde.vspeed = vs;
            sonde.time = new Date().getTime();
            lastSonde = sonde;

            //System.out.println("BtParse"+sonde.time);

            last_decoded = new Date().getTime();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Sonde getLastSonde() {
        return lastSonde;
    }

    @Override
    public long getLastDecoded() {
        return last_decoded;
    }

    @Override
    public LocalServerCollector.Status getStatus() {
        return mStatus;
    }


}
