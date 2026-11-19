package app.monthly.logic

import app.monthly.model.Event
import app.monthly.model.Freq
import app.monthly.model.NthWeekday
import app.monthly.model.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Occurrence start dates of this event inside [from]..[to], in the event's
 * own calendar. The start date is always the first occurrence. Dates that
 * do not exist (the 31st, Feb 29) are skipped, except on birthdays, which
 * fall back to the month's last day.
 */
fun Event.datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
  val rule = recurrence
  if (rule == null) {
    val hit = startDate in from..to && startDate !in exceptions
    return if (hit) listOf(startDate) else emptyList()
  }

  val last = rule.until?.let { minOf(it, to) } ?: to

  // COUNT is counted from the first occurrence, so no skipping ahead then.
  val skipTo = if (rule.count == null) from else startDate
  val found = mutableListOf<LocalDate>()
  var produced = 0
  for (date in candidates(rule, startDate, skipTo, last, isBirthday)) {
    if (rule.count != null && produced >= rule.count) break
    produced++
    if (date >= from && date !in exceptions) found += date
  }
  return found
}

private fun candidates(
  rule: Recurrence,
  start: LocalDate,
  skipTo: LocalDate,
  last: LocalDate,
  clamp: Boolean
): Sequence<LocalDate> = sequence {
  if (start > last) return@sequence
  yield(start)

  val following = when (rule.freq) {
    Freq.DAILY -> daily(rule, start, skipTo)
    Freq.WEEKLY -> weekly(rule, start, skipTo)
    Freq.MONTHLY -> monthly(rule, start, skipTo, clamp)
    Freq.YEARLY -> yearly(rule, start, skipTo, clamp)
  }
  yieldAll(following.filter { it > start }.takeWhile { it <= last })
}

private fun daily(rule: Recurrence, start: LocalDate, skipTo: LocalDate) =
  periods(ChronoUnit.DAYS.between(start, skipTo), rule.interval)
    .map { start.plusDays(it) }

/** Week blocks start on Monday, the RFC 5545 default WKST. */
private fun weekly(
  rule: Recurrence,
  start: LocalDate,
  skipTo: LocalDate
): Sequence<LocalDate> {
  val anchor = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  val days = rule.byDay.ifEmpty { setOf(start.dayOfWeek) }.sorted()
  return periods(ChronoUnit.WEEKS.between(anchor, skipTo), rule.interval)
    .flatMap { weeks ->
      val base = anchor.plusWeeks(weeks)
      days.asSequence().map { base.plusDays(it.value - 1L) }
    }
}

private fun monthly(
  rule: Recurrence,
  start: LocalDate,
  skipTo: LocalDate,
  clamp: Boolean
): Sequence<LocalDate> {
  val first = YearMonth.from(start)
  val gap = ChronoUnit.MONTHS.between(first, YearMonth.from(skipTo))
  return periods(gap, rule.interval).mapNotNull { months ->
    dayIn(first.plusMonths(months), rule.nth, start.dayOfMonth, clamp)
  }
}

private fun yearly(
  rule: Recurrence,
  start: LocalDate,
  skipTo: LocalDate,
  clamp: Boolean
): Sequence<LocalDate> {
  val first = YearMonth.from(start)
  val gap = (skipTo.year - start.year).toLong()
  return periods(gap, rule.interval).mapNotNull { years ->
    dayIn(first.plusYears(years), rule.nth, start.dayOfMonth, clamp)
  }
}

/** Offsets 0, n, 2n, ... starting at the last period not after [gap]. */
private fun periods(gap: Long, interval: Int): Sequence<Long> {
  val first = gap.coerceAtLeast(0) / interval
  return generateSequence(first) { it + 1 }.map { it * interval }
}

private fun dayIn(
  month: YearMonth,
  nth: NthWeekday?,
  dayOfMonth: Int,
  clamp: Boolean
): LocalDate? {
  if (nth != null) return nthWeekday(month, nth)
  if (dayOfMonth <= month.lengthOfMonth()) return month.atDay(dayOfMonth)
  return if (clamp) month.atEndOfMonth() else null
}

internal fun nthWeekday(month: YearMonth, nth: NthWeekday): LocalDate? {
  val date = if (nth.n > 0) {
    month.atDay(1)
      .with(TemporalAdjusters.nextOrSame(nth.day))
      .plusWeeks(nth.n - 1L)
  } else {
    month.atEndOfMonth()
      .with(TemporalAdjusters.previousOrSame(nth.day))
      .minusWeeks(-nth.n - 1L)
  }
  return date.takeIf { YearMonth.from(it) == month }
}
