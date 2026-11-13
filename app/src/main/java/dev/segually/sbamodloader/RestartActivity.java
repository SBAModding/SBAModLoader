package dev.segually.sbamodloader;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;

/**
 * Lives in its own process. The game cannot be unloaded from the process it runs in, so leaving it
 * means killing that process; this activity does that and then starts the dashboard in a fresh one.
 */
public class RestartActivity extends Activity {
    public static final String EXTRA_PID = "pid";

    private boolean started;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pid = getIntent().getIntExtra(EXTRA_PID, -1);
        if (pid > 0 && pid != Process.myPid()) {
            Process.killProcess(pid);
            waitForExit(pid);
        }
    }

    // The dashboard is started once this activity is visible: with the game process gone, nothing else
    // of this app is in the foreground, and Android blocks activity starts from the background.
    @Override
    protected void onResume() {
        super.onResume();
        if (started) return;
        started = true;
        Intent dashboard = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(dashboard);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // This process only exists for the restart.
        Process.killProcess(Process.myPid());
    }

    private void waitForExit(int pid) {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (int i = 0; i < 60; i++) {
            boolean alive = false;
            if (am != null && am.getRunningAppProcesses() != null) {
                for (ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
                    if (p.pid == pid) alive = true;
                }
            }
            if (!alive) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
