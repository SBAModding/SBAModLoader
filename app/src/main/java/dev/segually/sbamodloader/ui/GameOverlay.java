package dev.segually.sbamodloader.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

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

    private View menuRoot;
    private View panel;
    private MaterialSwitch freezeSwitch;
    private TextView logcatText;
    private TextView frozenChip;
    private LogcatPanel logcat;
    private boolean frozen;
    private boolean menuOpen;
    private Object backCallback;
    private Window.Callback originalWindowCallback;

    private GameOverlay(Activity activity, Context sbaBase) {
        this.activity = activity;
        this.ui = DynamicColors.wrapContextIfAvailable(new ContextThemeWrapper(sbaBase, R.style.Theme_SBA));
        this.host = new FrameLayout(ui);
    }

    public static GameOverlay install(Activity activity, Context sbaBase) {
        GameOverlay overlay = new GameOverlay(activity, sbaBase);
        overlay.build();
        overlay.installBackHandling();
        return overlay;
    }

    private void build() {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(host, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        menuRoot = LayoutInflater.from(ui).inflate(R.layout.overlay_game_menu, host, false);
        host.addView(menuRoot);
        panel = menuRoot.findViewById(R.id.menu_panel);
        freezeSwitch = menuRoot.findViewById(R.id.menu_freeze_switch);
        logcatText = menuRoot.findViewById(R.id.menu_logcat_text);

        // Same glass treatment as the dashboard's navigation bar.
        int container = MaterialColors.getColor(panel, com.google.android.material.R.attr.colorSurfaceContainer);
        int onSurface = MaterialColors.getColor(panel, com.google.android.material.R.attr.colorOnSurface);
        float density = ui.getResources().getDisplayMetrics().density;
        MaterialShapeDrawable bg = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(28 * density).build());
        bg.setFillColor(ColorStateList.valueOf((container & 0x00FFFFFF) | (0xE6 << 24)));
        bg.setStroke(density, (onSurface & 0x00FFFFFF) | (0x2E << 24));
        panel.setBackground(bg);

        menuRoot.findViewById(R.id.menu_scrim).setOnClickListener(v -> closeMenu());
        menuRoot.findViewById(R.id.menu_freeze).setOnClickListener(v -> {
            setFrozen(!frozen);
            closeMenu();
        });
        // The log overlay is a developer tool.
        menuRoot.findViewById(R.id.menu_logcat).setVisibility(
                dev.segually.sbamodloader.SBASettings.getDeveloperFeatures(ui) ? View.VISIBLE : View.GONE);
        menuRoot.findViewById(R.id.menu_logcat).setOnClickListener(v -> {
            toggleLogcat();
            closeMenu();
        });
        menuRoot.findViewById(R.id.menu_exit).setOnClickListener(v -> exitToDashboard());

        frozenChip = new TextView(ui);
        frozenChip.setText(R.string.game_frozen);
        frozenChip.setTextColor(MaterialColors.getColor(panel, com.google.android.material.R.attr.colorOnSurface));
        frozenChip.setTextSize(12);
        frozenChip.setPadding(Math.round(14 * density), Math.round(6 * density), Math.round(14 * density), Math.round(6 * density));
        MaterialShapeDrawable chipBg = new MaterialShapeDrawable(ShapeAppearanceModel.builder()
                .setAllCornerSizes(ShapeAppearanceModel.PILL).build());
        chipBg.setFillColor(ColorStateList.valueOf((container & 0x00FFFFFF) | (0xD9 << 24)));
        frozenChip.setBackground(chipBg);
        frozenChip.setVisibility(View.GONE);
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        chipLp.topMargin = Math.round(12 * density);
        host.addView(frozenChip, chipLp);
        // The menu sits above the chip.
        menuRoot.bringToFront();
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
        freezeSwitch.setChecked(frozen);
        logcatText.setText(logcat != null && logcat.isShown2() ? R.string.game_menu_logcat_close : R.string.game_menu_logcat_open);
        menuRoot.setVisibility(View.VISIBLE);
        menuRoot.bringToFront();
        View scrim = menuRoot.findViewById(R.id.menu_scrim);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.post(() -> {
            panel.setTranslationX(-(panel.getWidth() + 48));
            panel.animate().translationX(0f).setDuration(200).start();
        });
    }

    private void closeMenu() {
        if (!menuOpen) return;
        menuOpen = false;
        View scrim = menuRoot.findViewById(R.id.menu_scrim);
        scrim.animate().alpha(0f).setDuration(140).start();
        panel.animate().translationX(-(panel.getWidth() + 48)).setDuration(160)
                .withEndAction(() -> {
                    if (!menuOpen) menuRoot.setVisibility(View.GONE);
                }).start();
    }

    // ---- actions -------------------------------------------------------------------------------

    private void toggleLogcat() {
        if (logcat == null) logcat = new LogcatPanel(ui);
        if (logcat.isShown2()) logcat.hide();
        else logcat.show(host);
        menuRoot.bringToFront();
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
        frozenChip.setVisibility(freeze ? View.VISIBLE : View.GONE);
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
        if (logcat != null) logcat.hide();
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            try {
                activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCallback);
            } catch (Throwable ignored) {
            }
        }
        handler.removeCallbacksAndMessages(null);
    }
}
