package eu.piotro.sondechaser.data.local;

import java.util.Date;

import eu.piotro.sondechaser.data.BlueAdapter;
import eu.piotro.sondechaser.data.LocalServerCollector;
import eu.piotro.sondechaser.data.Sonde;

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
        disable();
        // Address and mode should be configured already in passed object
        bt_runnable = blueAdapter.getRunnable();
        bt_thread = new Thread(bt_runnable);
        bt_thread.start();
    }

    @Override
    public void disable() {
        if(bt_thread == null)
            return;
        bt_thread.interrupt();
        blueAdapter.close();
    }

    @Override
    public void download() {
        if (!blueAdapter.isConnected())
            mStatus = LocalServerCollector.Status.RED;
        else
            mStatus = LocalServerCollector.Status.YELLOW;

        String line = bt_runnable.getLine();

        if (line == null)
            return;

        System.out.println("PARSE" + line);

        last_decoded = new Date().getTime();
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
