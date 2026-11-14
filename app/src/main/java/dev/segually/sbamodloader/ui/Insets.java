package dev.segually.sbamodloader.ui;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class Insets {
    private Insets() {
    }

    /** Extra bottom space a page needs so its last item can scroll clear of the floating dock. */
    public static int navClearance(android.content.Context context) {
        return Math.round(96 * context.getResources().getDisplayMetrics().density);
    }

    /** Pads a scrolling page so content clears the status bar and the floating nav bar. */
    public static void padForChrome(View view, int extraBottomPx) {
        final int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // Left and right matter in landscape, where a camera cutout or a side navigation bar can sit there.
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom + extraBottomPx);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
