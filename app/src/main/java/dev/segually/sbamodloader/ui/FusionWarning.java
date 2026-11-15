package dev.segually.sbamodloader.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import dev.segually.sbamodloader.R;

/** The yellow "Fusion may not work on this device" dialog, shown at first launch and when Fusion is turned on. */
public final class FusionWarning {
    private static final int YELLOW = 0xFFFFC107;

    private FusionWarning() {
    }

    /** Shown once, before the first launch on a device that failed the check. */
    public static void showFirstLaunch(Context context, List<String> reasons, Runnable onOk) {
        build(context, reasons, R.string.fusion_warn_title, R.string.fusion_warn_body)
                .setCancelable(false)
                .setPositiveButton(R.string.fusion_warn_ok, (d, w) -> onOk.run())
                .show();
    }

    /** Shown when the user turns Fusion on, on a device that failed the check. */
    public static void showTurnOn(Context context, List<String> reasons, Runnable onConfirm, Runnable onCancel) {
        build(context, reasons, R.string.fusion_enable_title, R.string.fusion_enable_body)
                .setOnCancelListener(d -> onCancel.run())
                .setNegativeButton(android.R.string.cancel, (d, w) -> onCancel.run())
                .setPositiveButton(R.string.fusion_enable_confirm, (d, w) -> onConfirm.run())
                .show();
    }

    private static MaterialAlertDialogBuilder build(Context context, List<String> reasons, int title, int body) {
        float density = context.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, Math.round(20 * density), pad, 0);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_warning);
        icon.setColorFilter(YELLOW);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Math.round(56 * density), Math.round(56 * density));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(icon, iconParams);

        TextView heading = text(context, context.getString(title), 20, true);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, Math.round(12 * density), 0, Math.round(8 * density));
        column.addView(heading);

        column.addView(text(context, context.getString(body), 15, false));

        if (reasons != null && !reasons.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String r : reasons) sb.append("\u2022 ").append(r).append('\n');
            TextView detected = text(context, sb.toString().trim(), 14, false);
            detected.setPadding(0, Math.round(8 * density), 0, 0);
            detected.setTextColor(YELLOW);
            column.addView(detected);
        }

        TextView what = text(context, context.getString(R.string.fusion_warn_what), 16, true);
        what.setPadding(0, Math.round(16 * density), 0, Math.round(4 * density));
        column.addView(what);
        column.addView(text(context, context.getString(R.string.fusion_warn_bullets), 15, false));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(column);
        return new MaterialAlertDialogBuilder(context).setView(scroll);
    }

    private static TextView text(Context context, String value, int sp, boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(com.google.android.material.color.MaterialColors.getColor(tv,
                com.google.android.material.R.attr.colorOnSurface, Color.WHITE));
        tv.setTypeface(Theming.font(context, bold));
        return tv;
    }
}
