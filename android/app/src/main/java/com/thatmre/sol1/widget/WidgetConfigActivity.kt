package com.thatmre.sol1.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.thatmre.sol1.data.Repo
import com.thatmre.sol1.data.stats
import com.thatmre.sol1.ui.Sol1Theme
import kotlinx.coroutines.launch

/** Shown when a widget is added (optional) or reconfigured: pick which tracker it displays. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            Sol1Theme {
                ConfigScreen { trackerId ->
                    lifecycleScope.launch {
                        Repo.bindWidget(this@WidgetConfigActivity, appWidgetId, trackerId)
                        val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                        Sol1Widget().update(this@WidgetConfigActivity, glanceId)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(onPick: (String) -> Unit) {
    val context = LocalContext.current
    val trackers by Repo.flow(context).collectAsState(initial = emptyList())
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Show on this widget", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pick a tracker. You can change it later by long-pressing the widget.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            if (trackers.isEmpty()) {
                Text("No trackers yet. Open Sol-1 and add one first.")
            }
            LazyColumn {
                items(trackers, key = { it.id }) { t ->
                    val s = t.stats()
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onPick(t.id) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium)
                            Text("${s.number} ${s.unit}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
