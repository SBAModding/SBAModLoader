package dev.segually.sbamodloader.hooks;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.io.File;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import dalvik.system.PathClassLoader;
import dev.segually.sbamodloader.tools.NativeLibraryManager;
import dev.segually.sbamodloader.tools.PatchManager;
import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.tools.CustomContextWrapper;

public final class GameRuntime {
    private static android.app.Application gameApplication;
    private GameRuntime() {}

    public static android.app.Application getGameApplication() { return gameApplication; }

    // The APK presented as the game's own when asset overlays are active (see ModPacks), or null.
    private static volatile String overlayApk;

    public static String getOverlayApk() { return overlayApk; }

    /**
     * Makes the game see {@code apk} as its own APK: Unity reads its data straight from the path the context
     * reports as the code path, so this is how overlay files become part of the game's assets.
     */
    public static void useOverlayApk(Context gameContext, String apk) {
        overlayApk = apk;
        android.content.pm.ApplicationInfo info = gameContext.getApplicationInfo();
        info.sourceDir = apk;
        info.publicSourceDir = apk;
    }

    // Set when a patch replaces the Application class declared in the game's manifest.
    private static boolean applicationOverridden;
    private static String applicationOverride;

    public static void initializeApplication(Context gameContext, Context sbaContext)
            throws ReflectiveOperationException {
        String className = gameContext.getApplicationInfo().className;
        if (applicationOverridden) {
            className = applicationOverride == null || applicationOverride.isEmpty()
                    || applicationOverride.equals("android.app.Application") ? null : applicationOverride;
        }
        if (gameApplication != null || className == null) return;
        android.app.Application application = (android.app.Application) gameContext.getClassLoader()
                .loadClass(className).getDeclaredConstructor().newInstance();
        Method attach = android.app.Application.class.getDeclaredMethod("attach", Context.class);
        attach.setAccessible(true);
        attach.invoke(application, new CustomContextWrapper(gameContext, sbaContext, application));
        gameApplication = application;
        application.onCreate();
        Log.i("GameRuntime", "Initialized application " + className);
    }

    public static Context prepareGameContext(Context gameContext, ClassLoader sbaLoader,
                                             PatchManager.ActivePatch patch,
                                             File overlayApk, java.util.List<File> packLibDirs)
            throws ReflectiveOperationException {
        android.content.pm.ApplicationInfo info = gameContext.getApplicationInfo();
        clearAppComponentFactory(info);
        ArrayList<String> apks = new ArrayList<>();
        String libraryPath = info.nativeLibraryDir;
        applicationOverridden = patch != null && patch.manifest.applicationClass != null;
        applicationOverride = applicationOverridden ? patch.manifest.applicationClass : null;
        if (patch != null) {
            // Patch classes and libraries are searched before the game's own.
            for (File dex : patch.dexFiles) apks.add(dex.getAbsolutePath());
            if (patch.libDir != null) libraryPath = patch.libDir.getAbsolutePath() + File.pathSeparator + libraryPath;
        }
        // With overlays the class loader reads the merged APK, so dex files a pack added to it (classes5.dex...)
        // load like the rest of the game's code.
        apks.add(overlayApk != null ? overlayApk.getAbsolutePath() : info.sourceDir);
        if (info.splitSourceDirs != null) Collections.addAll(apks, info.splitSourceDirs);
        StringBuilder libs = new StringBuilder();
        for (File dir : packLibDirs) libs.append(dir.getAbsolutePath()).append(File.pathSeparator);
        libraryPath = libs + libraryPath;
        ClassLoader gameLoader = new GameClassLoader(String.join(File.pathSeparator, apks),
                libraryPath, sbaLoader);
        return new ContextWrapper(gameContext) {
            @Override public ClassLoader getClassLoader() { return gameLoader; }
        };
    }

    /**
     * Android builds its own LoadedApk for the game whenever something resolves resources for its
     * ApplicationInfo (for example an ad SDK reading the app label). That instantiates the
     * manifest's appComponentFactory from the original, unpatched dex files, which can run code a
     * patch removed. Components are only created through our own hooks, so the factory is unneeded.
     */
    private static void clearAppComponentFactory(android.content.pm.ApplicationInfo info) {
        try {
            android.content.pm.ApplicationInfo.class.getField("appComponentFactory").set(info, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w("GameRuntime", "Could not clear appComponentFactory", e);
        }
    }

    private static final class GameClassLoader extends PathClassLoader {
        GameClassLoader(String dexPath, String libraryPath, ClassLoader parent) {
            super(dexPath, libraryPath, parent);
        }

        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (this) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    boolean platform = name.startsWith("java.") || name.startsWith("javax.") ||
                            name.startsWith("android.") || name.startsWith("dalvik.") ||
                            name.startsWith("sun.") || name.startsWith("org.w3c.") ||
                            name.startsWith("org.xml.") || name.startsWith("dev.segually.sbamodloader.");
                    if (!platform) {
                        try { loaded = findClass(name); }
                        catch (ClassNotFoundException ignored) { }
                    }
                    if (loaded == null) loaded = super.loadClass(name, false);
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        @Override public String findLibrary(String name) {
            String override = NativeLibraryManager.findLibrary(name);
            return override != null ? override : super.findLibrary(name);
        }
    }

    public static void install(Context gameContext) throws ReflectiveOperationException {
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Method currentThread = threadClass.getDeclaredMethod("currentActivityThread");
        currentThread.setAccessible(true);
        Object thread = currentThread.invoke(null);
        Field instrumentation = threadClass.getDeclaredField("mInstrumentation");
        instrumentation.setAccessible(true);
        Instrumentation original = (Instrumentation) instrumentation.get(thread);
        if (!(original instanceof GameInstrumentation)) {
            instrumentation.set(thread, new GameInstrumentation(original, gameContext));
        }
        Log.i("GameRuntime", "Installed activity loading");
    }

    private static final class GameInstrumentation extends Instrumentation {
        private final Instrumentation original;
        private final Context gameContext;
        private final Map<Activity, Intent> pendingIntents = Collections.synchronizedMap(new WeakHashMap<>());
        private final java.util.Set<Activity> gameActivities = Collections.newSetFromMap(new WeakHashMap<>());
        private final Map<Activity, dev.segually.sbamodloader.ui.GameOverlay> overlays = Collections.synchronizedMap(new WeakHashMap<>());

        GameInstrumentation(Instrumentation original, Context gameContext) {
            this.original = original;
            this.gameContext = gameContext;
        }

        @Override public Activity newActivity(ClassLoader loader, String name, Intent intent)
                throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            intent.setExtrasClassLoader(gameContext.getClassLoader());
            if (!InstrumentationHooks.isDynamicIntent(intent)) return original.newActivity(loader, name, intent);
            Intent target = InstrumentationHooks.resolveOriginalIntent(intent);
            if (target == null || target.getComponent() == null) {
                throw new InstantiationException("Missing dynamic activity intent");
            }
            target = new Intent(target);
            target.setExtrasClassLoader(gameContext.getClassLoader());
            if (intent.hasExtra(InstrumentationHooks.EXTRA_SBA_CONFIG)) {
                target.putExtra(InstrumentationHooks.EXTRA_SBA_CONFIG,
                        intent.<android.os.Parcelable>getParcelableExtra(InstrumentationHooks.EXTRA_SBA_CONFIG));
            }
            if (intent.hasExtra(InstrumentationHooks.EXTRA_TARGET_ORIENTATION)) {
                target.putExtra(InstrumentationHooks.EXTRA_TARGET_ORIENTATION,
                        intent.getIntExtra(InstrumentationHooks.EXTRA_TARGET_ORIENTATION,
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
            }
            Activity activity = original.newActivity(gameContext.getClassLoader(),
                    target.getComponent().getClassName(), target);
            pendingIntents.put(activity, target);
            gameActivities.add(activity);
            return activity;
        }

        private void prepare(Activity activity) {
            Intent target = pendingIntents.remove(activity);
            if (target == null) return;
            try {
                activity.setIntent(target);
                Context sbaBase = activity.getBaseContext();
                Field base = ContextWrapper.class.getDeclaredField("mBase");
                base.setAccessible(true);
                base.set(activity, new CustomContextWrapper(gameContext, sbaBase));
                if (gameApplication != null) {
                    Field application = Activity.class.getDeclaredField("mApplication");
                    application.setAccessible(true);
                    application.set(activity, gameApplication);
                }
                Field resources = ContextThemeWrapper.class.getDeclaredField("mResources");
                resources.setAccessible(true);
                resources.set(activity, null);
                // A launch activity supplied by a pack is not in the game's manifest; its superclass is.
                ActivityInfo info = null;
                for (Class<?> c = activity.getClass(); info == null && c != null; c = c.getSuperclass()) {
                    try {
                        info = gameContext.getPackageManager().getActivityInfo(
                                new android.content.ComponentName(gameContext.getPackageName(), c.getName()), 0);
                    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                        // try the superclass
                    }
                }
                if (info == null) throw new IllegalStateException("No manifest entry for " + activity.getClass().getName());
                android.content.res.Resources.Theme theme = activity.getResources().newTheme();
                theme.applyStyle(info.getThemeResource() != 0 ? info.getThemeResource()
                        : android.R.style.Theme_Black_NoTitleBar, true);
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    activity.setTheme(theme);
                } else {
                    Field oldTheme = ContextThemeWrapper.class.getDeclaredField("mTheme");
                    oldTheme.setAccessible(true);
                    oldTheme.set(activity, theme);
                }
                InstrumentationHooks.applyTargetOrientation(activity);
                // The game sees its own package name, and Android copies the activity's package name into the
                // window's attributes. Left like that, the window manager treats our window as the game's and
                // applies the game's Game Mode resolution downscale to it, which crops the game because the
                // process itself was never told. The window belongs to this app, so register it as ours.
                android.view.WindowManager.LayoutParams windowAttributes = activity.getWindow().getAttributes();
                windowAttributes.packageName = dev.segually.sbamodloader.BuildConfig.APPLICATION_ID;
                activity.getWindow().setAttributes(windowAttributes);
                if (target.hasExtra(InstrumentationHooks.EXTRA_SBA_CONFIG)) {
                    ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                    Context loadingTheme = new ContextThemeWrapper(sbaBase, androidx.appcompat.R.style.Theme_AppCompat);
                    decor.addView(LayoutInflater.from(loadingTheme).inflate(R.layout.loading_view, decor, false));
                    overlays.put(activity, dev.segually.sbamodloader.ui.GameOverlay.install(activity, sbaBase));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot prepare activity", e);
            }
        }

        @Override public void callActivityOnCreate(Activity activity, Bundle state) {
            prepare(activity);
            original.callActivityOnCreate(activity, state);
        }
        @Override public void callActivityOnCreate(Activity activity, Bundle state, PersistableBundle persistentState) {
            prepare(activity);
            original.callActivityOnCreate(activity, state, persistentState);
        }
        @Override public void callActivityOnResume(Activity activity) {
            if (gameActivities.contains(activity)) InstrumentationHooks.applyTargetOrientation(activity);
            original.callActivityOnResume(activity);
            dev.segually.sbamodloader.ui.GameOverlay overlay = overlays.get(activity);
            if (overlay != null) overlay.onActivityResumed();
        }
        @Override public void callActivityOnNewIntent(Activity activity, Intent intent) {
            Intent target = InstrumentationHooks.resolveOriginalIntent(intent);
            original.callActivityOnNewIntent(activity, target != null ? target : intent);
        }
        @Override public void callActivityOnDestroy(Activity activity) {
            pendingIntents.remove(activity);
            gameActivities.remove(activity);
            dev.segually.sbamodloader.ui.GameOverlay overlay = overlays.remove(activity);
            if (overlay != null) overlay.destroy();
            original.callActivityOnDestroy(activity);
        }
        @Override public boolean onException(Object object, Throwable error) {
            return original.onException(object, error);
        }

        private Intent wrap(Intent intent) {
            if (intent == null || intent.getComponent() == null || InstrumentationHooks.isDynamicIntent(intent)) return intent;
            String packageName = intent.getComponent().getPackageName();
            String className = intent.getComponent().getClassName();
            if (!packageName.equals(gameContext.getPackageName()) &&
                    !packageName.equals(dev.segually.sbamodloader.BuildConfig.APPLICATION_ID)) return intent;
            if (className.startsWith("dev.segually.sbamodloader.")) return intent;
            Intent wrapped = InstrumentationHooks.getInjectedIntent(intent);
            wrapped.setComponent(new android.content.ComponentName(
                    dev.segually.sbamodloader.BuildConfig.APPLICATION_ID,
                    dev.segually.sbamodloader.SecondaryStubActivity.class.getName()));
            return wrapped;
        }

        private ActivityResult start(Class<?> targetType, Context who, IBinder contextThread,
                                     IBinder token, Object target, Intent intent, int requestCode,
                                     Bundle options, UserHandle user) {
            try {
                Class<?>[] types = user == null
                        ? new Class<?>[] {Context.class, IBinder.class, IBinder.class, targetType, Intent.class, int.class, Bundle.class}
                        : new Class<?>[] {Context.class, IBinder.class, IBinder.class, targetType, Intent.class, int.class, Bundle.class, UserHandle.class};
                Method method = Instrumentation.class.getDeclaredMethod("execStartActivity", types);
                method.setAccessible(true);
                Object[] args = user == null
                        ? new Object[] {who, contextThread, token, target, wrap(intent), requestCode, options}
                        : new Object[] {who, contextThread, token, target, wrap(intent), requestCode, options, user};
                return (ActivityResult) method.invoke(original, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw new IllegalStateException("Cannot launch activity", cause);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot dispatch activity", e);
            }
        }

        public ActivityResult execStartActivity(Context who, IBinder thread, IBinder token,
                                               Activity target, Intent intent, int code, Bundle options) {
            return start(Activity.class, who, thread, token, target, intent, code, options, null);
        }
        public ActivityResult execStartActivity(Context who, IBinder thread, IBinder token,
                                               String target, Intent intent, int code, Bundle options) {
            return start(String.class, who, thread, token, target, intent, code, options, null);
        }
        public ActivityResult execStartActivity(Context who, IBinder thread, IBinder token,
                                               Activity target, Intent intent, int code, Bundle options, UserHandle user) {
            return start(Activity.class, who, thread, token, target, intent, code, options, user);
        }
    }
}