package dev.segually.sbamodloader.tools;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;

import dev.segually.sbamodloader.R;

public class JniBridge {
    private static final String TAG = "JniBridge";

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable timerRunnable;

    public static void SetLoadingState(Activity activity, boolean enabled) {
        activity.runOnUiThread(() -> {
            if (!dev.segually.sbamodloader.ui.compose.LoadingOverlay.setVisible(activity, enabled)) {
                Log.e(TAG, "Could not set loading state, there is no loading view!");
                return;
            }

            Log.i(TAG, "Set loading state to "+enabled);

            if (!enabled && timerRunnable != null) {
                handler.removeCallbacks(timerRunnable);
            }
        });
    }

    public static void SetLoadingText(Activity activity, String text) {
        activity.runOnUiThread(() -> {
            if (!dev.segually.sbamodloader.ui.compose.LoadingOverlay.setText(activity, text)) {
                Log.e(TAG, "Could not set loading text, there is no loading view!");
                return;
            }
            Log.i(TAG, "Set loading text to "+text);

            // reset timer
            if (timerRunnable != null) {
                handler.removeCallbacks(timerRunnable);
            }

            timerRunnable = new Runnable() {
                private int elapsedSeconds = 0;

                @Override
                public void run() {
                    elapsedSeconds++;
                    int minutes = elapsedSeconds / 60;
                    int seconds = elapsedSeconds % 60;

                    String timeString = String.format(Locale.getDefault(),
                            " (%02d:%02d)", minutes, seconds);

                    dev.segually.sbamodloader.ui.compose.LoadingOverlay.setText(activity, text + timeString);

                    handler.postDelayed(this, 1000);
                }
            };
            handler.post(timerRunnable);
        });
    }
}
