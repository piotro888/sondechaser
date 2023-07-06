package eu.piotro.sondechaser.data.local;

import eu.piotro.sondechaser.data.LocalServerCollector;
import eu.piotro.sondechaser.data.Sonde;

public interface LocalServerDownloader {
    void download();

    Sonde getLastSonde();
    long getLastDecoded();
    LocalServerCollector.Status getStatus();

    void enable();
    void disable();
}
