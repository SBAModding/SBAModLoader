package dev.segually.sbamodloader.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.segually.sbamodloader.BuildConfig
import dev.segually.sbamodloader.R
import dev.segually.sbamodloader.SBASettings
import dev.segually.sbamodloader.tools.AppUpdater
import dev.segually.sbamodloader.ui.compose.AlertCard
import dev.segually.sbamodloader.ui.compose.AlertText
import dev.segually.sbamodloader.ui.compose.DialogButton
import dev.segually.sbamodloader.ui.compose.SbaDialogs
import java.io.IOException

/** The dialogs of the update flow, shared by the automatic check and the Settings page. */
object UpdatePrompt {
    @Volatile
    private var busy = false

    /** Asks whether to update to [release]. "Later" is remembered so the automatic prompt stays quiet for it. */
    @JvmStatic
    fun offer(activity: Activity, release: AppUpdater.Release) {
        if (activity.isFinishing || activity.isDestroyed) return
        SbaDialogs.show(activity) { dismiss ->
            AlertCard(
                title = stringResource(R.string.update_dialog_title),
                buttons = {
                    DialogButton(stringResource(R.string.update_later), { dismiss(); SBASettings.setUpdateLater(activity, release.version) })
                    DialogButton(stringResource(R.string.update_now), { dismiss(); start(activity, release) })
                },
            ) { AlertText(stringResource(R.string.update_dialog_message, release.version, BuildConfig.VERSION_NAME)) }
        }
    }

    private fun progressDialog(activity: Activity, progress: androidx.compose.runtime.State<Float?>) =
        SbaDialogs.show(activity, cancelable = false) {
            AlertCard(title = stringResource(R.string.update_dialog_title), buttons = {}) {
                Column(Modifier.fillMaxWidth()) {
                    AlertText(stringResource(R.string.update_downloading), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    val p = progress.value
                    if (p == null) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                    else LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
        }

    /** Debug builds: the download dialog at 60%, with nothing downloaded or installed, for layout checks. */
    @JvmStatic
    fun debugProgress(activity: Activity) {
        progressDialog(activity, mutableStateOf(0.6f))
    }

    /** Downloads the update, then hands it to Android's installer. */
    @JvmStatic
    fun start(activity: Activity, release: AppUpdater.Release) {
        if (busy) return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            SbaDialogs.show(activity) { dismiss ->
                AlertCard(
                    title = stringResource(R.string.update_allow_title),
                    buttons = {
                        DialogButton(stringResource(android.R.string.cancel), dismiss)
                        DialogButton(stringResource(R.string.update_open_settings), {
                            dismiss()
                            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
                        })
                    },
                ) { AlertText(stringResource(R.string.update_allow_message)) }
            }
            return
        }
        busy = true
        // Null while the size is not known; otherwise 0..1.
        val progress = mutableStateOf<Float?>(null)
        val dialog = progressDialog(activity, progress)
        val app = activity.applicationContext
        Thread({
            var error: String? = null
            try {
                val apk = AppUpdater.download(app, release) { done, total ->
                    activity.runOnUiThread { if (total > 0) progress.value = done.toFloat() / total }
                }
                AppUpdater.install(app, apk)
            } catch (e: IOException) {
                android.util.Log.w("UpdatePrompt", "Update failed", e)
                error = app.getString(R.string.update_download_failed)
            }
            activity.runOnUiThread {
                busy = false
                if (dialog.isShowing) dialog.dismiss()
                if (error != null && !activity.isFinishing) Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            }
        }, "app-update").start()
    }
}
