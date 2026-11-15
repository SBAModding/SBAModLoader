package dev.segually.sbamodloader.ui;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class Insets {
    private Insets() {
    }

    /** Extra bottom space a page needs so its last item can scroll clear of the floating dock. */
    public static int navClearance(android.content.Context context) {
        // On a desktop the navigation is a rail at the side, so nothing floats over the bottom of the page.
        float dp = LayoutMode.current ? 24 : 96;
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    /** Pads a scrolling page so content clears the status bar and the floating nav bar. */
    public static void padForChrome(View view, int extraBottomPx) {
        padForChrome(view, extraBottomPx, 0);
    }

    /**
     * Like {@link #padForChrome(View, int)}, and keeps the content at most {@code maxContentDp} wide, centred, so a page
     * stays readable on a wide screen (landscape, tablets, desktops). 0 means no limit.
     */
    public static void padForChrome(View view, int extraBottomPx, int maxContentDp) {
        padForChrome(view, extraBottomPx, maxContentDp, true);
    }

    /**
     * As above; {@code includeTop} false is for a page whose own app bar already sits below the status bar, so only
     * the sides and the bottom are padded.
     */
    public static void padForChrome(View view, int extraBottomPx, int maxContentDp, boolean includeTop) {
        final int l = view.getPaddingLeft(), t = view.getPaddingTop(), r = view.getPaddingRight(), b = view.getPaddingBottom();
        final int maxPx = Math.round(maxContentDp * view.getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int left = 0;
            int right = 0;
            if (maxPx > 0) {
                int free = Math.max(0, v.getWidth() - bars.left - bars.right - l - r - maxPx);
                // On a desktop the content hugs the navigation rail, in line with the page title; elsewhere it is centred.
                if (LayoutMode.current) right = free;
                else left = right = free / 2;
            }
            // Left and right matter in landscape, where a camera cutout or a side navigation bar can sit there.
            v.setPadding(l + bars.left + left, t + (includeTop ? bars.top : 0), r + bars.right + right, b + bars.bottom + extraBottomPx);
            return insets;
        });
        if (maxPx > 0) {
            // The width is only known after layout, and changes when the window is resized.
            view.addOnLayoutChangeListener((v, a, b2, c, d, oa, ob, oc, od) -> {
                if (c - a != oc - oa) ViewCompat.requestApplyInsets(v);
            });
        }
        ViewCompat.requestApplyInsets(view);
    }
}
