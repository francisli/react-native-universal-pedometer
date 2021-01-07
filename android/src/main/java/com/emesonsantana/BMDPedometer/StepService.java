package com.emesonsantana.BMDPedometer;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;

import com.emesonsantana.BMDPedometer.util.API23Wrapper;
import com.emesonsantana.BMDPedometer.util.API26Wrapper;
import com.emesonsantana.BMDPedometer.util.IsEmulatorConstant;
import com.emesonsantana.BMDPedometer.util.Utility;
import com.emesonsantana.BMDPedometer.util.Logger;
import com.emesonsantana.BMDPedometer.util.Database;

import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

public class StepService extends Service implements SensorEventListener  {
    public final static String INTENT_STEPPED = "STEPPED";
    public static final String INTENT_EXTRA_STEPPED = "STEPPED";

    public final static int NOTIFICATION_ID = 1;

    private final static long MICROSECONDS_IN_ONE_MINUTE = 60000000;
    private final static long MILLISECONDS_IN_ONE_SECOND = 1000;
    private final static long SAVE_OFFSET_TIME = AlarmManager.INTERVAL_HOUR;
    private final static int SAVE_OFFSET_STEPS = 500;

    private int steps;
    private int lastSaveSteps;
    private long lastSaveTime;
    private long debugTimer = System.currentTimeMillis();

    private final BroadcastReceiver shutdownReceiver = new ShutdownReceiver();
    private final StepDetector stepDetector = new StepDetector();

    @Override
    public void onAccuracyChanged(final Sensor sensor, int accuracy) {
        // nobody knows what happens here: step value might magically decrease
        // when this method is called...
        if (BuildConfig.DEBUG) Logger.log(sensor.getName() + " accuracy changed: " + accuracy);
    }

    /**
     * Sensor listener event.
     * @param event
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onSensorChanged(final SensorEvent event) {

        boolean isKnownSensorType = false;
        int nextStepCount = 0;

        if(event.sensor.getType() == Sensor.TYPE_STEP_COUNTER){
            isKnownSensorType = true;
            nextStepCount = (int) event.values[0];
        } else if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            isKnownSensorType = true;
            nextStepCount = stepDetector.updateAccel(
                    event.timestamp, event.values[0], event.values[1], event.values[2]);
        }
        // Only look at step counter or accelerometer events
        if (!isKnownSensorType)
            return;

        // during emulator, let us take a step if a second has passed
        // so we don't have to be very precise with sensors to see activity
        if (IsEmulatorConstant.isEmulator && nextStepCount == 0) {
            long now = System.currentTimeMillis();
            long diff = now - debugTimer;
            if ( diff >= MILLISECONDS_IN_ONE_SECOND) {
                debugTimer = now;
                nextStepCount = 1;
            }
        }

        this.steps += nextStepCount;

        save();

        if (nextStepCount > 0) {
            Intent stepped = new Intent(INTENT_STEPPED);
            stepped.putExtra(INTENT_EXTRA_STEPPED, this.steps);
            sendBroadcast(stepped);
        }
    }

    /**
     * @return true, if notification was updated
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private boolean save() {
        if (steps > lastSaveSteps + SAVE_OFFSET_STEPS ||
                (steps > 0 && System.currentTimeMillis() > lastSaveTime + SAVE_OFFSET_TIME)) {
            if (BuildConfig.DEBUG) Logger.log(
                    "saving steps: steps=" + steps + " lastSave=" + lastSaveSteps +
                            " lastSaveTime=" + new Date(lastSaveTime));
            Database db = Database.getInstance(this);
            if (db.getSteps(Utility.getToday()) == Integer.MIN_VALUE) {
                int pauseDifference = steps -
                        getSharedPreferences("pedometer", Context.MODE_PRIVATE)
                                .getInt("pauseCount", steps);
                db.insertNewDay(Utility.getToday(), steps - pauseDifference);
                if (pauseDifference > 0) {
                    // update pauseCount for the new day
                    getSharedPreferences("pedometer", Context.MODE_PRIVATE).edit()
                            .putInt("pauseCount", steps).apply();
                }
            }
            db.saveCurrentSteps(steps);
            db.close();
            lastSaveSteps = steps;
            lastSaveTime = System.currentTimeMillis();
            showNotification(); // update notification

            return true;
        } else {
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void showNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            startForeground(NOTIFICATION_ID, getNotification(this));
        } else if (getSharedPreferences("pedometer", Context.MODE_PRIVATE)
                .getBoolean("notification", true)) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .notify(NOTIFICATION_ID, getNotification(this));
        }
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        reRegisterSensor();
        registerBroadcastReceiver();
        if (!save()) {
            showNotification();
        }

        // restart service every hour to save the current step count
        long nextUpdate = Math.min(Utility.getTomorrow(),
                System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR);
        if (BuildConfig.DEBUG) Logger.log("next update: " + new Date(nextUpdate).toLocaleString());
        AlarmManager am =
                (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent
                .getService(getApplicationContext(), 2, new Intent(this, StepService.class),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 23) {
            API23Wrapper.setAlarmWhileIdle(am, AlarmManager.RTC, nextUpdate, pi);
        } else {
            am.set(AlarmManager.RTC, nextUpdate, pi);
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) Logger.log("SensorListener onCreate");
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (BuildConfig.DEBUG) Logger.log("sensor service task removed");
        // Restart service in 500 ms
        ((AlarmManager) getSystemService(Context.ALARM_SERVICE))
                .set(AlarmManager.RTC, System.currentTimeMillis() + 500, PendingIntent
                        .getService(this, 3, new Intent(this, StepService.class), 0));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (BuildConfig.DEBUG) Logger.log("SensorListener onDestroy");
        try {
            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            sm.unregisterListener(this);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @SuppressLint("StringFormatInvalid")
    public Notification getNotification(final Context context) {
        if (BuildConfig.DEBUG) Logger.log("getNotification");
        SharedPreferences prefs = context.getSharedPreferences("pedometer", Context.MODE_PRIVATE);
        int goal = prefs.getInt("goal", 10000);
        Database db = Database.getInstance(context);
        int today_offset = db.getSteps(Utility.getToday());
        if (steps == 0)
            steps = db.getCurrentSteps(); // use saved value if we haven't anything better
        db.close();
        Notification.Builder notificationBuilder =
                Build.VERSION.SDK_INT >= 26 ? API26Wrapper.getNotificationBuilder(context) :
                        new Notification.Builder(context);
        if (steps > 0) {
            if (today_offset == Integer.MIN_VALUE) today_offset = -steps;
            NumberFormat format = NumberFormat.getInstance(Locale.getDefault());
            // notificationBuilder.setProgress(goal, today_offset + steps, false).setContentText(
            //         today_offset + steps >= goal ?
            //                 context.getString(R.string.goal_reached_notification,
            //                         format.format((today_offset + steps))) :
            //                 context.getString(R.string.notification_text,
            //                         format.format((goal - today_offset - steps)))).setContentTitle(
            //         format.format(today_offset + steps) + " " + context.getString(R.string.steps));
        } else { // still no step value?
            // notificationBuilder.setContentText(
            //         context.getString(R.string.your_progress_will_be_shown_here_soon))
            //         .setContentTitle(context.getString(R.string.notification_title));
        }
        notificationBuilder.setPriority(Notification.PRIORITY_MIN).setShowWhen(false)
                .setContentIntent(PendingIntent
                        .getActivity(context, 0, new Intent(context, BMDPedometerModule.class),
                                PendingIntent.FLAG_UPDATE_CURRENT));
                //.setSmallIcon(R.drawable.ic_notification).setOngoing(true);
        return notificationBuilder.build();
    }

    private void registerBroadcastReceiver() {
        if (BuildConfig.DEBUG) Logger.log("register broadcastreceiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(shutdownReceiver, filter);
    }

    @SuppressLint("NewApi")
    private void reRegisterSensor() {
        if (BuildConfig.DEBUG) Logger.log("re-register sensor listener");
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        try {
            sm.unregisterListener(this);
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Logger.log(e);
            e.printStackTrace();
        }

        if (BuildConfig.DEBUG) {
            Logger.log("step sensors: " + sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size());
            if (sm.getSensorList(Sensor.TYPE_STEP_COUNTER).size() > 1) {
                Logger.log("default: " + sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER).getName());
            }
            else if (sm.getSensorList(Sensor.TYPE_ACCELEROMETER).size() > 1) {
                Logger.log("default: " + sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER).getName());
            }
            else {
                Logger.log("no available sensors");
            }

        }

        Sensor sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) {
            sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        if (sensor != null) {
            // enable batching with delay of max 5 min
            sm.registerListener(this, sensor,
                    SensorManager.SENSOR_DELAY_NORMAL, (int) (5 * MICROSECONDS_IN_ONE_MINUTE));
        }
    }
}
