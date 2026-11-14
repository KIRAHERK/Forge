package com.forge.app

import android.app.Application
import android.content.Context
import com.forge.app.data.ForgeDb
import com.forge.app.data.Repo
import com.forge.app.data.SettingsStore
import com.forge.app.notify.Notifier
import com.forge.app.notify.Scheduler
import com.forge.app.widget.ForgeWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tiny service locator. One database, one settings store, one repo. */
object Graph {
    lateinit var appContext: Context
        private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: ForgeDb by lazy { ForgeDb.create(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val repo: Repo by lazy {
        Repo(appContext, db, settings).also { r -> r.onChanged = { scheduleRefresh() } }
    }

    fun init(context: Context) {
        if (!::appContext.isInitialized) appContext = context.applicationContext
        com.forge.app.i18n.Lang.init(appContext)
    }

    /** Programs loaded (and the original one created on first run). Call before reading any day. */
    suspend fun programsReady() = com.forge.app.data.ProgramStore.ensure(db, settings)

    private var refreshJob: Job? = null

    /** Debounced: refresh widget and reminders after data changes. */
    fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(400)
            runCatching { ForgeWidget.refresh(appContext) }
            runCatching { Scheduler.rescheduleAll(appContext) }
        }
    }
}

class ForgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifier.createChannels(this)
        com.forge.app.update.DailyWorker.schedule(this)
        // Evaluation happens where its result is shown (alarm receiver, boot, activity resume),
        // so a "missed" notification is never swallowed by a background evaluation.
        Graph.scope.launch {
            runCatching { Graph.programsReady() }
            runCatching { Scheduler.rescheduleAll(this@ForgeApp) }
        }
    }
}
