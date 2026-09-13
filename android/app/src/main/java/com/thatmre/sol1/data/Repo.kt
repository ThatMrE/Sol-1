package com.thatmre.sol1.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

private val Context.store: DataStore<Preferences> by preferencesDataStore("sol1")
private val TRACKERS = stringPreferencesKey("trackers")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val listSerializer = ListSerializer(Tracker.serializer())

/** All persistence: a JSON list of trackers plus one key per widget instance naming its tracker. */
object Repo {
    fun flow(context: Context): Flow<List<Tracker>> = context.store.data.map { decode(it[TRACKERS]) }

    suspend fun load(context: Context): List<Tracker> = decode(context.store.data.first()[TRACKERS])

    private fun decode(s: String?): List<Tracker> =
        if (s.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(listSerializer, s) }.getOrDefault(emptyList())

    private suspend fun save(context: Context, list: List<Tracker>) {
        context.store.edit { it[TRACKERS] = json.encodeToString(listSerializer, list) }
    }

    suspend fun upsert(context: Context, t: Tracker) {
        val list = load(context)
        save(context, if (list.any { it.id == t.id }) list.map { if (it.id == t.id) t else it } else list + t)
    }

    suspend fun delete(context: Context, id: String) = save(context, load(context).filter { it.id != id })

    /** Habit: toggle today's check-in. */
    suspend fun toggleToday(context: Context, id: String) = mutate(context, id) { t ->
        val key = dayKey(LocalDate.now())
        val set = t.checkins.toMutableSet()
        if (!set.add(key)) set.remove(key)
        t.copy(checkins = set.sorted())
    }

    /** Habit: mark today done (idempotent, used by the widget button). */
    suspend fun checkInToday(context: Context, id: String) = mutate(context, id) { t ->
        t.copy(checkins = (t.checkins.toSet() + dayKey(LocalDate.now())).sorted())
    }

    /** Count: log the streak that just ended and restart from now. */
    suspend fun slip(context: Context, id: String) = mutate(context, id) { t ->
        val now = System.currentTimeMillis()
        val days = t.stats(now).days
        t.copy(start = now, history = t.history + Reset(t.start, now, days))
    }

    private suspend fun mutate(context: Context, id: String, f: (Tracker) -> Tracker) {
        save(context, load(context).map { if (it.id == id) f(it) else it })
    }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    // ---- widget bindings ----

    private fun widgetKey(appWidgetId: Int) = stringPreferencesKey("widget_$appWidgetId")

    /** The tracker a widget instance shows; falls back to the first tracker when unconfigured. */
    suspend fun trackerForWidget(context: Context, appWidgetId: Int): Tracker? {
        val prefs = context.store.data.first()
        val list = decode(prefs[TRACKERS])
        val bound = prefs[widgetKey(appWidgetId)]
        return list.firstOrNull { it.id == bound } ?: list.firstOrNull()
    }

    suspend fun bindWidget(context: Context, appWidgetId: Int, trackerId: String) {
        context.store.edit { it[widgetKey(appWidgetId)] = trackerId }
    }

    suspend fun unbindWidgets(context: Context, appWidgetIds: IntArray) {
        context.store.edit { prefs -> appWidgetIds.forEach { prefs.remove(widgetKey(it)) } }
    }
}
