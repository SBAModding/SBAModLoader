package dev.segually.sbamodloader.tools;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.DatabaseErrorHandler;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;


import org.jetbrains.annotations.Nullable;

import java.io.File;

public class CustomContextWrapper extends ContextWrapper {
    Context sbaContext;
    Context gameContext;
    private final Context applicationContext;

    public CustomContextWrapper(Context gameContext, Context sbaContext) {
        this(gameContext, sbaContext, dev.segually.sbamodloader.hooks.GameRuntime.getGameApplication());
    }

    public CustomContextWrapper(Context gameContext, Context sbaContext, Application application) {
        super(gameContext);
        this.gameContext = gameContext;
        this.sbaContext = sbaContext;
        if (application != null) {
            this.applicationContext = application;
        } else {
            Application sbaApplication = (Application) sbaContext.getApplicationContext();
            GameApplication gameApplication = new GameApplication(sbaApplication);
            gameApplication.attach(new CustomContextWrapper(gameContext, sbaApplication, gameApplication));
            this.applicationContext = gameApplication;
        }
        this.getApplicationInfo().dataDir = Utilities.getExternalSBAModLoaderDirectory(gameContext.getPackageName(), "PersistentData").getAbsolutePath();
        // this prevents the game from resolving its own libraries
        // that way we can override them properly with our own versions
        this.getApplicationInfo().nativeLibraryDir = "";
    }

    public Context getOriginalActivity() {
        return sbaContext;
    }

//    @Override
//    public Resources getResources() {
//        return this.appContext.getResources();
//    }


//    @Override
//    public ApplicationInfo getApplicationInfo() {
//        return super.getApplicationInfo();
//    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return this.sbaContext.getSharedPreferences(name, mode);
    }

    public boolean deleteSharedPreferences(String name) {
        return this.sbaContext.deleteSharedPreferences(name);
    }

    public boolean moveSharedPreferencesFrom(Context sourceContext, String name) {
        return this.sbaContext.moveSharedPreferencesFrom(sourceContext, name);
    }

    @Override
    public File getDatabasePath(String name) {
        return sbaContext.getDatabasePath(name);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
        return sbaContext.openOrCreateDatabase(name, mode, factory);
    }

    @Override
    public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
                                               DatabaseErrorHandler errorHandler) {
        return sbaContext.openOrCreateDatabase(name, mode, factory, errorHandler);
    }

    @Override public boolean deleteDatabase(String name) { return sbaContext.deleteDatabase(name); }
    @Override public String[] databaseList() { return sbaContext.databaseList(); }
    @Override public boolean moveDatabaseFrom(Context source, String name) {
        return sbaContext.moveDatabaseFrom(source, name);
    }

    @Override
    public File getFilesDir() {
        return this.sbaContext.getFilesDir();
    }

    @Override
    public File getNoBackupFilesDir() {
        return this.sbaContext.getNoBackupFilesDir();
    }

    @Override
    public File getDir(String name, int mode) {
        return this.sbaContext.getDir(name, mode);
    }

    @Override
    public File getCodeCacheDir() {
        return this.sbaContext.getCodeCacheDir();
    }

    @Override
    public File getCacheDir() {
        return this.sbaContext.getCacheDir();
    }

    @Nullable
    @Override
    public File getExternalCacheDir() {
        return this.sbaContext.getExternalCacheDir();
    }


    @Override
    public File[] getExternalCacheDirs() {
        return this.sbaContext.getExternalCacheDirs();
    }

    @Override
    public File getExternalFilesDir(String type) {
        if (type == null) {
            return Utilities.getExternalSBAModLoaderDirectory(gameContext.getPackageName(), "PersistentData");
        }
        return new File(Utilities.getExternalSBAModLoaderDirectory(gameContext.getPackageName(), "PersistentData"), type);
    }

    @Override
    public Display getDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return this.sbaContext.getDisplay();
        }
        return null;
    }

    @Override
    public Object getSystemService(String name) {
        return this.sbaContext.getSystemService(name);
    }

    @Override
    public Context getBaseContext() {
        return super.getBaseContext();
    }

    @Override
    public Context getApplicationContext() {
        return applicationContext;
    }

    @Override
    public File getObbDir() {
        Log.i("f", "2");
        return null;
//        return this.appContext.getObbDir();
    }

    @Override
    public File[] getObbDirs() {
        return this.sbaContext.getObbDirs();
    }

    private static final class GameApplication extends Application {
        private final Application sbaApplication;

        GameApplication(Application sbaApplication) { this.sbaApplication = sbaApplication; }
        void attach(Context context) { attachBaseContext(context); }

        @Override public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
            sbaApplication.registerActivityLifecycleCallbacks(callback);
        }
        @Override public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
            sbaApplication.unregisterActivityLifecycleCallbacks(callback);
        }
        @Override public void registerComponentCallbacks(android.content.ComponentCallbacks callback) {
            sbaApplication.registerComponentCallbacks(callback);
        }
        @Override public void unregisterComponentCallbacks(android.content.ComponentCallbacks callback) {
            sbaApplication.unregisterComponentCallbacks(callback);
        }
    }
}
