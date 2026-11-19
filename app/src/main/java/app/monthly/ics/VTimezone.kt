package app.monthly.ics

import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransitionRule

/**
 * VTIMEZONE components built from the JDK's current rules, so strict
 * consumers can resolve the TZIDs the writer emits.
 */
internal object VTimezone {

  fun lines(zone: ZoneId, now: Instant): List<String> = buildList {
    add("BEGIN:VTIMEZONE")
    add("TZID:${zone.id}")
    add("X-LIC-LOCATION:${zone.id}")

    val rules = zone.rules.transitionRules
    if (rules.isEmpty()) {
      val offset = offset(zone.rules.getOffset(now))
      add("BEGIN:STANDARD")
      add("TZOFFSETFROM:$offset")
      add("TZOFFSETTO:$offset")
      add("DTSTART:19700101T000000")
      add("END:STANDARD")
    }

    rules.sortedBy { it.month }.forEach { addAll(observance(it)) }
    add("END:VTIMEZONE")
  }

  private fun observance(rule: ZoneOffsetTransitionRule): List<String> {
    val daylight = rule.offsetAfter.totalSeconds >
      rule.standardOffset.totalSeconds
    val kind = if (daylight) "DAYLIGHT" else "STANDARD"
    val onset = rule.createTransition(1970).dateTimeBefore
    return listOf(
      "BEGIN:$kind",
      "TZOFFSETFROM:${offset(rule.offsetBefore)}",
      "TZOFFSETTO:${offset(rule.offsetAfter)}",
      "DTSTART:${IcsText.dateTime(onset)}",
      "RRULE:FREQ=YEARLY;BYMONTH=${rule.month.value};${days(rule)}",
      "END:$kind"
    )
  }

  /**
   * The JDK states a rule as "weekday on or after day d" (d > 0) or "on or
   * before the d-th last day" (d < 0); RRULE prefers ordinals where exact.
   */
  private fun days(rule: ZoneOffsetTransitionRule): String {
    val indicator = rule.dayOfMonthIndicator
    val weekday = rule.dayOfWeek ?: return "BYMONTHDAY=$indicator"
    val code = IcsText.dayCode(weekday)
    val lastWeek = rule.month != Month.FEBRUARY &&
      indicator == rule.month.maxLength() - 6

    if (indicator == -1 || lastWeek) return "BYDAY=-1$code"
    if (indicator > 0 && (indicator - 1) % 7 == 0) {
      return "BYDAY=${(indicator - 1) / 7 + 1}$code"
    }

    val window = if (indicator > 0) {
      indicator..indicator + 6
    } else {
      indicator - 6..indicator
    }
    return "BYDAY=$code;BYMONTHDAY=${window.joinToString(",")}"
  }

  private fun offset(offset: ZoneOffset): String {
    val total = offset.totalSeconds
    val sign = if (total < 0) "-" else "+"
    val abs = Math.abs(total)
    val hours = abs / 3600
    val minutes = abs % 3600 / 60
    val seconds = abs % 60
    val base = sign + pad(hours) + pad(minutes)
    return if (seconds == 0) base else base + pad(seconds)
  }

  private fun pad(value: Int) = value.toString().padStart(2, '0')
}
