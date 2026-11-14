package dev.segually.sbamodloader.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.IOException;

import dev.segually.sbamodloader.BuildConfig;
import dev.segually.sbamodloader.R;
import dev.segually.sbamodloader.SBASettings;
import dev.segually.sbamodloader.tools.AppUpdater;

/** The dialogs of the update flow, shared by the automatic check and the Settings page. */
public final class UpdatePrompt {
    private static volatile boolean busy;

    private UpdatePrompt() {
    }

    /** Asks whether to update to {@code release}. "Later" is remembered so the automatic prompt stays quiet for it. */
    public static void offer(Activity activity, AppUpdater.Release release) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_dialog_title)
                .setMessage(activity.getString(R.string.update_dialog_message, release.version, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.update_now, (d, w) -> start(activity, release))
                .setNegativeButton(R.string.update_later, (d, w) -> SBASettings.setUpdateLater(activity, release.version))
                .show();
    }

    /** Downloads the update, then hands it to Android's installer. */
    public static void start(Activity activity, AppUpdater.Release release) {
        if (busy) return;
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.update_allow_title)
                    .setMessage(R.string.update_allow_message)
                    .setPositiveButton(R.string.update_open_settings, (d, w) -> activity.startActivity(
                            new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        busy = true;
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_update_progress, null);
        LinearProgressIndicator bar = view.findViewById(R.id.update_progress);
        TextView text = view.findViewById(R.id.update_progress_text);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_dialog_title)
                .setView(view)
                .setCancelable(false)
                .show();
        android.content.Context app = activity.getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                File apk = AppUpdater.download(app, release, (done, total) -> activity.runOnUiThread(() -> {
                    if (total > 0) {
                        bar.setIndeterminate(false);
                        bar.setProgress((int) (done * 100 / total));
                    }
                }));
                AppUpdater.install(app, apk);
            } catch (IOException e) {
                android.util.Log.w("UpdatePrompt", "Update failed", e);
                error = app.getString(R.string.update_download_failed);
            }
            String err = error;
            activity.runOnUiThread(() -> {
                busy = false;
                if (dialog.isShowing()) dialog.dismiss();
                if (err != null && !activity.isFinishing()) Toast.makeText(activity, err, Toast.LENGTH_LONG).show();
            });
        }, "app-update").start();
    }
}
