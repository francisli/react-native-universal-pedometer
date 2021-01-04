package com.emesonsantana.BMDPedometer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import android.os.Handler;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Pair;

import com.emesonsantana.BMDPedometer.util.Database;

import com.emesonsantana.BMDPedometer.util.Utility;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.List;

import static android.content.Context.SENSOR_SERVICE;

public class BMDPedometerModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

  ReactApplicationContext reactContext;

  public static int STOPPED = 0;
  public static int STARTING = 1;
  public static int RUNNING = 2;
  public static int ERROR_FAILED_TO_START = 3;
  public static int ERROR_NO_SENSOR_FOUND = 4;
  public static float STEP_IN_METERS = 0.762f;

  private int status;     // status of listener
  private float numSteps; // number of the steps
  private long startAt; //time stamp of when the measurement starts

  private SensorManager sensorManager; // Sensor manager
  private Sensor mSensor;             // Pedometer sensor returned by sensor manager
  private StepDetector stepDetector;
  private Database database;
  private Intent serviceIntent;
  public BMDPedometerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.reactContext.addLifecycleEventListener(this);

    this.startAt = 0;
    this.numSteps = 0;
    this.setStatus(BMDPedometerModule.STOPPED);
    this.stepDetector = new StepDetector();

    this.sensorManager = (SensorManager) this.reactContext.getSystemService(SENSOR_SERVICE);
    this.database = Database.getInstance(reactContext);
    this.serviceIntent = new Intent(reactContext, StepService.class);
  }

  @Override
  public String getName() {
    return "BMDPedometer";
  }

  @ReactMethod
  public void isStepCountingAvailable(Callback callback) {
    Sensor stepCounter = this.sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    Sensor accel = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    if (accel != null || stepCounter != null) {
      callback.invoke(null, true);
    } else {
      this.setStatus(BMDPedometerModule.ERROR_NO_SENSOR_FOUND);
      callback.invoke("Error: step counting is not available. BMDPedometerModule.ERROR_NO_SENSOR_FOUND", false);
    }
  }

  @ReactMethod
  public void isDistanceAvailable(Callback callback) {
    callback.invoke(null, true);
  }

  @ReactMethod
  public void isFloorCountingAvailable(Callback callback) {
    callback.invoke(null, true);
  }

  @ReactMethod
  public void isPaceAvailable(Callback callback) {
    callback.invoke(null, true);
  }

  @ReactMethod
  public void isCadenceAvailable(Callback callback) {
    callback.invoke(null, true);
  }

  @ReactMethod
  public void startPedometerUpdatesFromDate(Integer date) {
    if (this.status != BMDPedometerModule.RUNNING) {
      // If not running, then this is an async call, so don't worry about waiting
      // We drop the callback onto our stack, call start, and let start and the sensor callback fire off the callback down the road
      this.startAt = date;
      this.numSteps = database.getCurrentSteps() + database.getSteps(Utility.getToday());
      this.start();
    }
  }

  @ReactMethod
  public void stopPedometerUpdates() {
    if (this.status == BMDPedometerModule.RUNNING) {
      this.stop();
    }
  }

  @ReactMethod
  public void queryPedometerDataBetweenDates(Integer startDate, Integer endDate, Callback callback) {
    try {
      this.numSteps = this.database.getSteps(startDate, endDate);
      callback.invoke(null, this.getStepsParamsMap());
    } catch(Exception e) {
      callback.invoke(e.getMessage(), null);
    }

  }

  @ReactMethod
  public void tryAThing(Callback callback) {
    String value = database.getLastEntries(10).toString();
    callback.invoke(null, value);
  }

  @Override
  public void onHostResume() {
  }

  @Override
  public void onHostPause() {
  }

  @Override
  public void onHostDestroy() {
    this.stop();
  }

  /**
   * Called when the accuracy of the sensor has changed.
   */
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    //nothing to do here
    return;
  }

  /**
   * Sensor listener event.
   * @param event
   */
  public void onSensorChanged(SensorEvent event) {
    // Only look at step counter or accelerometer events
    if (event.sensor.getType() != this.mSensor.getType()) {
      return;
    }

    // If not running, then just return
    if (this.status == BMDPedometerModule.STOPPED) {
      return;
    }
    this.setStatus(BMDPedometerModule.RUNNING);

    if(this.mSensor.getType() == Sensor.TYPE_STEP_COUNTER){
      float steps = event.values[0];
      this.numSteps = steps;

      try {
        this.sendPedometerUpdateEvent(this.getStepsParamsMap());
      } catch(Exception e) {
        e.printStackTrace();
      }

    }else if(this.mSensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      stepDetector.updateAccel(
          event.timestamp, event.values[0], event.values[1], event.values[2]);
    }
  }

  public void step(long timeNs) {
    this.numSteps++;
    try {
      this.sendPedometerUpdateEvent(this.getStepsParamsMap());
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Start listening for pedometers sensor.
   */
  private void start() {
    reactContext.startService(serviceIntent);
  }

  /**
   * Stop listening to sensor.
   */
  private void stop() {
      //reactContext.stopService(this.serviceIntent);
      //this.setStatus(BMDPedometerModule.STOPPED);
  }

  private void setStatus(int status) {
    this.status = status;
  }

  private WritableMap getStepsParamsMap() {
    WritableMap map = Arguments.createMap();
    map.putInt("startDate", (int)this.startAt);
    map.putInt("endDate", (int)System.currentTimeMillis());
    map.putDouble("numberOfSteps", this.numSteps);
    map.putDouble("distance", this.numSteps * BMDPedometerModule.STEP_IN_METERS);
    
    return map;
  }

  private WritableMap getErrorParamsMap(int code, String message) {
    // Error object
     WritableMap map = Arguments.createMap();
    try {
      map.putInt("code", code);
      map.putString("message", message);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return map;
  }

  private void sendPedometerUpdateEvent(@Nullable WritableMap params) {
    this.reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("pedometerDataDidUpdate", params);
  }
}
