package dev.segually.sbamodloader.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * A FAB menu in the Material 3 Expressive style: one big button that opens into a stack of labelled actions and turns
 * into a close button. (Material's own FAB menu only exists for Jetpack Compose, which this app does not use, so this
 * is built from views with the same behaviour: a scrim, items that spring in one after another, a button that spins.)
 */
public final class FabMenu {
    private final Context context;
    private final FloatingActionButton fab;
    private final LinearLayout items;
    private final View scrim;
    private final List<MaterialButton> buttons = new ArrayList<>();
    private boolean open;

    public FabMenu(FloatingActionButton fab, LinearLayout items, View scrim) {
        this.context = fab.getContext();
        this.fab = fab;
        this.items = items;
        this.scrim = scrim;
        items.setVisibility(View.GONE);
        scrim.setVisibility(View.GONE);
        scrim.setAlpha(0f);
        fab.setOnClickListener(v -> toggle());
        scrim.setOnClickListener(v -> close());
        Motion.pressable(fab);
    }

    /** Adds an action. Returns its button, so the caller can hide it for some users. */
    public MaterialButton addItem(@StringRes int label, @DrawableRes int icon, Runnable action) {
        MaterialButton b = new MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        b.setElevation(0f);
        b.setStateListAnimator(null);
        b.setStrokeWidth(0);
        b.setText(label);
        b.setIconResource(icon);
        b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        b.setIconPadding(dp(12));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinHeight(dp(64));
        b.setMinimumHeight(dp(64));
        b.setCornerRadius(dp(32));
        b.setPadding(dp(24), 0, dp(28), 0);
        b.setTextAppearance(dev.segually.sbamodloader.R.style.TextAppearance_SBA_TitleMediumEmphasized);
        int bg = MaterialColors.getColor(fab, com.google.android.material.R.attr.colorPrimaryContainer);
        int on = MaterialColors.getColor(fab, com.google.android.material.R.attr.colorOnPrimaryContainer);
        b.setBackgroundTintList(ColorStateList.valueOf(bg));
        b.setTextColor(on);
        b.setIconTint(ColorStateList.valueOf(on));
        b.setOnClickListener(v -> {
            close();
            action.run();
        });
        Motion.pressable(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(64));
        lp.gravity = Gravity.END;
        lp.topMargin = dp(10);
        items.addView(b, lp);
        buttons.add(b);
        return b;
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        if (open) close(); else open();
    }

    public void open() {
        if (open) return;
        open = true;
        items.animate().cancel();
        items.setAlpha(1f);
        items.setVisibility(View.VISIBLE);
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(0.4f).setDuration(200).start();
        // The button becomes a close button: new icon, the contrasting colour, and a springy pop.
        fab.setImageResource(dev.segually.sbamodloader.R.drawable.ic_close);
        fab.setScaleX(0.8f);
        fab.setScaleY(0.8f);
        Motion.spring(fab, DynamicAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_HIGH_BOUNCY);
        Motion.spring(fab, DynamicAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_HIGH_BOUNCY);
        fab.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(fab, androidx.appcompat.R.attr.colorPrimary)));
        // Items spring up from the button, the nearest one first.
        int shown = 0;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            MaterialButton b = buttons.get(i);
            if (b.getVisibility() != View.VISIBLE) continue;
            b.setAlpha(0f);
            b.setTranslationY(dp(48));
            final long delay = 45L * shown++;
            b.postDelayed(() -> {
                b.animate().alpha(1f).setDuration(160).start();
                Motion.spring(b, DynamicAnimation.TRANSLATION_Y, 0f, SpringForce.STIFFNESS_MEDIUM,
                        SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
            }, delay);
        }
    }

    public void close() {
        if (!open) return;
        open = false;
        fab.setImageResource(dev.segually.sbamodloader.R.drawable.ic_add);
        fab.setScaleX(0.8f);
        fab.setScaleY(0.8f);
        Motion.spring(fab, DynamicAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_HIGH_BOUNCY);
        Motion.spring(fab, DynamicAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_HIGH_BOUNCY);
        fab.setBackgroundTintList(ColorStateList.valueOf(MaterialColors.getColor(fab,
                com.google.android.material.R.attr.colorPrimaryContainer)));
        scrim.animate().alpha(0f).setDuration(160).withEndAction(() -> {
            if (!open) scrim.setVisibility(View.GONE);
        }).start();
        items.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            if (!open) {
                items.setVisibility(View.GONE);
                items.setAlpha(1f);
            }
        }).start();
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
