package app.monthly.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.monthly.data.Settings
import app.monthly.model.Event
import app.monthly.model.EventColor
import app.monthly.model.EventKind
import app.monthly.model.Freq
import app.monthly.model.Recurrence
import app.monthly.model.Reminder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

sealed interface EditorDialog {
  data object StartDate : EditorDialog
  data object StartTime : EditorDialog
  data object EndDate : EditorDialog
  data object EndTime : EditorDialog
  data object Zone : EditorDialog
  data object Repeat : EditorDialog
  data object NewReminder : EditorDialog
}

/**
 * The editable form of an event or birthday. Birthdays without a known
 * year are stored in a leap year so Feb 29 survives.
 */
@Stable
class EditorState(
  val original: Event?,
  kind: EventKind,
  date: LocalDate,
  private val settings: Settings,
  val device: ZoneId
) {

  private val uid = original?.uid ?: UUID.randomUUID().toString()
  private val firstHour = LocalTime.now()
    .plusHours(1)
    .truncatedTo(ChronoUnit.HOURS)

  var kind by mutableStateOf(original?.kind ?: kind)
    private set
  var title by mutableStateOf(original?.title.orEmpty())
  var titleError by mutableStateOf(false)
  var allDay by mutableStateOf(original?.allDay ?: (kind == EventKind.BIRTHDAY))
    private set
  var startDate by mutableStateOf(original?.startDate ?: date)
  var startTime by mutableStateOf(original?.startTime ?: firstHour)
  var endDate by mutableStateOf(original?.endDate ?: date)
  var endTime by mutableStateOf(original?.endTime ?: firstHour.plusHours(1))
  var zone by mutableStateOf(original?.zone ?: defaultZone(kind))
  var location by mutableStateOf(original?.location.orEmpty())
  var notes by mutableStateOf(original?.notes.orEmpty())
  var color by mutableStateOf(original?.color ?: defaultColor(kind))
  var recurrence by mutableStateOf(original?.recurrence)
  var yearKnown by mutableStateOf(original?.birthYear != null)
  var dialog by mutableStateOf<EditorDialog?>(null)
  val reminders = mutableStateListOf<Reminder>().apply {
    addAll(original?.reminders ?: defaultReminders(kind, allDay))
  }

  val isBirthday get() = kind == EventKind.BIRTHDAY

  /** Reminders fire on a day (birthdays, all-day) or before a start. */
  val remindsOnDay get() = isBirthday || allDay

  fun switchKind(to: EventKind) {
    if (to == kind) return
    kind = to
    allDay = to == EventKind.BIRTHDAY
    zone = defaultZone(to)
    color = defaultColor(to)
    recurrence = null
    resetReminders()
  }

  fun changeAllDay(value: Boolean) {
    allDay = value
    resetReminders()
  }

  /** Moving the start keeps the duration. */
  fun moveStart(date: LocalDate = startDate, time: LocalTime = startTime) {
    val oldStart = startDate.atTime(startTime)
    val length = Duration.between(oldStart, endDate.atTime(endTime))
      .coerceAtLeast(Duration.ZERO)
    startDate = date
    startTime = time
    val end = date.atTime(time).plus(length)
    endDate = end.toLocalDate()
    endTime = end.toLocalTime()
  }

  fun addReminder(reminder: Reminder) {
    if (reminder !in reminders) reminders += reminder
  }

  /** The event as it would be saved, without validation. */
  fun draft(): Event {
    val blank =
      Event(uid = uid, title = "", allDay = true, startDate = startDate)
    val base = (original ?: blank)
      .copy(title = title.trim(), notes = notes.trim(), color = color)

    if (isBirthday) {
      val day = if (yearKnown) {
        startDate
      } else {
        startDate.withYear(PLACEHOLDER_YEAR)
      }
      return base.copy(
        kind = EventKind.BIRTHDAY, allDay = true,
        startDate = day, startTime = null, endDate = day, endTime = null,
        zone = zone, location = "",
        recurrence = Recurrence(Freq.YEARLY), exceptions = emptySet(),
        reminders = reminders.filterIsInstance<Reminder.OnDay>(),
        birthYear = if (yearKnown) day.year else null
      )
    }

    if (allDay) {
      return base.copy(
        kind = EventKind.EVENT, allDay = true,
        startDate = startDate, startTime = null,
        endDate = maxOf(endDate, startDate), endTime = null,
        zone = null, location = location.trim(), recurrence = recurrence,
        reminders = reminders.filterIsInstance<Reminder.OnDay>(),
        birthYear = null
      )
    }

    val start = startDate.atTime(startTime)
    val end = endDate.atTime(endTime).takeIf { it >= start }
      ?: start.plusHours(1)
    return base.copy(
      kind = EventKind.EVENT, allDay = false,
      startDate = startDate, startTime = startTime,
      endDate = end.toLocalDate(), endTime = end.toLocalTime(),
      zone = zone ?: device, location = location.trim(),
      recurrence = recurrence,
      reminders = reminders.filterIsInstance<Reminder.Before>(),
      birthYear = null
    )
  }

  /** Null (and an error shown) when the title is missing. */
  fun toEvent(): Event? {
    titleError = title.isBlank()
    return if (titleError) null else draft()
  }

  private fun resetReminders() {
    reminders.clear()
    reminders.addAll(defaultReminders(kind, allDay))
  }

  private fun defaultZone(kind: EventKind): ZoneId? =
    if (kind == EventKind.EVENT) device else null

  private fun defaultColor(kind: EventKind) =
    if (kind == EventKind.BIRTHDAY) EventColor.PINK else EventColor.BLUE

  private fun defaultReminders(
    kind: EventKind,
    allDay: Boolean
  ): List<Reminder> =
    when {
      kind == EventKind.BIRTHDAY ->
        listOf(Reminder.OnDay(0, settings.birthdayTime, theirZone = true))
      allDay -> emptyList()
      else -> listOfNotNull(settings.eventReminder?.let { Reminder.Before(it) })
    }

  private companion object {
    const val PLACEHOLDER_YEAR = 2000
  }
}
