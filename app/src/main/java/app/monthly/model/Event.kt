package app.monthly.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class EventKind { EVENT, BIRTHDAY }

/**
 * A calendar entry. Timed events keep their wall-clock time in [zone]
 * (null = floating, follows the device). All-day events treat [zone] as
 * the zone whose day it is, e.g. a friend's zone for a birthday.
 * [endDate] is inclusive for all-day events.
 */
data class Event(
  val id: Long = 0,
  val uid: String,
  val kind: EventKind = EventKind.EVENT,
  val title: String,
  val allDay: Boolean,
  val startDate: LocalDate,
  val startTime: LocalTime? = null,
  val endDate: LocalDate = startDate,
  val endTime: LocalTime? = null,
  val zone: ZoneId? = null,
  val location: String = "",
  val notes: String = "",
  val color: EventColor = EventColor.BLUE,
  val recurrence: Recurrence? = null,
  val exceptions: Set<LocalDate> = emptySet(),
  val reminders: List<Reminder> = emptyList(),
  val birthYear: Int? = null
) {
  val isBirthday get() = kind == EventKind.BIRTHDAY

  val isTimed get() = !allDay && startTime != null

  fun zoneOr(device: ZoneId): ZoneId = zone ?: device

  /** Days from the first to the last covered calendar day. */
  val spanDays: Long
    get() = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0)

  fun duration(device: ZoneId): Duration {
    val time = startTime
    if (allDay || time == null) return Duration.ZERO

    val zone = zoneOr(device)
    val start = startDate.atTime(time).atZone(zone)
    val end = endDate.atTime(endTime ?: time).atZone(zone)
    val length = Duration.between(start, end)
    return if (length.isNegative) Duration.ZERO else length
  }

  fun ageOn(date: LocalDate): Int? = birthYear?.let { date.year - it }
}

/** Palette entries carry a CSS colour name for the RFC 7986 COLOR property. */
enum class EventColor(val css: String) {
  BLUE("royalblue"),
  TEAL("teal"),
  GREEN("seagreen"),
  YELLOW("goldenrod"),
  ORANGE("darkorange"),
  RED("tomato"),
  PINK("hotpink"),
  PURPLE("mediumpurple"),
  GRAY("slategray");

  companion object {
    fun fromCss(name: String?): EventColor? =
      entries.firstOrNull { it.css.equals(name?.trim(), ignoreCase = true) }
  }
}
