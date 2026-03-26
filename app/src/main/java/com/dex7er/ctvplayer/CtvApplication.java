package com.dex7er.ctvplayer;

import android.app.Application;

import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

public class CtvApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        OneSignal.getDebug().setLogLevel(LogLevel.VERBOSE);
        OneSignal.initWithContext(this, "453a2eb8-9551-4dca-bc7d-2d656d5648a7");
        OneSignal.getNotifications().requestPermission(false, Continue.none());
    }
}
