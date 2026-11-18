package app.stash.android

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Turns the backup settings into WorkManager jobs. Call [apply] again whenever a setting changes. */
object BackupScheduler {
    private const val PERIODIC = "stash-backup-periodic"
    private const val NOW = "stash-backup-now"

    private fun constraints(p: Prefs, charging: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (p.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(charging)
        .build()

    /** (Re)schedules the hourly job, or cancels everything when backup is switched off. */
    fun apply(ctx: Context) {
        val p = Prefs(ctx)
        val wm = WorkManager.getInstance(ctx)
        if (!p.backupEnabled) {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(NOW)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints(p, p.chargingOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** "Back up now": runs as soon as the network rule is met (ignores the charging rule, you asked for it). */
    fun runNow(ctx: Context) {
        val p = Prefs(ctx)
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints(p, false))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }
}
