package com.emesonsantana.BMDPedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;


import com.emesonsantana.BMDPedometer.util.Logger;
import com.emesonsantana.BMDPedometer.util.API26Wrapper;

public class AppUpdatedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (BuildConfig.DEBUG) Logger.log("app updated");
        if (Build.VERSION.SDK_INT >= 26) {
            API26Wrapper.startForegroundService(context, new Intent(context, StepService.class));
        } else {
            context.startService(new Intent(context, StepService.class));
        }
    }

}
