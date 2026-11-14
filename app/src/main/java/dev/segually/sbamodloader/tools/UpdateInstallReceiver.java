package dev.segually.sbamodloader.tools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import dev.segually.sbamodloader.R;

/** Receives the result of an update install: opens Android's confirmation screen, or reports a failure. */
public class UpdateInstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent.class);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirm);
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            android.util.Log.w("AppUpdater", "Install failed: " + status + " "
                    + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            if (status != PackageInstaller.STATUS_FAILURE_ABORTED) { // aborted: the user said no
                Toast.makeText(context, R.string.update_install_failed, Toast.LENGTH_LONG).show();
            }
        }
    }
}
