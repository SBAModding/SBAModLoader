package dev.segually.sbamodloader.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import dev.segually.sbamodloader.ui.compose.GameOverlayHost;
import dev.segually.sbamodloader.ui.compose.LogcatSource;
import dev.segually.sbamodloader.ui.compose.OverlayActions;
import dev.segually.sbamodloader.ui.compose.OverlayState;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import dev.segually.sbamodloader.BuildConfig;
import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.RestartActivity;

/**
 * The in-game menu. Pressing back (button, 3-button navigation or the gesture) opens a sidebar with
 * three actions: freeze the game, show a logcat overlay, and exit to the dashboard.
 */
public final class GameOverlay {
    private static final String TAG = "GameOverlay";

    private final Activity activity;
    private final Context ui;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final FrameLayout host;

    private final androidx.compose.runtime.MutableState<OverlayState> uiState =
            androidx.compose.runtime.SnapshotStateKt.mutableStateOf(new OverlayState(), androidx.compose.runtime.SnapshotStateKt.structuralEqualityPolicy());
    private final GameOverlayHost.Env env;
    private final LogcatSource logSource = new LogcatSource(lines -> {
        uiState.setValue(copy(uiState.getValue(), null, null, null, null, lines));
        return kotlin.Unit.INSTANCE;
    });
    private boolean frozen;
    private boolean menuOpen;
    private boolean logcatOpen;
    private Object backCallback;
    private Window.Callback originalWindowCallback;

    /** The overlay's actions, called from its Compose menu. */
    private final OverlayActions actions = new OverlayActions() {
        @Override public void closeMenu() { GameOverlay.this.closeMenu(); }
        @Override public void toggleFreeze() { setFrozen(!frozen); closeMenu(); }
        @Override public void toggleLogcat() { GameOverlay.this.toggleLogcat(); closeMenu(); }
        @Override public void exitToDashboard() { GameOverlay.this.exitToDashboard(); }
        @Override public void clearLog() { logSource.clear(); }
        @Override public void hideLog() { setLogcat(false); }
    };

    private static OverlayState copy(OverlayState s, Boolean menu, Boolean frozen, Boolean developer, Boolean logcat, java.util.List<String> lines) {
        return new OverlayState(menu != null ? menu : s.getMenuOpen(), frozen != null ? frozen : s.getFrozen(),
                developer != null ? developer : s.getDeveloper(), logcat != null ? logcat : s.getLogcatOpen(),
                lines != null ? lines : s.getLogLines());
    }

    private GameOverlay(Activity activity, Context sbaBase) {
        this.activity = activity;
        this.ui = Theming.wrap(sbaBase);
        this.env = GameOverlayHost.env(activity);
        this.host = new FrameLayout(ui);
    }

    public static GameOverlay install(Activity activity, Context sbaBase) {
        GameOverlay overlay = new GameOverlay(activity, sbaBase);
        overlay.build();
        overlay.installBackHandling();
        if (BuildConfig.DEBUG) overlay.debugOpen();
        return overlay;
    }

    private void build() {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(host, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        uiState.setValue(copy(uiState.getValue(), null, null,
                dev.segually.sbamodloader.SBASettings.getDeveloperFeatures(ui), null, null));
        GameOverlayHost.attach(host, env, uiState, actions);
    }

    /** Debug builds: a marker file in the app's files folder opens the menu (and the log) a few seconds in, for layout checks. */
    private void debugOpen() {
        handler.postDelayed(() -> {
            java.io.File marker = new java.io.File("/sdcard/Android/data/" + BuildConfig.APPLICATION_ID + "/files/sba_overlay");
            if (!marker.exists()) return;
            if (marker.length() > 5) toggleLogcat();
            openMenu();
        }, 6000);
    }

    // ---- back handling -------------------------------------------------------------------------

    private void installBackHandling() {
        // Predictive back / gesture navigation (API 33+).
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                OnBackInvokedCallback cb = this::onBackPressed;
                activity.getOnBackInvokedDispatcher()
                        .registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, cb);
                backCallback = cb;
            } catch (Throwable t) {
                Log.w(TAG, "Could not register back callback", t);
            }
        }
        // Legacy back key (3-button navigation, and gesture navigation when the system sends key events).
        try {
            Window window = activity.getWindow();
            originalWindowCallback = window.getCallback();
            if (originalWindowCallback != null) {
                Window.Callback original = originalWindowCallback;
                Window.Callback proxy = (Window.Callback) Proxy.newProxyInstance(
                        Window.Callback.class.getClassLoader(), new Class<?>[]{Window.Callback.class},
                        (p, method, args) -> {
                            if (method.getName().equals("dispatchKeyEvent") && args != null && args[0] instanceof KeyEvent) {
                                KeyEvent e = (KeyEvent) args[0];
                                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                                    if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) onBackPressed();
                                    return true;
                                }
                            }
                            try {
                                return method.invoke(original, args);
                            } catch (InvocationTargetException ex) {
                                throw ex.getCause() != null ? ex.getCause() : ex;
                            }
                        });
                window.setCallback(proxy);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not intercept the back key", t);
        }
    }

    private void onBackPressed() {
        if (menuOpen) closeMenu();
        else openMenu();
    }

    // ---- sidebar -------------------------------------------------------------------------------

    private void openMenu() {
        if (menuOpen) return;
        menuOpen = true;
        uiState.setValue(copy(uiState.getValue(), true, null, null, null, null));
    }

    private void closeMenu() {
        if (!menuOpen) return;
        menuOpen = false;
        uiState.setValue(copy(uiState.getValue(), false, null, null, null, null));
    }

    // ---- actions -------------------------------------------------------------------------------

    private void toggleLogcat() {
        setLogcat(!logcatOpen);
    }

    private void setLogcat(boolean open) {
        logcatOpen = open;
        if (open) logSource.start(); else logSource.stop();
        uiState.setValue(copy(uiState.getValue(), null, null, null, open, null));
    }

    private Object unityPlayer() {
        for (Class<?> c = activity.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("mUnityPlayer");
                f.setAccessible(true);
                return f.get(activity);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            }
        }
        return null;
    }

    private boolean applyUnityPause(boolean pause) {
        Object player = unityPlayer();
        if (player == null) return false;
        Method m = findMethod(player.getClass(), pause ? "pause" : "resume");
        if (m == null) return false;
        try {
            m.invoke(player);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Unity pause/resume failed", e);
            return false;
        }
    }

    private void setFrozen(boolean freeze) {
        if (freeze == frozen) return;
        if (!applyUnityPause(freeze)) {
            Toast.makeText(ui, R.string.game_freeze_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        frozen = freeze;
        uiState.setValue(copy(uiState.getValue(), null, freeze, null, null, null));
    }

    /** Unity resumes itself when the activity does; keep it frozen if that is what the user asked for. */
    public void onActivityResumed() {
        if (frozen) handler.post(() -> applyUnityPause(true));
    }

    private void exitToDashboard() {
        // The activity's context reports the game's package, so name our own package explicitly.
        Intent restart = new Intent()
                .setComponent(new ComponentName(BuildConfig.APPLICATION_ID, RestartActivity.class.getName()))
                .putExtra(RestartActivity.EXTRA_PID, Process.myPid())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            activity.startActivity(restart);
        } catch (RuntimeException e) {
            Log.e(TAG, "Could not start the restart helper", e);
            Toast.makeText(ui, R.string.game_exit_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        // The restarter kills this process too; this is the fallback if it is slow.
        handler.postDelayed(() -> Process.killProcess(Process.myPid()), 3000);
    }

    public void destroy() {
        logSource.stop();
        GameOverlayHost.release(activity);
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            } catch (Throwable ignored) {
            }
        }
        handler.removeCallbacksAndMessages(null);
    }
}
