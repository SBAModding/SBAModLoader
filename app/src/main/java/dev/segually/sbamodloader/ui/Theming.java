package dev.segually.sbamodloader.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.SBASettings;

/**
 * Applies the appearance settings: light, dark or system, and where the colours come from.
 *
 * <p>The base theme ({@code Theme.SBA}) carries a complete static scheme in the app's green. On Android 12 and newer
 * it is replaced by Material You (the wallpaper's colours) or by a scheme generated from the chosen accent colour.
 * Older versions always use the static scheme, since Android has no dynamic colours there.
 */
public final class Theming {
    private Theming() {
    }

    /** Call before {@code super.onCreate} of every activity that uses {@code Theme.SBA}. */
    public static void apply(Activity activity) {
        Context app = activity.getApplicationContext();
        AppCompatDelegate.setDefaultNightMode(nightMode(SBASettings.getThemeMode(app)));
        applyColors(activity, app);
    }

    private static void applyColors(Activity activity, Context app) {
        if (!DynamicColors.isDynamicColorAvailable()) return;
        if (SBASettings.getMaterialYou(app)) {
            DynamicColors.applyToActivityIfAvailable(activity);
            return;
        }
        int accent = SBASettings.getAccentColor(app);
        if (accent != 0) {
            DynamicColors.applyToActivityIfAvailable(activity,
                    new DynamicColorsOptions.Builder().setContentBasedSource(accent).build());
        }
    }

    /** Whether picking an accent colour does anything on this device. */
    public static boolean accentSupported() {
        return DynamicColors.isDynamicColorAvailable();
    }

    /** Call after the content view is set: dark icons on the system bars when the theme is light. */
    public static void applySystemBars(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        View decor = activity.getWindow().getDecorView();
        boolean night = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), decor);
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
    }

    /**
     * A context for UI that is added to the game's own activity (the in-game menu): themed like the app, with the same
     * light/dark choice and colours, which the game's activity knows nothing about.
     */
    public static Context wrap(Context base) {
        Context app = base.getApplicationContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        int mode = SBASettings.getThemeMode(app);
        if (mode != SBASettings.THEME_SYSTEM) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | (mode == SBASettings.THEME_DARK ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        }
        Context themed = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_SBA);
        if (!DynamicColors.isDynamicColorAvailable()) return themed;
        if (SBASettings.getMaterialYou(app)) return DynamicColors.wrapContextIfAvailable(themed);
        int accent = SBASettings.getAccentColor(app);
        return accent != 0
                ? DynamicColors.wrapContextIfAvailable(themed,
                        new DynamicColorsOptions.Builder().setContentBasedSource(accent).build())
                : themed;
    }

    /** The app's one typeface, for views that are built in code. {@code bold} gives the heavier weight. */
    public static android.graphics.Typeface font(Context context, boolean bold) {
        android.graphics.Typeface base = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.roboto_flex_family);
        if (base == null) return android.graphics.Typeface.DEFAULT;
        return bold ? android.graphics.Typeface.create(base, 700, false) : base;
    }

    private static int nightMode(int mode) {
        return switch (mode) {
            case SBASettings.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case SBASettings.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES;
            default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };
    }
}
