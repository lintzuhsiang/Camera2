package com.example.camera2;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class SensorListener {

    private boolean running = false;

    public boolean isListening(){
        return running;
    }


    //sensor
    private SensorManager sensorManager;
    private SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if(event.values!=null) {
                switch (event.sensor.getStringType()) {
                    case Sensor.STRING_TYPE_GYROSCOPE:
//                    gyroScope_y = event.values[2];
//                        mOnHeadListener.onChanged((event.values[2]));
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };


}
