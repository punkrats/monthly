package app.monthly

import app.monthly.model.Event
import app.monthly.model.EventKind
import app.monthly.model.Freq
import app.monthly.model.Recurrence
import app.monthly.model.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
val LOS_ANGELES: ZoneId = ZoneId.of("America/Los_Angeles")

fun date(text: String): LocalDate = LocalDate.parse(text)

fun time(text: String): LocalTime = LocalTime.parse(text)

fun allDay(
  start: String,
  end: String = start,
  rule: Recurrence? = null,
  zone: ZoneId? = null,
  reminders: List<Reminder> = emptyList(),
  title: String = "All day"
) = Event(
  uid = "all-day-$start-$title",
  title = title,
  allDay = true,
  startDate = date(start),
  endDate = date(end),
  zone = zone,
  recurrence = rule,
  reminders = reminders
)

fun timed(
  start: String,
  from: String,
  to: String,
  zone: ZoneId?,
  rule: Recurrence? = null,
  reminders: List<Reminder> = emptyList(),
  endDate: String = start,
  title: String = "Timed"
) = Event(
  uid = "timed-$start-$from-$title",
  title = title,
  allDay = false,
  startDate = date(start),
  startTime = time(from),
  endDate = date(endDate),
  endTime = time(to),
  zone = zone,
  recurrence = rule,
  reminders = reminders
)

fun birthday(
  day: String,
  zone: ZoneId?,
  reminders: List<Reminder> = emptyList(),
  birthYear: Int? = null
) = allDay(day, zone = zone, reminders = reminders, title = "Birthday")
  .copy(
    kind = EventKind.BIRTHDAY,
    recurrence = Recurrence(Freq.YEARLY),
    birthYear = birthYear
  )
