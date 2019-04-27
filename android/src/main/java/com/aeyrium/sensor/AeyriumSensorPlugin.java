package com.aeyrium.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * AeyriumSensorPlugin
 */
public class AeyriumSensorPlugin implements EventChannel.StreamHandler {

    private static final String SENSOR_CHANNEL_NAME =
            "plugins.aeyrium.com/sensor";
    private static final int SENSOR_DELAY_MICROS = 1000 * 1000;//16 * 1000;
    private WindowManager mWindowManager;
    private SensorEventListener sensorEventListener;
    private SensorManager sensorManager;
    private Sensor sensor;
    private int mLastAccuracy;

//    private float[] mGData = new float[3];
//    private float[] mMData = new float[3];
//    private float[] mR = new float[16];
//    private float[] mI = new float[16];

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final EventChannel sensorChannel =
                new EventChannel(registrar.messenger(), SENSOR_CHANNEL_NAME);
        sensorChannel.setStreamHandler(
                new AeyriumSensorPlugin(registrar.context(), Sensor.TYPE_ROTATION_VECTOR, registrar));

    }

    private AeyriumSensorPlugin(Context context, int sensorType, Registrar registrar) {
        mWindowManager = registrar.activity().getWindow().getWindowManager();
        sensorManager = (SensorManager) context.getSystemService(context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(sensorType);
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        sensorEventListener = createSensorEventListener(events);
        sensorManager.registerListener(sensorEventListener, sensor, sensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onCancel(Object arguments) {
        if (sensorManager != null && sensorEventListener != null) {
            sensorManager.unregisterListener(sensorEventListener);
        }
    }

    SensorEventListener createSensorEventListener(final EventChannel.EventSink events) {
        return new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                if (mLastAccuracy != accuracy) {
                    mLastAccuracy = accuracy;
                }
            }

            @Override
            public void onSensorChanged(SensorEvent event) {
                if (mLastAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    return;
                }

                updateOrientation(event.values, events);
            }
        };
    }

    private void updateOrientation(float[] rotationVector, EventChannel.EventSink events) {
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);

        float[] originalOrientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, originalOrientation);
        double azimuth;

        final int worldAxisForDeviceAxisX;
        final int worldAxisForDeviceAxisY;

        // Remap the axes as if the device screen was the instrument panel,
        // and adjust the rotation matrix for the device orientation.
        switch (mWindowManager.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
            default:
                worldAxisForDeviceAxisX = SensorManager.AXIS_X;
                worldAxisForDeviceAxisY = SensorManager.AXIS_Z;
                azimuth = originalOrientation[0] - originalOrientation[2];
                break;
            case Surface.ROTATION_90:
                worldAxisForDeviceAxisX = SensorManager.AXIS_Z;
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_X;
                azimuth = originalOrientation[0] + originalOrientation[1] + Math.PI / 2;
                break;
            case Surface.ROTATION_180:
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_X;
                worldAxisForDeviceAxisY = SensorManager.AXIS_MINUS_Z;
                azimuth = originalOrientation[0] + originalOrientation[2];
                break;
            case Surface.ROTATION_270:
                worldAxisForDeviceAxisX = SensorManager.AXIS_MINUS_Z;
                worldAxisForDeviceAxisY = SensorManager.AXIS_X;
                azimuth = originalOrientation[0] - originalOrientation[1] - Math.PI / 2;
                break;
        }


        float[] adjustedRotationMatrix = new float[9];
        SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisForDeviceAxisX,
                worldAxisForDeviceAxisY, adjustedRotationMatrix);

        // Transform rotation matrix into azimuth/pitch/roll
        float[] orientation = new float[3];
        SensorManager.getOrientation(adjustedRotationMatrix, orientation);

//        SensorManager.getRotationMatrix(mR, mI, mGData, mMData);
//        float incl = SensorManager.getInclination(mI);

        double pitch = -orientation[1];
        double roll = -orientation[2];
        double[] sensorValues = new double[3];
        sensorValues[0] = Math.toDegrees(pitch);
        sensorValues[1] = Math.toDegrees(roll);
        sensorValues[2] = Math.toDegrees(azimuth);
//        sensorValues[3] = Math.toDegrees(incl);
        events.success(sensorValues);
    }
}