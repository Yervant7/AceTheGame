package com.java.atg;

import android.app.Application;
import android.content.Context;

public class ATG extends Application {
    private static Context context;

    public void onCreate() {
        super.onCreate();
        ATG.context = getApplicationContext();
    }

    public static Context GetContext() {
        return ATG.context;
    }
}