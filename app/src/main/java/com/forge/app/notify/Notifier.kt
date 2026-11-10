package com.forge.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.forge.app.MainActivity
import com.forge.app.R

object Notifier {
    const val CH_WORKOUT = "workout"
    const val CH_PENALTY = "penalty"
    const val CH_NUTRITION = "nutrition"
    const val CH_HABITS = "habits"
    const val CH_TIMER = "timer"
    const val CH_WEEKLY = "weekly"
    const val CH_UPDATES = "updates"
    const val ID_UPDATE = 1010

    const val EXTRA_ROUTE = "route"

    // Notification ids
    const val ID_WEIGH = 1001
    const val ID_WORKOUT = 1002
    const val ID_MISSED = 1003
    const val ID_PROTEIN = 1004
    const val ID_NIGHT = 1005
    const val ID_WEEKLY = 1006
    const val ID_PENALTY = 1007
    const val ID_TIMER = 1008
    const val ID_TEST = 1009

    private const val ACCENT = 0xFFCCFF4D.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(CH_WORKOUT, "Workout reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders and deadline warnings on training days"
            },
            NotificationChannel(CH_PENALTY, "Penalties and misses", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Missed sessions, penalty deadlines"
            },
            NotificationChannel(CH_NUTRITION, "Food and weigh-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Morning weigh-in, protein and calorie checks"
            },
            NotificationChannel(CH_HABITS, "Daily check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "End-of-day habit check-in"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(CH_TIMER, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when rest is over"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            },
            NotificationChannel(CH_WEEKLY, "Weekly review", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your score at the end of each week"
            },
            NotificationChannel(CH_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A new version is available on GitHub"
            },
        )
        nm.createNotificationChannels(channels)
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openIntent(ctx: Context, route: String, requestCode: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun actionIntent(ctx: Context, action: String, requestCode: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    data class Action(val label: String, val intent: PendingIntent, val input: androidx.core.app.RemoteInput? = null)

    /** Mutable broadcast intent — required for actions that carry typed input. */
    fun inputIntent(ctx: Context, action: String, requestCode: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, requestCode, i, flags)
    }

    fun post(
        ctx: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        route: String = "today",
        actions: List<Action> = emptyList(),
        ongoing: Boolean = false,
        timeoutMs: Long? = null,
        publicText: String? = null,
    ) {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_forge)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(ctx, route, id))
            .setAutoCancel(true)
            .setOngoing(ongoing)
            .setCategory(if (channel == CH_TIMER) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(
                if (channel == CH_WORKOUT || channel == CH_PENALTY || channel == CH_TIMER) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
        if (publicText != null) {
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            b.setPublicVersion(
                NotificationCompat.Builder(ctx, channel)
                    .setSmallIcon(R.drawable.ic_stat_forge)
                    .setColor(ACCENT)
                    .setContentTitle("Forge")
                    .setContentText(publicText)
                    .build()
            )
        }
        timeoutMs?.let { b.setTimeoutAfter(it) }
        actions.forEach { a ->
            if (a.input != null) b.addAction(NotificationCompat.Action.Builder(0, a.label, a.intent).addRemoteInput(a.input).setAllowGeneratedReplies(false).build())
            else b.addAction(0, a.label, a.intent)
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}
