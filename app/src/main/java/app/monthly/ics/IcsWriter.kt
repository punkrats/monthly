package app.monthly.ics

import app.monthly.model.Event
import app.monthly.model.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Serialises events (and optionally cycle starts) as an RFC 5545 file. */
object IcsWriter {

  const val KIND = "X-MONTHLY-KIND"
  const val ZONE = "X-MONTHLY-ZONE"
  const val BIRTH_YEAR = "X-MONTHLY-BIRTH-YEAR"
  const val CLOCK = "X-MONTHLY-CLOCK"
  const val KIND_BIRTHDAY = "BIRTHDAY"
  const val KIND_CYCLE = "CYCLE_START"

  fun write(
    events: List<Event>,
    cycleStarts: List<LocalDate>,
    cycleTitle: String,
    stamp: Instant
  ): String {
    val stampText = IcsText.utc(LocalDateTime.ofInstant(stamp, ZoneOffset.UTC))
    val zones = events
      .filter { it.isTimed }
      .mapNotNull { it.zone }
      .filterNot { isUtc(it) }
      .distinct()
      .sortedBy { it.id }

    val lines = buildList {
      add("BEGIN:VCALENDAR")
      add("VERSION:2.0")
      add("PRODID:-//Monthly//Monthly Calendar//EN")
      add("CALSCALE:GREGORIAN")
      zones.forEach { addAll(VTimezone.lines(it, stamp)) }
      events.forEach { addAll(eventLines(it, stampText)) }
      cycleStarts.forEach { addAll(cycleLines(it, cycleTitle, stampText)) }
      add("END:VCALENDAR")
    }
    return lines.joinToString("") { IcsText.fold(it) + "\r\n" }
  }

  private fun eventLines(event: Event, stamp: String): List<String> =
    buildList {
      add("BEGIN:VEVENT")
      add("UID:${IcsText.escape(event.uid)}")
      add("DTSTAMP:$stamp")
      add("SUMMARY:${IcsText.escape(event.title)}")
      addAll(timeLines(event))
      event.recurrence?.let {
        add("RRULE:" + RRule.format(it, event.startDate, untilForm(event)))
      }
      exceptionLine(event)?.let { add(it) }
      if (event.location.isNotBlank()) {
        add("LOCATION:${IcsText.escape(event.location)}")
      }
      if (event.notes.isNotBlank()) {
        add("DESCRIPTION:${IcsText.escape(event.notes)}")
      }
      add("COLOR:${event.color.css}")
      if (event.isBirthday) {
        add("CATEGORIES:BIRTHDAY")
        add("$KIND:$KIND_BIRTHDAY")
      }
      event.birthYear?.let { add("$BIRTH_YEAR:$it") }
      if (event.allDay && event.zone != null) add("$ZONE:${event.zone.id}")
      event.reminders.forEach { addAll(alarmLines(event, it)) }
      add("END:VEVENT")
    }

  private fun timeLines(event: Event): List<String> {
    val startTime = event.startTime
    if (event.allDay || startTime == null) {
      val end = event.endDate.plusDays(1)
      return listOf(
        "DTSTART;VALUE=DATE:${IcsText.date(event.startDate)}",
        "DTEND;VALUE=DATE:${IcsText.date(end)}"
      )
    }

    val endTime = event.endTime ?: startTime
    return listOf(
      "DTSTART" + stamped(event.zone, event.startDate.atTime(startTime)),
      "DTEND" + stamped(event.zone, event.endDate.atTime(endTime))
    )
  }

  /** ";TZID=zone:local", ":localZ" for UTC, or ":local" when floating. */
  private fun stamped(zone: ZoneId?, local: LocalDateTime): String = when {
    zone == null -> ":" + IcsText.dateTime(local)
    isUtc(zone) -> ":" + IcsText.utc(local)
    else -> ";TZID=${zone.id}:" + IcsText.dateTime(local)
  }

  private fun untilForm(event: Event): UntilForm {
    val zone = event.zone
    return when {
      !event.isTimed -> UntilForm.Date
      zone == null -> UntilForm.Floating
      else -> UntilForm.Utc(zone)
    }
  }

  private fun exceptionLine(event: Event): String? {
    if (event.exceptions.isEmpty()) return null

    val dates = event.exceptions.sorted()
    val time = event.startTime
    if (event.allDay || time == null) {
      val values = dates.joinToString(",") { IcsText.date(it) }
      return "EXDATE;VALUE=DATE:$values"
    }

    val stamps = dates.map { stamped(event.zone, it.atTime(time)) }
    val head = stamps.first().substringBefore(':')
    val values = stamps.joinToString(",") { it.substringAfter(':') }
    return "EXDATE$head:$values"
  }

  private fun alarmLines(event: Event, reminder: Reminder): List<String> =
    buildList {
      add("BEGIN:VALARM")
      add("ACTION:DISPLAY")
      add("DESCRIPTION:${IcsText.escape(event.title)}")
      when (reminder) {
        is Reminder.Before -> {
          add("TRIGGER:" + IcsText.formatDuration(-reminder.minutes.toLong()))
        }
        is Reminder.OnDay -> {
          val minutes = minutesOf(reminder.time) - reminder.daysBefore * 1440L
          add("TRIGGER:" + IcsText.formatDuration(minutes))
          add("$CLOCK:" + if (reminder.theirZone) "THEIRS" else "MINE")
        }
      }
      add("END:VALARM")
    }

  private fun cycleLines(
    date: LocalDate,
    title: String,
    stamp: String
  ): List<String> = listOf(
    "BEGIN:VEVENT",
    "UID:cycle-${IcsText.date(date)}@monthly",
    "DTSTAMP:$stamp",
    "SUMMARY:${IcsText.escape(title)}",
    "DTSTART;VALUE=DATE:${IcsText.date(date)}",
    "DTEND;VALUE=DATE:${IcsText.date(date.plusDays(1))}",
    "TRANSP:TRANSPARENT",
    "$KIND:$KIND_CYCLE",
    "END:VEVENT"
  )

  private fun minutesOf(time: LocalTime) = time.hour * 60L + time.minute

  internal fun isUtc(zone: ZoneId) = zone.normalized() == ZoneOffset.UTC
}
