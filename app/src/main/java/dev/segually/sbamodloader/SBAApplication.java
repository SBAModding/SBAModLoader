package dev.segually.sbamodloader;

import android.app.Application;

import dev.segually.sbamodloader.tools.Utilities;

public class SBAApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Utilities.init(this);
    }
}
