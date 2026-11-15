package dev.segually.sbamodloader.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

/**
 * The navigation in a floating toolbar: icon buttons in a pill, where the chosen page's button grows to show its label
 * and the others shrink back to just an icon. Changing page morphs the buttons instead of switching them.
 */
public final class FloatingNav {
    public interface Listener {
        void onSelected(int index);
    }

    private final Context context;
    private final LinearLayout row;
    private final MaterialButton[] buttons;
    private final int[] labels;
    private final Listener listener;
    private int selected = -1;
    private boolean showAllLabels;

    public FloatingNav(LinearLayout row, int[] icons, int[] labels, Listener listener) {
        this.context = row.getContext();
        this.row = row;
        this.labels = labels;
        this.listener = listener;
        buttons = new MaterialButton[icons.length];
        for (int i = 0; i < icons.length; i++) {
            MaterialButton b = new MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle);
            b.setElevation(0f);
            b.setStateListAnimator(null);
            b.setStrokeWidth(0);
            b.setIconResource(icons[i]);
            b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            b.setInsetTop(0);
            b.setInsetBottom(0);
            b.setMinWidth(dp(56));
            b.setMinimumWidth(dp(56));
            b.setMinHeight(dp(56));
            b.setMinimumHeight(dp(56));
            b.setCornerRadius(dp(28));
            b.setTextAppearance(dev.segually.sbamodloader.R.style.TextAppearance_SBA_LabelLargeEmphasized);
            b.setContentDescription(context.getString(labels[i]));
            final int index = i;
            b.setOnClickListener(v -> select(index, true));
            Motion.pressable(b);
            row.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)));
            buttons[i] = b;
        }
    }

    /** On a wide screen every button shows its label; otherwise only the chosen one does. */
    public void setShowAllLabels(boolean all) {
        if (all == showAllLabels) return;
        showAllLabels = all;
        if (selected >= 0) {
            int keep = selected;
            selected = -1;
            select(keep, false);
        }
    }

    public int selectedIndex() {
        return selected;
    }

    /** Selects a page's button. {@code notify} false only updates how it looks. */
    public void select(int index, boolean notify) {
        if (index == selected) {
            if (notify) listener.onSelected(index);
            return;
        }
        boolean animate = selected >= 0 && row.isLaidOut();
        if (animate) {
            AutoTransition t = new AutoTransition();
            t.setDuration(280);
            TransitionManager.beginDelayedTransition(row, t);
        }
        selected = index;
        int selectedBg = MaterialColors.getColor(row, com.google.android.material.R.attr.colorSecondaryContainer);
        int onSelected = MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSecondaryContainer);
        int onSurface = MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurfaceVariant);
        for (int i = 0; i < buttons.length; i++) {
            MaterialButton b = buttons[i];
            boolean on = i == index;
            boolean label = on || showAllLabels;
            b.setText(label ? context.getString(labels[i]) : "");
            b.setIconPadding(label ? dp(8) : 0);
            b.setBackgroundTintList(ColorStateList.valueOf(on ? selectedBg : 0x00000000));
            b.setTextColor(on ? onSelected : onSurface);
            b.setIconTint(ColorStateList.valueOf(on ? onSelected : onSurface));
            b.setPadding(dp(label ? 20 : 16), 0, dp(label ? 20 : 16), 0);
        }
        if (notify) listener.onSelected(index);
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
