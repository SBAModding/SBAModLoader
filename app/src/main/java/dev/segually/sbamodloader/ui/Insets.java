package dev.segually.sbamodloader.ui;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class Insets {
    private Insets() {
    }

    /** Pads a scrolling page so content clears the status bar and the floating nav bar. */
    public static void padForChrome(View view, int extraBottomPx) {
        final int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(l, t + bars.top, r, b + bars.bottom + extraBottomPx);
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
