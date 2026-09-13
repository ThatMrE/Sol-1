package com.thatmre.sol1.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thatmre.sol1.data.Repo
import com.thatmre.sol1.data.Stats
import com.thatmre.sol1.data.Tracker
import com.thatmre.sol1.data.TrackerType
import com.thatmre.sol1.data.plural
import com.thatmre.sol1.data.stats
import com.thatmre.sol1.widget.Refresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Sol1Theme { Sol1App() }
        }
        Refresh.scheduleMidnight(this)
    }
}

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy HH:mm")

private fun Long.toLocalDateTime(): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sol1App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trackers by Repo.flow(context).collectAsState(initial = emptyList())

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tracker?>(null) }
    var slipping by remember { mutableStateOf<Tracker?>(null) }
    var deleting by remember { mutableStateOf<Tracker?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Sol-1", fontWeight = FontWeight.Bold)
                    Text(
                        "Day one. Then day two.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add tracker") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (trackers.isEmpty()) {
                item { EmptyState() }
            }
            items(trackers, key = { it.id }) { t ->
                TrackerCard(
                    tracker = t,
                    now = now,
                    onCheckIn = {
                        scope.launch {
                            Repo.toggleToday(context, t.id)
                            Refresh.updateWidgets(context)
                        }
                    },
                    onSlip = { slipping = t },
                    onEdit = { editing = t },
                    onDelete = { deleting = t },
                )
            }
            item { WidgetHelp() }
        }
    }

    if (showAdd || editing != null) {
        TrackerDialog(
            existing = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { t ->
                scope.launch {
                    Repo.upsert(context, t)
                    Refresh.updateWidgets(context)
                }
                showAdd = false
                editing = null
            },
        )
    }

    slipping?.let { t ->
        AlertDialog(
            onDismissRequest = { slipping = null },
            title = { Text("Reset the count?") },
            text = {
                val s = t.stats(now)
                Text(
                    "“${t.name}” is at ${plural(s.days, "day")}, ${s.hours}h. This logs a reset and " +
                        "starts the count again from right now.\n\nA reset isn't a failure, it's a data point. " +
                        "Your longest streak and total days are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        Repo.slip(context, t.id)
                        Refresh.updateWidgets(context)
                    }
                    slipping = null
                }) { Text("Reset to now") }
            },
            dismissButton = { TextButton(onClick = { slipping = null }) { Text("Keep going") } },
        )
    }

    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${t.name}”?") },
            text = { Text("Its history goes with it. Widgets showing it fall back to your first tracker.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        Repo.delete(context, t.id)
                        Refresh.updateWidgets(context)
                    }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyState() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No trackers yet.", fontWeight = FontWeight.SemiBold)
            Text(
                "Add a “days since” counter for sobriety, or a daily habit you want to keep.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun WidgetHelp() {
    Column(Modifier.padding(top = 8.dp)) {
        Text("Put it on your lock screen", style = MaterialTheme.typography.titleSmall)
        Text(
            "Settings → Display & touch → Lock screen → turn on Widgets on lock screen. " +
                "Then swipe to the widget page on the lock screen, tap Add, and pick Sol-1. " +
                "Long-press the widget to change which tracker it shows. " +
                "The same widget works on the home screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TrackerCard(
    tracker: Tracker,
    now: Long,
    onCheckIn: () -> Unit,
    onSlip: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s: Stats = tracker.stats(now)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tracker.name.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    s.number.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 68.sp,
                )
                Text(
                    s.unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
            val live = if (s.kind == TrackerType.COUNT) {
                val started = tracker.start.toLocalDateTime().format(dateFmt)
                "${plural(s.days, "day")}, ${s.hours}h ${"%02d".format(s.mins)}m ${"%02d".format(s.secs)}s · since $started"
            } else if (s.doneToday) "Checked in today ✓" else "Not checked in yet today"
            Text(live, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            LinearProgressIndicator(
                progress = { s.milestone.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
            )
            Text(
                "Next milestone: ${s.milestone.next} days · ${plural(s.milestone.toGo, "day")} to go",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Longest", s.longest)
                Stat("Total", s.total)
                if (s.kind == TrackerType.COUNT) Stat("Resets", s.resets)
            }
            if (s.kind == TrackerType.HABIT) HabitGrid(s.grid)

            Spacer(Modifier.height(14.dp))
            if (s.kind == TrackerType.HABIT) {
                if (s.doneToday) OutlinedButton(onClick = onCheckIn) { Text("Undo today") }
                else Button(onClick = onCheckIn) { Text("Check in today") }
            } else {
                FilledTonalButton(onClick = onSlip) { Text("I slipped") }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" $value", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HabitGrid(grid: List<Boolean>) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        grid.chunked(14).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { done ->
                    Box(Modifier.size(14.dp).background(if (done) on else off, CircleShape))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackerDialog(existing: Tracker?, onDismiss: () -> Unit, onSave: (Tracker) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: TrackerType.COUNT) }
    var start by remember {
        mutableStateOf(
            if (existing != null && existing.type == TrackerType.COUNT) existing.start.toLocalDateTime()
            else LocalDateTime.now(),
        )
    }
    var showDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New tracker" else "Edit tracker") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    placeholder = { Text("Sober, No nicotine, Gym, Read…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (existing == null) {
                    Spacer(Modifier.height(12.dp))
                    TypeChoice("Days since", type == TrackerType.COUNT) { type = TrackerType.COUNT }
                    TypeChoice("Daily habit", type == TrackerType.HABIT) { type = TrackerType.HABIT }
                }
                if (type == TrackerType.COUNT) {
                    Spacer(Modifier.height(12.dp))
                    Text("Counting from", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(start.format(dateTimeFmt))
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { start = LocalDateTime.now() }) { Text("Now") }
                        TextButton(onClick = { start = start.with(LocalTime.MIDNIGHT) }) { Text("Midnight that day") }
                    }
                    Text(
                        "Your last drink, last cigarette, or the moment you decided. Days and hours count up from here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tap “Check in” each day you do it. Miss a day and the streak restarts; your history is kept.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val startMs = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val t = if (existing != null) {
                        existing.copy(
                            name = name.trim(),
                            start = if (existing.type == TrackerType.COUNT) startMs else existing.start,
                        )
                    } else {
                        Tracker(
                            id = Repo.newId(),
                            name = name.trim(),
                            type = type,
                            start = if (type == TrackerType.COUNT) startMs else 0L,
                        )
                    }
                    onSave(t)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDate) {
        val utcMidnight = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = utcMidnight)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        start = LocalDateTime.of(date, start.toLocalTime())
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun TypeChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.width(200.dp))
    }
}
