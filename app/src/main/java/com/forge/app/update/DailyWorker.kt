package com.forge.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.forge.app.Graph
import com.forge.app.data.AutoBackup
import com.forge.app.notify.Notifier
import java.util.concurrent.TimeUnit

/** Once a day: automatic backup, then a look for a new release. */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        Graph.init(ctx)
        val s = Graph.settings.current()
        if (!s.onboarded) return Result.success()

        if (s.autoBackup && System.currentTimeMillis() - s.lastBackupAt > 20 * 3_600_000L) {
            runCatching { AutoBackup.run(ctx, reason = "daily") }
        }

        if (s.autoUpdateCheck) {
            val info = runCatching { Updater.fetchLatest() }.getOrNull()
            if (info != null) {
                Updater.publish(info)
                if (s.lastNotifiedVersion != info.version) {
                    Notifier.post(
                        ctx, Notifier.ID_UPDATE, Notifier.CH_UPDATES, "Forge ${info.version} is out",
                        info.notes.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('-', '*', ' ')
                            ?: "Tap to see what's new and install. Your data stays.",
                        route = "update",
                    )
                    Graph.settings.update { it.copy(lastNotifiedVersion = info.version) }
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "forge-daily"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
