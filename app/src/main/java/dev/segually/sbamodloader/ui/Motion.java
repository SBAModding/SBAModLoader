package dev.segually.sbamodloader.ui;

import android.view.MotionEvent;
import android.view.View;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import dev.segually.sbamodloader.R;

/**
 * Spring motion, the way Material 3 Expressive moves things: pressed things squash a little and spring back, and new
 * things settle into place instead of just appearing.
 */
public final class Motion {
    private Motion() {
    }

    /** Pressing the view squashes it, letting go springs it back with a small overshoot. Clicks still work. */
    public static void pressable(View view) {
        view.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scale(v, 0.96f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scale(v, 1f);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void scale(View v, float to) {
        spring(v, DynamicAnimation.SCALE_X, to, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        spring(v, DynamicAnimation.SCALE_Y, to, SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
    }

    static void spring(View v, androidx.dynamicanimation.animation.DynamicAnimation.ViewProperty property, float to,
                       float stiffness, float damping) {
        SpringAnimation animation = new SpringAnimation(v, property, to);
        animation.getSpring().setStiffness(stiffness).setDampingRatio(damping);
        animation.start();
    }

    /**
     * Makes a new item rise into place with a spring, a little later for each later item. Only the first time the
     * view is shown (a recycled row that scrolls back does not animate again).
     */
    public static void appear(View view, int index) {
        if (view.getTag(R.id.motion_appeared) != null || index > 10) return;
        view.setTag(R.id.motion_appeared, Boolean.TRUE);
        float rise = 36 * view.getResources().getDisplayMetrics().density;
        final float target = view.getAlpha();
        view.setAlpha(0f);
        view.setTranslationY(rise);
        view.postDelayed(() -> {
            view.animate().alpha(target).setDuration(220).start();
            spring(view, DynamicAnimation.TRANSLATION_Y, 0f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_LOW_BOUNCY);
        }, 40L * index);
    }
}
