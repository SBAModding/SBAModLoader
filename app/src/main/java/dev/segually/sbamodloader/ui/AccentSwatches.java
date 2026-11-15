package dev.segually.sbamodloader.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;

import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.SBASettings;

/** A row of accent colour choices: the app's green, some presets, and a colour of your own. */
public final class AccentSwatches {
    /** The presets (the first is the app's own green, saved as 0). */
    private static final int[] PRESETS = {
            0xFF7FD1A7, 0xFF8AB4F8, 0xFFC6A8FF, 0xFFFFA6C9, 0xFFFF8A7A, 0xFFFFB05C, 0xFFE9C349, 0xFF5CD6D6,
    };

    private final Context context;
    private final LinearLayout container;
    private final Runnable onChanged;
    private boolean enabled = true;

    public AccentSwatches(Context context, LinearLayout container, Runnable onChanged) {
        this.context = context;
        this.container = container;
        this.onChanged = onChanged;
        build();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        for (int i = 0; i < container.getChildCount(); i++) container.getChildAt(i).setEnabled(enabled);
    }

    private void build() {
        container.removeAllViews();
        int saved = SBASettings.getAccentColor(context);
        boolean custom = saved != 0 && !isPreset(saved);
        for (int i = 0; i < PRESETS.length; i++) {
            int color = PRESETS[i];
            int stored = i == 0 ? 0 : color;
            container.addView(swatch(color, saved == stored, v -> choose(stored)));
        }
        container.addView(customSwatch(custom ? saved : 0, custom));
    }

    private boolean isPreset(int color) {
        for (int i = 1; i < PRESETS.length; i++) if (PRESETS[i] == color) return true;
        return false;
    }

    private void choose(int stored) {
        if (!enabled) return;
        SBASettings.setAccentColor(context, stored);
        onChanged.run();
    }

    private View swatch(int color, boolean selected, View.OnClickListener click) {
        int size = dp(56);
        FrameLayout frame = new FrameLayout(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(dp(12));
        frame.setLayoutParams(lp);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(color);
        int ring = MaterialColors.getColor(frame, com.google.android.material.R.attr.colorOnSurface);
        if (selected) disc.setStroke(dp(3), ring);
        frame.setBackground(disc);
        frame.setClickable(true);
        frame.setFocusable(true);
        frame.setForeground(rippleOver(disc));
        frame.setOnClickListener(click);
        frame.setContentDescription(String.format("#%06X", color & 0xFFFFFF));
        if (selected) {
            ImageView tick = new ImageView(context);
            tick.setImageResource(R.drawable.ic_check);
            tick.setImageTintList(ColorStateList.valueOf(readableOn(color)));
            FrameLayout.LayoutParams t = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
            frame.addView(tick, t);
        }
        return frame;
    }

    private View customSwatch(int color, boolean selected) {
        View v = swatch(color != 0 ? color : 0xFF3A3F3C, selected, x -> pickCustom());
        if (!selected) {
            ImageView plus = new ImageView(context);
            plus.setImageResource(R.drawable.ic_add);
            plus.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            ((FrameLayout) v).addView(plus, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
            v.setContentDescription(context.getString(R.string.appearance_accent_custom));
        }
        return v;
    }

    private void pickCustom() {
        if (!enabled) return;
        int current = SBASettings.getAccentColor(context);
        float[] hsv = new float[3];
        Color.colorToHSV(current != 0 ? current : PRESETS[1], hsv);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(8), dp(24), 0);
        View preview = new View(context);
        body.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        Slider hue = slider(body, R.string.appearance_accent_hue, 0, 359, hsv[0]);
        Slider strength = slider(body, R.string.appearance_accent_strength, 20, 100, Math.max(0.2f, hsv[1]) * 100);
        int[] chosen = {0};
        Runnable update = () -> {
            chosen[0] = Color.HSVToColor(new float[]{hue.getValue(), strength.getValue() / 100f, 0.9f});
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(dp(24));
            d.setColor(chosen[0]);
            preview.setBackground(d);
        };
        hue.addOnChangeListener((s, value, fromUser) -> update.run());
        strength.addOnChangeListener((s, value, fromUser) -> update.run());
        update.run();

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.appearance_accent_custom)
                .setView(body)
                .setPositiveButton(R.string.appearance_accent_use, (d, w) -> choose(chosen[0] | 0xFF000000))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Slider slider(LinearLayout parent, int labelRes, float from, float to, float value) {
        android.widget.TextView label = new android.widget.TextView(context);
        label.setText(labelRes);
        label.setTextAppearance(R.style.TextAppearance_SBA_RowBody);
        label.setPadding(0, dp(12), 0, 0);
        parent.addView(label);
        Slider slider = new Slider(context);
        slider.setValueFrom(from);
        slider.setValueTo(to);
        slider.setValue(Math.max(from, Math.min(to, value)));
        parent.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return slider;
    }

    private android.graphics.drawable.Drawable rippleOver(GradientDrawable shape) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        return new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x33000000), null, mask);
    }

    private static int readableOn(int color) {
        return androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
