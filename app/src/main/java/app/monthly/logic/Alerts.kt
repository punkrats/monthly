package app.monthly.logic

import app.monthly.model.Event
import app.monthly.model.Reminder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** A reminder due at [at] for the occurrence of [event] on [date]. */
data class Alert(
  val at: Instant,
  val event: Event,
  val date: LocalDate,
  val reminder: Reminder
)

object Alerts {

  private val searchWindows = listOf(2L, 14L, 62L, 400L)

  fun triggerAt(
    event: Event,
    date: LocalDate,
    reminder: Reminder,
    device: ZoneId
  ): Instant = when (reminder) {
    is Reminder.Before -> {
      val time = event.startTime ?: LocalTime.MIDNIGHT
      val start = date.atTime(time).atZone(event.zoneOr(device)).toInstant()
      start.minus(Duration.ofMinutes(reminder.minutes.toLong()))
    }
    is Reminder.OnDay -> {
      val zone = if (reminder.theirZone) event.zoneOr(device) else device
      val day = date.minusDays(reminder.daysBefore.toLong())
      day.atTime(reminder.time).atZone(zone).toInstant()
    }
  }

  /** Alerts with `after < at <= until`, earliest first. */
  fun between(
    events: List<Event>,
    after: Instant,
    until: Instant,
    device: ZoneId
  ): List<Alert> = events
    .filter { it.reminders.isNotEmpty() }
    .flatMap { alertsOf(it, after, until, device) }
    .sortedBy { it.at }

  /** Searches growing windows so frequent repeats stay cheap. */
  fun next(events: List<Event>, after: Instant, device: ZoneId): Instant? {
    for (days in searchWindows) {
      val until = after.plus(Duration.ofDays(days))
      val found = between(events, after, until, device)
      if (found.isNotEmpty()) return found.first().at
    }
    return null
  }

  private fun alertsOf(
    event: Event,
    after: Instant,
    until: Instant,
    device: ZoneId
  ): List<Alert> {
    val lead = event.reminders.maxOf { leadDays(it) }
    val lag = event.reminders.maxOf { leadDays(it, after = true) }
    val from = after.atZone(ZoneOffset.UTC).toLocalDate().minusDays(lag + 2)
    val to = until.atZone(ZoneOffset.UTC).toLocalDate().plusDays(lead + 2)

    return event.datesBetween(from, to)
      .flatMap { date ->
        event.reminders.map { reminder ->
          val at = triggerAt(event, date, reminder, device)
          Alert(at, event, date, reminder)
        }
      }
      .filter { it.at > after && it.at <= until }
  }

  /** Whole days a reminder fires ahead of (or [after]) its occurrence. */
  private fun leadDays(reminder: Reminder, after: Boolean = false): Long {
    val minutes = when (reminder) {
      is Reminder.Before -> reminder.minutes.toLong()
      is Reminder.OnDay -> reminder.daysBefore * 24L * 60
    }
    val signed = if (after) -minutes else minutes
    return (signed.coerceAtLeast(0) + 24 * 60 - 1) / (24 * 60)
  }
}
