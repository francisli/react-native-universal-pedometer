package com.emesonsantana.BMDPedometer;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;

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

import static android.content.Context.SENSOR_SERVICE;

public class BMDPedometerModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

  ReactApplicationContext reactContext;

  public static int STOPPED = 0;
  public static int STARTING = 1;
  public static int RUNNING = 2;
  public static int ERROR_FAILED_TO_START = 3;
  public static int ERROR_NO_SENSOR_FOUND = 4;

  private int status;     // status of listener

  public long startTimeStamp;
  private SensorManager sensorManager; // Sensor manager
  private Database database;
  private Intent serviceIntent;
  private StepReceiver stepReceiver;

  public BMDPedometerModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.reactContext.addLifecycleEventListener(this);
    this.setStatus(BMDPedometerModule.STOPPED);
    this.sensorManager = (SensorManager) this.reactContext.getSystemService(SENSOR_SERVICE);
    this.database = Database.getInstance(reactContext);
    this.serviceIntent = new Intent(reactContext, StepService.class);
    stepReceiver = new StepReceiver(this);
    stepReceiver.register();
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
  // using double here because react does not support long params, but unix time stamps are long which do not fit in int
  public void startPedometerUpdatesFromDate(double date) {
    if (this.status != BMDPedometerModule.RUNNING) {
      this.startTimeStamp = (long)date;
      // seed the result in case the device records no steps soon
      this.sendPedometerUpdateEvent(numberOfStepsSinceAppStarted());
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
  // using double here because react does not support long params, but unix time stamps are long which do not fit in int
  public void queryPedometerDataBetweenDates(double startDate, double endDate, Callback callback) {
    try {
      StepContract step = new StepContract((long)startDate);
      step.numberOfSteps = this.database.getSteps((long)startDate, (long)endDate);
      callback.invoke(null, step.getMap());
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

  public int numberOfStepsSinceAppStarted()
  {
    return database.getCurrentSteps() + database.getSteps(Utility.getToday());
  }

  public void sendPedometerUpdateEvent(int stepCount) {
    StepContract step = new StepContract(this.startTimeStamp);
    step.numberOfSteps = stepCount;
    WritableMap params = step.getMap();
    this.reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("pedometerDataDidUpdate", params);
  }
}
