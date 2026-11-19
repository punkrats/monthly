package app.monthly.ui

import android.content.Context
import android.text.format.DateFormat
import app.monthly.R
import app.monthly.logic.Alert
import app.monthly.logic.Occurrences
import app.monthly.model.Event
import app.monthly.model.Freq
import app.monthly.model.NthWeekday
import app.monthly.model.Recurrence
import app.monthly.model.Reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Locale-aware text for dates, zones, repeat rules and reminders. */
class Format(context: Context) {

  private val res = context.resources
  val locale: Locale = res.configuration.locales[0]

  private val timeFormat =
    pattern(if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
  private val dayMonthFormat = pattern("dMMM")
  private val weekdayFormat = pattern("EEEEdMMMM")
  private val shortWeekdayFormat = pattern("EEEdMMM")
  private val monthFormat = pattern("MMMMy")
  private val dateFormat = pattern("yMMMd")

  private fun pattern(skeleton: String): DateTimeFormatter {
    val best = DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(best, locale)
  }

  fun time(time: LocalTime): String = time.format(timeFormat)

  fun dayMonth(date: LocalDate): String = date.format(dayMonthFormat)

  fun weekday(date: LocalDate): String = date.format(weekdayFormat)

  fun shortWeekday(date: LocalDate): String = date.format(shortWeekdayFormat)

  fun month(month: YearMonth): String = month.format(monthFormat)

  fun date(date: LocalDate): String = date.format(dateFormat)

  fun dayName(day: DayOfWeek, style: TextStyle = TextStyle.SHORT): String =
    day.getDisplayName(style, locale)

  // Zones

  fun city(zone: ZoneId): String =
    zone.id.substringAfterLast('/').replace('_', ' ')

  fun offset(zone: ZoneId, at: Instant = Instant.now()): String {
    val seconds = zone.rules.getOffset(at).totalSeconds
    if (seconds == 0) return "UTC"

    val sign = if (seconds < 0) "-" else "+"
    val hours = Math.abs(seconds) / 3600
    val minutes = Math.abs(seconds) % 3600 / 60
    val rest = if (minutes == 0) "" else ":" + "$minutes".padStart(2, '0')
    return "UTC$sign$hours$rest"
  }

  fun zoneLabel(zone: ZoneId): String = "${city(zone)} (${offset(zone)})"

  /** True when both zones show the same wall clock at [at]. */
  fun sameClock(a: ZoneId, b: ZoneId, at: Instant): Boolean =
    a.rules.getOffset(at) == b.rules.getOffset(at)

  // Relative days

  fun relativeDay(date: LocalDate, today: LocalDate): String =
    when (ChronoUnit.DAYS.between(today, date)) {
      0L -> res.getString(R.string.today)
      1L -> res.getString(R.string.tomorrow)
      -1L -> res.getString(R.string.yesterday)
      else -> if (date.year == today.year) shortWeekday(date) else date(date)
    }

  // Reminders

  fun reminder(reminder: Reminder, event: Event): String = when (reminder) {
    is Reminder.Before -> before(reminder.minutes)
    is Reminder.OnDay -> onDay(reminder, event.zone != null)
  }

  fun before(minutes: Int): String = when {
    minutes == 0 -> res.getString(R.string.reminder_at_start)
    minutes % 10080 == 0 -> plural(R.plurals.reminder_weeks, minutes / 10080)
    minutes % 1440 == 0 -> plural(R.plurals.reminder_days, minutes / 1440)
    minutes % 60 == 0 -> plural(R.plurals.reminder_hours, minutes / 60)
    else -> plural(R.plurals.reminder_minutes, minutes)
  }

  fun onDay(reminder: Reminder.OnDay, zoned: Boolean): String {
    val day = when (reminder.daysBefore) {
      0 -> res.getString(R.string.reminder_on_the_day)
      else -> plural(R.plurals.reminder_days, reminder.daysBefore)
    }
    val text = res.getString(R.string.reminder_at, day, time(reminder.time))
    if (!zoned) return text

    val clock =
      if (reminder.theirZone) R.string.their_time else R.string.my_time
    return "$text · ${res.getString(clock)}"
  }

  // Repeat rules

  fun repeat(rule: Recurrence?, start: LocalDate): String {
    rule ?: return res.getString(R.string.repeat_none)

    val every = when (rule.freq) {
      Freq.DAILY -> plural(R.plurals.every_day, rule.interval)
      Freq.WEEKLY -> plural(R.plurals.every_week, rule.interval)
      Freq.MONTHLY -> plural(R.plurals.every_month, rule.interval)
      Freq.YEARLY -> plural(R.plurals.every_year, rule.interval)
    }
    val detail = repeatDetail(rule, start)
    val base = if (detail == null) every else "$every $detail"
    val end = when {
      rule.until != null ->
        res.getString(R.string.repeat_until, date(rule.until))
      rule.count != null -> plural(R.plurals.times, rule.count)
      else -> null
    }
    return if (end == null) base else "$base, $end"
  }

  private fun repeatDetail(rule: Recurrence, start: LocalDate): String? {
    val nth = rule.nth
    return when {
      rule.freq == Freq.WEEKLY && rule.byDay.isNotEmpty() -> {
        val days = rule.byDay.sorted().joinToString(", ") { dayName(it) }
        res.getString(R.string.repeat_on, days)
      }
      nth != null && rule.freq == Freq.MONTHLY -> nthText(nth)
      nth != null -> {
        val month = start.month.getDisplayName(TextStyle.FULL, locale)
        res.getString(R.string.repeat_in_month, nthText(nth), month)
      }
      rule.freq == Freq.MONTHLY ->
        res.getString(R.string.repeat_on_day, start.dayOfMonth)
      else -> null
    }
  }

  fun nthText(nth: NthWeekday): String {
    val ordinals = res.getStringArray(R.array.ordinals)
    val ordinal = if (nth.n < 0) {
      res.getString(R.string.ordinal_last)
    } else {
      ordinals[(nth.n - 1).coerceIn(0, ordinals.lastIndex)]
    }
    val day = dayName(nth.day, TextStyle.FULL)
    return res.getString(R.string.repeat_on_nth, ordinal, day)
  }

  // Notifications

  fun alertTitle(alert: Alert): String {
    val event = alert.event
    val name = event.title.ifBlank { res.getString(R.string.untitled) }
    if (!event.isBirthday) return name

    val age = event.ageOn(alert.date)
    if (age != null && age > 0) {
      return res.getString(R.string.birthday_turns, name, age)
    }
    return res.getString(R.string.birthday_of, name)
  }

  /**
   * "Today 10:00 · 16:00 in New York", or for a friend's birthday
   * "Today · 09:00 in Tokyo, 01:00 here".
   */
  fun alertBody(alert: Alert, device: ZoneId): String {
    val event = alert.event
    val zone = event.zoneOr(device)
    val start = Occurrences.of(event, alert.date, device).start
    val parts = mutableListOf<String>()

    if (start != null) {
      val today = alert.at.atZone(device).toLocalDate()
      val day = relativeDay(start.toLocalDate(), today)
      parts += "$day ${time(start.toLocalTime())}"
      if (!sameClock(zone, device, start.toInstant())) {
        val there = start.withZoneSameInstant(zone).toLocalTime()
        parts += res.getString(R.string.time_in, time(there), city(zone))
      }
    } else {
      val today = alert.at.atZone(zone).toLocalDate()
      parts += relativeDay(alert.date, today)
      if (!sameClock(zone, device, alert.at)) {
        val there = time(alert.at.atZone(zone).toLocalTime())
        val here = time(alert.at.atZone(device).toLocalTime())
        val clock = R.string.clock_there_here
        parts += res.getString(clock, there, city(zone), here)
      }
    }

    if (event.location.isNotBlank()) parts += event.location
    return parts.joinToString(" · ")
  }

  fun plural(id: Int, count: Int): String =
    res.getQuantityString(id, count, count)
}
