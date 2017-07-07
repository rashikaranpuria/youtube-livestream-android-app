package com.google.android.apps.watchme;

import android.app.Application;
import android.util.Log;

//import com.google.firebase.crash.FirebaseCrash;

import timber.log.Timber;

/**
 * Created by rashi on 1/7/17.
 */

public class WatchMeApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            Timber.uprootAll();
            Timber.plant(new Timber.DebugTree());
        }
        else Timber.plant(new FirebaseTree());
    }

    private static class FirebaseTree extends Timber.Tree {
        @Override
        protected void log(int priority, String tag, String message, Throwable t) {
            if (priority == Log.VERBOSE || priority == Log.DEBUG) {
                return;
            }

//            FirebaseCrash.log(message);

            if (t != null) {
//                FirebaseCrash.report(t);
            }
        }
    }
}
