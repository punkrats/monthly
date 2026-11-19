package app.monthly.ics

import app.monthly.model.Freq
import app.monthly.model.NthWeekday
import app.monthly.model.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** How UNTIL is written: it must match the value type of DTSTART. */
sealed interface UntilForm {
  data object Date : UntilForm
  data object Floating : UntilForm
  data class Utc(val zone: ZoneId) : UntilForm
}

/**
 * RRULE text for [Recurrence]. Also the database storage form. Rules the
 * model cannot express parse to null so callers can tell the user.
 */
object RRule {

  private val supportedKeys = setOf(
    "FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH",
    "WKST"
  )

  private val dayToken = Regex("([+-]?\\d{1,2})?(MO|TU|WE|TH|FR|SA|SU)")

  fun format(
    rule: Recurrence,
    start: LocalDate,
    until: UntilForm = UntilForm.Date
  ): String {
    val parts = mutableListOf("FREQ=${rule.freq.name}")
    if (rule.interval > 1) parts += "INTERVAL=${rule.interval}"

    if (rule.byDay.isNotEmpty()) {
      val days = rule.byDay.sorted().joinToString(",") { IcsText.dayCode(it) }
      parts += "BYDAY=$days"
    }

    rule.nth?.let { nth ->
      if (rule.freq == Freq.YEARLY) parts += "BYMONTH=${start.monthValue}"
      parts += "BYDAY=${nth.n}${IcsText.dayCode(nth.day)}"
    }

    rule.until?.let { parts += "UNTIL=" + untilText(it, until) }
    rule.count?.let { parts += "COUNT=$it" }
    return parts.joinToString(";")
  }

  fun parse(text: String, start: LocalDate, zone: ZoneId): Recurrence? {
    val fields = fieldsOf(text) ?: return null
    if (fields.keys.any { it !in supportedKeys }) return null

    val freq = fields["FREQ"]
      ?.let { name -> Freq.entries.firstOrNull { it.name == name } }
      ?: return null
    val interval = fields["INTERVAL"]?.toIntOrNull() ?: 1
    if (fields.containsKey("INTERVAL") && interval < 1) return null

    val count = fields["COUNT"]?.let { it.toIntOrNull() ?: return null }
    val until = fields["UNTIL"]?.let { parseUntil(it, zone) ?: return null }
    val base = Recurrence(freq, interval, until = until, count = count)
    if (!monthFieldsMatch(fields, freq, start)) return null

    val days = fields["BYDAY"]?.split(',')?.map { token ->
      val match = dayToken.matchEntire(token.trim()) ?: return null
      val ordinal = match.groupValues[1].toIntOrNull()
      val day = IcsText.dayOf(match.groupValues[2]) ?: return null
      ordinal to day
    } ?: return base

    return withDays(base, days, fields.containsKey("BYMONTH"))
  }

  private fun fieldsOf(text: String): Map<String, String>? {
    val fields = mutableMapOf<String, String>()
    for (part in text.trim().split(';').filter { it.isNotBlank() }) {
      val pair = part.split('=', limit = 2)
      if (pair.size != 2) return null
      fields[pair[0].trim().uppercase()] = pair[1].trim().uppercase()
    }
    return fields
  }

  /** BYMONTHDAY / BYMONTH may only restate what the start date implies. */
  private fun monthFieldsMatch(
    fields: Map<String, String>,
    freq: Freq,
    start: LocalDate
  ): Boolean {
    val monthDay = fields["BYMONTHDAY"]
    if (monthDay != null) {
      val day = monthDay.toIntOrNull() ?: return false
      val periodic = freq == Freq.MONTHLY || freq == Freq.YEARLY
      if (!periodic || day != start.dayOfMonth) return false
    }

    val month = fields["BYMONTH"] ?: return true
    return freq == Freq.YEARLY && month.toIntOrNull() == start.monthValue
  }

  private fun withDays(
    base: Recurrence,
    days: List<Pair<Int?, DayOfWeek>>,
    hasMonth: Boolean
  ): Recurrence? {
    val plain = days.all { it.first == null }
    val single = days.singleOrNull()
    val ordinal = single?.first

    return when (base.freq) {
      Freq.DAILY -> {
        if (!plain || base.interval != 1) return null
        base.copy(freq = Freq.WEEKLY, byDay = days.map { it.second }.toSet())
      }
      Freq.WEEKLY -> {
        if (!plain) return null
        base.copy(byDay = days.map { it.second }.toSet())
      }
      Freq.MONTHLY -> {
        if (ordinal == null || ordinal == 0 || ordinal !in -5..5) return null
        base.copy(nth = NthWeekday(ordinal, single.second))
      }
      Freq.YEARLY -> {
        if (!hasMonth || ordinal == null) return null
        if (ordinal == 0 || ordinal !in -5..5) return null
        base.copy(nth = NthWeekday(ordinal, single.second))
      }
    }
  }

  /** Zoned and floating forms use the last second of the until day. */
  private fun untilText(until: LocalDate, form: UntilForm): String {
    val endOfDay = until.atTime(LocalTime.of(23, 59, 59))
    return when (form) {
      UntilForm.Date -> IcsText.date(until)
      UntilForm.Floating -> IcsText.dateTime(endOfDay)
      is UntilForm.Utc -> {
        val instant = endOfDay.atZone(form.zone)
        val utc = instant.withZoneSameInstant(ZoneOffset.UTC)
        IcsText.utc(utc.toLocalDateTime())
      }
    }
  }

  private fun parseUntil(text: String, zone: ZoneId): LocalDate? {
    if (text.length == 8) return IcsText.parseDate(text)
    if (!IcsText.isUtc(text)) return IcsText.parseDateTime(text)?.toLocalDate()
    return IcsText.utcInstant(text)?.atZone(zone)?.toLocalDate()
  }
}
