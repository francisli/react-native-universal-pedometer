package com.emesonsantana.BMDPedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.emesonsantana.BMDPedometer.util.Database;
import com.emesonsantana.BMDPedometer.util.Logger;
import com.emesonsantana.BMDPedometer.util.Utility;
import com.facebook.react.bridge.ReactApplicationContext;

public class StepReceiver extends BroadcastReceiver {
    private final BMDPedometerModule pedometerModule;
    public StepReceiver(BMDPedometerModule pedometerModule)
    {
        this.pedometerModule = pedometerModule;
    }

    public void register()
    {
        if (this.pedometerModule.reactContext != null)
        {
            IntentFilter filter = new IntentFilter(StepService.INTENT_STEPPED);
            this.pedometerModule.reactContext.registerReceiver(this, filter);
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        this.pedometerModule.sendPedometerUpdateEvent(intent.getIntExtra(StepService.INTENT_EXTRA_STEPPED, 0));
    }
}
