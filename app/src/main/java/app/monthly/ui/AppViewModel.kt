package app.monthly.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.monthly.MonthlyApp
import app.monthly.R
import app.monthly.alarm.ReminderScheduler
import app.monthly.data.Settings
import app.monthly.ics.IcsImport
import app.monthly.ics.IcsReader
import app.monthly.ics.IcsWriter
import app.monthly.logic.Occurrence
import app.monthly.logic.nearbyStart
import app.monthly.model.Event
import app.monthly.model.EventKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
  data object Calendar : Screen
  data object Agenda : Screen
  data object Settings : Screen
  data class Editor(
    val event: Event?,
    val kind: EventKind,
    val date: LocalDate
  ) : Screen
}

/** A recorded cycle start the user may move to another day. */
data class CycleMove(val from: LocalDate, val to: LocalDate)

/** A snackbar message, optionally with an undo action. */
data class Notice(val text: String, val undo: (() -> Unit)? = null)

class AppViewModel(application: Application) :
  AndroidViewModel(application) {

  private val app = application as MonthlyApp
  private val repository = app.repository

  val events = repository.events
  val cycleStarts = repository.cycleStarts
  val settings = app.settings.settings

  var zone by mutableStateOf(ZoneId.systemDefault())
    private set
  var today by mutableStateOf(LocalDate.now())
    private set
  var notificationsOn by mutableStateOf(true)
    private set

  var selected by mutableStateOf(LocalDate.now())
  var detail by mutableStateOf<Occurrence?>(null)
  var pendingImport by mutableStateOf<IcsImport?>(null)
  var pendingMove by mutableStateOf<CycleMove?>(null)
  var notice by mutableStateOf<Notice?>(null)

  val screens = mutableStateListOf<Screen>(Screen.Calendar)

  init {
    background { repository.load() }
  }

  // Navigation

  fun open(screen: Screen) {
    detail = null
    screens += screen
  }

  fun back() {
    if (screens.size > 1) screens.removeAt(screens.lastIndex)
  }

  fun jumpTo(date: LocalDate) {
    selected = date
    while (screens.size > 1) back()
  }

  /** Clock, zone and permission may have changed while away. */
  fun resume() {
    zone = ZoneId.systemDefault()
    val now = LocalDate.now()
    if (now != today && selected == today) selected = now
    today = now
    notificationsOn =
      NotificationManagerCompat.from(app).areNotificationsEnabled()
    background { ReminderScheduler.deliverDue(app) }
  }

  // Events

  fun save(event: Event) {
    back()
    background { repository.save(event) }
  }

  fun delete(event: Event) {
    detail = null
    if (screens.last() is Screen.Editor) back()
    background { repository.delete(event.id) }
    notice = Notice(text(R.string.deleted)) {
      background { repository.save(event.copy(id = 0)) }
    }
  }

  fun deleteOne(occurrence: Occurrence) {
    detail = null
    val event = occurrence.event
    background { repository.addException(event, occurrence.date) }
    notice = Notice(text(R.string.deleted)) {
      background { repository.save(event) }
    }
  }

  // Cycle

  /**
   * Clears [date] when it is a start, else marks it. Next to an existing
   * start it asks first whether to move that one.
   */
  fun toggleCycleStart(date: LocalDate) {
    val starts = cycleStarts.value
    val near = nearbyStart(starts, date)
    when {
      date in starts -> setCycleStart(date, false)
      near != null -> pendingMove = CycleMove(near, date)
      else -> setCycleStart(date, true)
    }
  }

  fun moveCycleStart(move: CycleMove) {
    pendingMove = null
    background { repository.moveCycleStart(move.from, move.to) }
    val day = Format(app).dayMonth(move.to)
    notice = Notice(app.getString(R.string.cycle_moved, day)) {
      background { repository.moveCycleStart(move.to, move.from) }
    }
  }

  fun setCycleStart(date: LocalDate, started: Boolean) {
    background { repository.setCycleStart(date, started) }
    val format = Format(app)
    val message = if (started) R.string.cycle_saved else R.string.cycle_removed
    notice = Notice(app.getString(message, format.dayMonth(date))) {
      background { repository.setCycleStart(date, !started) }
    }
  }

  fun updateSettings(transform: (Settings) -> Settings) =
    app.settings.update(transform)

  // Import / export

  fun prepareImport(uri: Uri) = background {
    val content = runCatching {
      app.contentResolver.openInputStream(uri)
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()
    val result = content?.let { IcsReader.read(it, ZoneId.systemDefault()) }

    withContext(Dispatchers.Main) {
      val empty = result == null ||
        result.events.isEmpty() && result.cycleStarts.isEmpty()
      when {
        content == null -> notice = Notice(text(R.string.import_unreadable))
        empty -> notice = Notice(text(R.string.import_nothing))
        else -> pendingImport = result
      }
    }
  }

  fun confirmImport() {
    val result = pendingImport ?: return
    pendingImport = null
    background { repository.import(result) }
    val count = result.events.size + result.cycleStarts.size
    notice = Notice(Format(app).plural(R.plurals.imported, count))
  }

  fun export(uri: Uri, includeCycle: Boolean) = background {
    val cycle = if (includeCycle) cycleStarts.value else emptyList()
    val title = text(R.string.cycle_start)
    val ics = IcsWriter.write(events.value, cycle, title, Instant.now())
    val written = runCatching {
      app.contentResolver.openOutputStream(uri, "wt")
        ?.use { it.write(ics.toByteArray(Charsets.UTF_8)) }
    }.isSuccess

    val count = events.value.size + cycle.size
    val message = if (written) {
      Format(app).plural(R.plurals.exported, count)
    } else {
      text(R.string.export_failed)
    }
    withContext(Dispatchers.Main) { notice = Notice(message) }
  }

  private fun text(id: Int) = app.getString(id)

  private fun background(block: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) { block() }
  }
}
