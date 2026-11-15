package dev.segually.sbamodloader.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.color.MaterialColors;

/**
 * Decoration for hero cards: a few soft, overlapping expressive shapes (a scalloped "cookie", a flower and a circle)
 * in the theme's tonal colours. It only draws and takes no space of its own.
 */
public final class ShapeBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public ShapeBackdropView(Context context) {
        this(context, null);
    }

    public ShapeBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** A rounded polygon-like blob: radius {@code r} with {@code lobes} soft bumps of relative depth {@code depth}. */
    static void blob(Path out, float cx, float cy, float r, int lobes, float depth, float rotation) {
        out.reset();
        final int steps = 180;
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2 * i / steps;
            double rr = r * (1 + depth * Math.cos(lobes * a + rotation));
            float x = (float) (cx + rr * Math.cos(a));
            float y = (float) (cy + rr * Math.sin(a));
            if (i == 0) out.moveTo(x, y);
            else out.lineTo(x, y);
        }
        out.close();
    }

    /** The same shape for other views that need a cookie-shaped clip (icons). */
    public static Path cookie(float size, int lobes, float depth) {
        Path p = new Path();
        float r = size / 2f / (1 + depth);
        blob(p, size / 2f, size / 2f, r, lobes, depth, 0f);
        return p;
    }

    /** Clips a view to a cookie shape (a rounded square before Android 11, which cannot clip to a non-convex path). */
    public static android.view.ViewOutlineProvider cookieOutline(int lobes, float depth) {
        return new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    Path p = cookie(Math.min(w, h), lobes, depth);
                    outline.setPath(p);
                } else {
                    outline.setRoundRect(0, 0, w, h, Math.min(w, h) * 0.3f);
                }
            }
        };
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float unit = Math.min(w, h);

        int tertiary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiaryContainer);
        int secondary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer);
        int primary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);

        paint.setStyle(Paint.Style.FILL);

        paint.setColor(withAlpha(tertiary, 0xCC));
        blob(path, w - unit * 0.12f, unit * 0.18f, unit * 0.52f, 10, 0.055f, 0.3f);
        canvas.drawPath(path, paint);

        paint.setColor(withAlpha(secondary, 0xB3));
        blob(path, w - unit * 0.62f, h - unit * 0.05f, unit * 0.34f, 6, 0.12f, 0.9f);
        canvas.drawPath(path, paint);

        paint.setColor(withAlpha(primary, 0x2E));
        canvas.drawCircle(unit * 0.12f, h - unit * 0.1f, unit * 0.26f, paint);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
