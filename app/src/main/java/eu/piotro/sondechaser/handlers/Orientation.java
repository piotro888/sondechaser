package eu.piotro.sondechaser.handlers;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class Orientation implements SensorEventListener {

    private float[] lastAccel = new float[3];
    private float[] lastMag = new float[3];

    private double azimuth;
    private double pitch;
    private double roll;

    public Orientation(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(SENSOR_SERVICE);

        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
            return;

        // Gets the value of the sensor that has been changed
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                lastAccel = event.values.clone();
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                lastMag = event.values.clone();
                break;
        }

        float[] rotationMatrix_R = new float[16];
        float[] rotationMatrix_I = new float[16];
        if (SensorManager.getRotationMatrix(rotationMatrix_R, rotationMatrix_I, lastAccel, lastMag)) {
            float[] values = new float[3];
            SensorManager.getOrientation(rotationMatrix_R, values);
            azimuth = Math.toDegrees(values[0]);
            pitch = Math.toDegrees(values[1]);
            roll = Math.toDegrees(values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    public double getAzimuth() {
        return azimuth;
    }
}
