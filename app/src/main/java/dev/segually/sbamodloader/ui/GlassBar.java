package dev.segually.sbamodloader.ui;

import android.content.res.ColorStateList;
import android.provider.Settings;

import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

/**
 * Liquid-glass rules, Material 3 look: the navigation bar is the only translucent layer.
 * It floats over the content, uses the M3 container colour with partial transparency and a
 * thin light rim, and keeps the standard M3 active indicator. Content stays opaque.
 */
public final class GlassBar {
    private GlassBar() {
    }

    public static void apply(NavigationBarView bar) {
        var ctx = bar.getContext();
        float density = ctx.getResources().getDisplayMetrics().density;

        int container = MaterialColors.getColor(bar, com.google.android.material.R.attr.colorSurfaceContainer);
        int onSurface = MaterialColors.getColor(bar, com.google.android.material.R.attr.colorOnSurface);
        // Reduce-transparency equivalent: respect high contrast text and stay opaque.
        boolean opaque = Settings.Secure.getInt(ctx.getContentResolver(), "high_text_contrast_enabled", 0) == 1;
        int alpha = opaque ? 0xFF : 0xB8;
        int body = (container & 0x00FFFFFF) | (alpha << 24);

        var shape = ShapeAppearanceModel.builder().setAllCornerSizes(ShapeAppearanceModel.PILL).build();
        var bg = new MaterialShapeDrawable(shape);
        bg.setFillColor(ColorStateList.valueOf(body));
        bg.setStroke(density, (onSurface & 0x00FFFFFF) | (0x2E << 24));
        bar.setBackground(bg);
        bar.setElevation(0f);
    }
}
