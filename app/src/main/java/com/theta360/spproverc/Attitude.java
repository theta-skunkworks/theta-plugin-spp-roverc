/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.theta360.spproverc;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


public class Attitude implements SensorEventListener {

    protected final static double RAD2DEG = (180/Math.PI);

    private float[] rotationMatrix      = new float[9];
    private float[] curAttitudeVal      = new float[3];

    public synchronized float getRadAzimath() {
        return this.curAttitudeVal[0];
    }
    public synchronized float getRadPitch() {
        return this.curAttitudeVal[1];
    }
    public synchronized float getRadRoll() {
        return this.curAttitudeVal[2];
    }

    public synchronized float getDegAzimath() {
        return (float)(this.curAttitudeVal[0] * RAD2DEG);
    }
    public synchronized float getDegPitch() {
        return (float)(this.curAttitudeVal[1] * RAD2DEG);
    }
    public synchronized float getDegRoll() {
        return (float)(this.curAttitudeVal[2] * RAD2DEG);
    }

    public Attitude(SensorManager sensorManager){
        int rate = SensorManager.SENSOR_DELAY_UI;
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), rate);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if ( event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR ) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            synchronized (this) {
                SensorManager.getOrientation(
                        rotationMatrix,
                        curAttitudeVal);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}

