package com.emesonsantana.BMDPedometer;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

public class StepContract {

    public static float STEP_IN_METERS = 0.762f;
    public long startDateStamp = 0;
    public int numberOfSteps = 0;

    public StepContract(long startDateStamp) {
        this.startDateStamp = startDateStamp;
    }
    public long endDateStamp() {
        return System.currentTimeMillis();
    }
    public float distance() {
        return this.numberOfSteps * STEP_IN_METERS;
    }
    public WritableMap getMap() {
        WritableMap map = Arguments.createMap();
        map.putDouble("startDate", this.startDateStamp);
        map.putDouble("endDate", this.endDateStamp());
        map.putInt("numberOfSteps", this.numberOfSteps);
        map.putDouble("distance", this.distance());
        return map;
    }
}
