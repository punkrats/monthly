package app.monthly.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** "2nd Tuesday" is NthWeekday(2, TUESDAY), "last Friday" is (-1, FRIDAY). */
data class NthWeekday(val n: Int, val day: DayOfWeek)

/**
 * The supported subset of an RFC 5545 RRULE. Monthly and yearly rules
 * repeat on the start's day of month unless [nth] is set. [until] is the
 * last allowed occurrence date, inclusive.
 */
data class Recurrence(
  val freq: Freq,
  val interval: Int = 1,
  val byDay: Set<DayOfWeek> = emptySet(),
  val nth: NthWeekday? = null,
  val until: LocalDate? = null,
  val count: Int? = null
)
