package com.thatmre.sol1.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.thatmre.sol1.data.Repo
import com.thatmre.sol1.data.Tracker
import com.thatmre.sol1.data.TrackerType
import com.thatmre.sol1.data.stats
import com.thatmre.sol1.ui.MainActivity

val TrackerIdKey = ActionParameters.Key<String>("trackerId")

class Sol1Widget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val tracker = Repo.trackerForWidget(context, appWidgetId)
        provideContent {
            GlanceTheme {
                WidgetContent(tracker)
            }
        }
    }
}

@Composable
private fun WidgetContent(tracker: Tracker?) {
    val size = LocalSize.current
    val compact = size.height < 100.dp
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (tracker == null) {
            Text(
                text = "Open Sol-1 and add a tracker",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, textAlign = TextAlign.Center),
            )
            return@Box
        }
        val s = tracker.stats()
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = tracker.name.uppercase(),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
            Text(
                text = s.number.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 34.sp else 52.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
            Text(
                text = s.unit,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 11.sp else 14.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            if (!compact) {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "Next ${s.milestone.next} · ${s.milestone.toGo} to go",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center),
                )
                if (s.kind == TrackerType.HABIT) {
                    Spacer(GlanceModifier.height(6.dp))
                    if (s.doneToday) {
                        Text(
                            text = "Checked in today",
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    } else {
                        Button(
                            text = "Check in",
                            onClick = actionRunCallback<CheckInAction>(actionParametersOf(TrackerIdKey to tracker.id)),
                        )
                    }
                }
            }
        }
    }
}

/** Widget button: mark today's habit done and refresh every widget. */
class CheckInAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TrackerIdKey] ?: return
        Repo.checkInToday(context, id)
        Sol1Widget().updateAll(context)
    }
}
