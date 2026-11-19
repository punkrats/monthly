package app.monthly.data

import android.content.Context
import app.monthly.ics.IcsImport
import app.monthly.model.Event
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All events and cycle starts held in memory and mirrored to SQLite. Every
 * write reloads the snapshot and then calls [onChange] (alarm scheduling)
 * outside the lock, since the scheduler reads back through [snapshot].
 * Blocking: call from a background thread.
 */
class Repository(context: Context, private val onChange: () -> Unit) {

  private val db = Database(context)
  private val loaded = AtomicBoolean(false)
  private val eventState = MutableStateFlow<List<Event>>(emptyList())
  private val cycleState = MutableStateFlow<List<LocalDate>>(emptyList())

  val events: StateFlow<List<Event>> = eventState.asStateFlow()
  val cycleStarts: StateFlow<List<LocalDate>> = cycleState.asStateFlow()

  @Synchronized
  fun load() {
    eventState.value = db.events()
    cycleState.value = db.cycleStarts()
    loaded.set(true)
  }

  fun snapshot(): List<Event> {
    if (!loaded.get()) load()
    return eventState.value
  }

  fun save(event: Event): Long = write { db.save(event) }

  fun delete(id: Long) = write { db.delete(id) }

  fun addException(event: Event, date: LocalDate) = write {
    db.save(event.copy(exceptions = event.exceptions + date))
  }

  fun setCycleStart(date: LocalDate, started: Boolean) = write {
    if (started) db.addCycleStart(date) else db.removeCycleStart(date)
  }

  fun import(result: IcsImport) = write {
    db.upsertAll(result.events, result.cycleStarts)
  }

  private fun <T> write(block: () -> T): T {
    val result = synchronized(this) { block().also { load() } }
    onChange()
    return result
  }
}
