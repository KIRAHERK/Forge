package com.forge.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.forge.app.Graph
import com.forge.app.MainActivity
import com.forge.app.data.DayStatus
import com.forge.app.data.Fmt
import com.forge.app.data.Stats
import com.forge.app.domain.Program
import com.forge.app.domain.Xp
import com.forge.app.notify.Notifier
import java.time.LocalDateTime

private val BG = ColorProvider(Color(0xFF141417))
private val HI = ColorProvider(Color(0xFFF2F2F3))
private val MID = ColorProvider(Color(0xFF9B9BA4))
private val VOLT: ColorProvider get() = ColorProvider(Color(com.forge.app.domain.Accent.current.argb))
private val RED: ColorProvider get() = ColorProvider(Color(com.forge.app.domain.Accent.current.miss))
private val TRACK = ColorProvider(Color(0xFF26262B))

data class WidgetData(
    val onboarded: Boolean,
    val date: String,
    val title: String,
    val status: String,
    val statusColor: Int, // 0 mid, 1 volt, 2 red
    val protein: Int,
    val proteinTarget: Int,
    val kcal: Int,
    val kcalTarget: Int,
    val level: Int,
    val penalties: Int,
)

class ForgeWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(context, data) }
    }

    private suspend fun load(context: Context): WidgetData {
        Graph.init(context)
        Graph.programsReady()
        val s = Graph.settings.current()
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val sessions = Graph.db.sessions().allNow()
        val done = Stats.doneDates(sessions)
        val m = Stats.sum(Graph.db.food().forDayNow(today.toEpochDay()))
        val day = s.dayFor(today)
        val st = Stats.dayStatus(s, today, now, done)
        val (status, color) = when {
            !s.onboarded -> "Open the app to set up" to 0
            st == DayStatus.DONE -> "Done. Recover and eat." to 1
            st == DayStatus.SICK -> "Sick day. Rest up." to 0
            day == null -> Program.REST_NOTE to 0
            st == DayStatus.PENDING -> "Due ${Fmt.time(s.deadline)} · ${Fmt.left(now, s.deadlineAt(today))} left" to 1
            else -> "Missed. Penalty active." to 2
        }
        val pen = Graph.db.penalties().activeNow().size
        return WidgetData(
            onboarded = s.onboarded,
            date = Fmt.day(today).uppercase(),
            title = if (st == DayStatus.DONE && day == null) "Extra session done" else day?.title?.uppercase() ?: "REST DAY",
            status = status,
            statusColor = color,
            protein = m.protein.toInt(),
            proteinTarget = s.protein,
            kcal = m.kcal,
            kcalTarget = s.kcal,
            level = Xp.level(Graph.db.xp().totalNow()).level,
            penalties = pen,
        )
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val open = Intent(context, MainActivity::class.java).putExtra(Notifier.EXTRA_ROUTE, "today")
        Column(
            modifier = GlanceModifier.fillMaxSize().background(BG).cornerRadius(20.dp).padding(16.dp)
                .clickable(actionStartActivity(open)),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("FORGE · ${d.date}", style = TextStyle(color = MID, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                Spacer(GlanceModifier.defaultWeight())
                if (d.penalties > 0) {
                    Text("${d.penalties} PENALTY  ", style = TextStyle(color = RED, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
                Text("LVL ${d.level}", style = TextStyle(color = VOLT, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(d.title, style = TextStyle(color = HI, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(
                d.status,
                style = TextStyle(color = when (d.statusColor) { 1 -> VOLT; 2 -> RED; else -> MID }, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(10.dp))
            Bar("Protein", "${d.protein} / ${d.proteinTarget} g", d.protein.toFloat() / d.proteinTarget.coerceAtLeast(1))
            Spacer(GlanceModifier.height(6.dp))
            Bar("Calories", "${Fmt.num(d.kcal)} / ${Fmt.num(d.kcalTarget)}", d.kcal.toFloat() / d.kcalTarget.coerceAtLeast(1))
        }
    }

    @Composable
    private fun Bar(label: String, value: String, p: Float) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(label, style = TextStyle(color = MID, fontSize = 11.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text(value, style = TextStyle(color = HI, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.height(3.dp))
            LinearProgressIndicator(
                progress = p.coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = VOLT,
                backgroundColor = TRACK,
            )
        }
    }

    companion object {
        suspend fun refresh(context: Context) = ForgeWidget().updateAll(context)
    }
}

class ForgeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ForgeWidget()
}
