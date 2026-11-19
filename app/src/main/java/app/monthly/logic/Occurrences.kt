package app.monthly.logic

import app.monthly.model.Event
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One instance of an event. [date] is the start date in the event's own
 * calendar (the key for exceptions). [start] / [end] are timed instances
 * on the device clock; [firstDay]..[lastDay] are the device-local days the
 * instance covers.
 */
data class Occurrence(
  val event: Event,
  val date: LocalDate,
  val start: ZonedDateTime?,
  val end: ZonedDateTime?,
  val firstDay: LocalDate,
  val lastDay: LocalDate
)

object Occurrences {

  private val order = compareBy<Occurrence>(
    { it.firstDay },
    { it.start != null },
    { it.start?.toInstant() },
    { it.event.title.lowercase() }
  )

  fun of(event: Event, date: LocalDate, device: ZoneId): Occurrence {
    val time = event.startTime
    if (event.allDay || time == null) {
      val last = date.plusDays(event.spanDays)
      return Occurrence(event, date, null, null, date, last)
    }

    val start = date.atTime(time)
      .atZone(event.zoneOr(device))
      .withZoneSameInstant(device)
    val end = start.plus(event.duration(device))
    val lastDay = if (end.isAfter(start)) {
      end.minusNanos(1).toLocalDate()
    } else {
      start.toLocalDate()
    }
    return Occurrence(event, date, start, end, start.toLocalDate(), lastDay)
  }

  /** Instances overlapping [from]..[to] on the device clock, sorted. */
  fun between(
    events: List<Event>,
    from: LocalDate,
    to: LocalDate,
    device: ZoneId
  ): List<Occurrence> =
    events.flatMap { inRange(it, from, to, device) }.sortedWith(order)

  fun byDay(
    events: List<Event>,
    from: LocalDate,
    to: LocalDate,
    device: ZoneId
  ): Map<LocalDate, List<Occurrence>> {
    val days = mutableMapOf<LocalDate, MutableList<Occurrence>>()
    for (occurrence in between(events, from, to, device)) {
      var day = maxOf(occurrence.firstDay, from)
      val last = minOf(occurrence.lastDay, to)
      while (day <= last) {
        days.getOrPut(day) { mutableListOf() } += occurrence
        day = day.plusDays(1)
      }
    }
    return days
  }

  /** Two days of margin cover any zone shift between event and device. */
  private fun inRange(
    event: Event,
    from: LocalDate,
    to: LocalDate,
    device: ZoneId
  ): List<Occurrence> {
    val length = maxOf(event.spanDays, event.duration(device).toDays())
    val dates = event.datesBetween(from.minusDays(length + 2), to.plusDays(2))
    return dates
      .map { of(event, it, device) }
      .filter { it.lastDay >= from && it.firstDay <= to }
  }
}
