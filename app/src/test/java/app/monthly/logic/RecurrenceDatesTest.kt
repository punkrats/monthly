package app.monthly.logic

import app.monthly.allDay
import app.monthly.birthday
import app.monthly.date
import app.monthly.model.Freq
import app.monthly.model.NthWeekday
import app.monthly.model.Recurrence
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceDatesTest {

  private fun dates(vararg text: String) = text.map { date(it) }

  @Test
  fun singleEventYieldsItsStartInsideTheRange() {
    val event = allDay("2026-11-18")

    assertEquals(
      dates("2026-11-18"),
      event.datesBetween(date("2026-11-01"), date("2026-11-30"))
    )
    assertEquals(
      emptyList<Any>(),
      event.datesBetween(date("2026-12-01"), date("2026-12-31"))
    )
  }

  @Test
  fun dailyWithInterval() {
    val event = allDay("2026-01-01", rule = Recurrence(Freq.DAILY, 3))

    val result = event.datesBetween(date("2026-01-01"), date("2026-01-10"))

    assertEquals(
      dates("2026-01-01", "2026-01-04", "2026-01-07", "2026-01-10"),
      result
    )
  }

  @Test
  fun dailySkipAheadMatchesBruteForce() {
    val start = date("2020-01-01")
    val event = allDay("2020-01-01", rule = Recurrence(Freq.DAILY, 3))
    val from = date("2026-01-01")
    val to = date("2026-01-12")

    val expected = generateSequence(from) { it.plusDays(1) }
      .takeWhile { it <= to }
      .filter { ChronoUnit.DAYS.between(start, it) % 3 == 0L }
      .toList()

    assertEquals(expected, event.datesBetween(from, to))
  }

  @Test
  fun weeklyOnSeveralDays() {
    val rule = Recurrence(
      Freq.WEEKLY, byDay = setOf(MONDAY, WEDNESDAY, FRIDAY)
    )
    val event = allDay("2026-11-18", rule = rule)

    val result = event.datesBetween(date("2026-11-01"), date("2026-11-30"))

    assertEquals(
      dates(
        "2026-11-18", "2026-11-20", "2026-11-23", "2026-11-25",
        "2026-11-27", "2026-11-30"
      ),
      result
    )
  }

  @Test
  fun everyOtherWeekOnTuesdayAndThursday() {
    val rule = Recurrence(
      Freq.WEEKLY, interval = 2, byDay = setOf(TUESDAY, THURSDAY)
    )
    val event = allDay("2026-11-17", rule = rule)

    val result = event.datesBetween(date("2026-11-01"), date("2026-12-06"))

    assertEquals(
      dates("2026-11-17", "2026-11-19", "2026-12-01", "2026-12-03"),
      result
    )
  }

  @Test
  fun weeklySkipAheadKeepsTheIntervalPhase() {
    val rule = Recurrence(Freq.WEEKLY, interval = 2)
    val event = allDay("2025-01-06", rule = rule)

    val result = event.datesBetween(date("2026-11-01"), date("2026-11-30"))

    assertEquals(dates("2026-11-09", "2026-11-23"), result)
  }

  @Test
  fun monthlyOnThe31stSkipsShortMonths() {
    val event = allDay("2026-01-31", rule = Recurrence(Freq.MONTHLY))

    val result = event.datesBetween(date("2026-01-01"), date("2026-05-31"))

    assertEquals(dates("2026-01-31", "2026-03-31", "2026-05-31"), result)
  }

  @Test
  fun monthlyOnTheSecondTuesday() {
    val rule = Recurrence(Freq.MONTHLY, nth = NthWeekday(2, TUESDAY))
    val event = allDay("2026-01-13", rule = rule)

    val result = event.datesBetween(date("2026-01-01"), date("2026-03-31"))

    assertEquals(dates("2026-01-13", "2026-02-10", "2026-03-10"), result)
  }

  @Test
  fun monthlyOnTheLastFriday() {
    val rule = Recurrence(Freq.MONTHLY, nth = NthWeekday(-1, FRIDAY))
    val event = allDay("2026-01-30", rule = rule)

    val result = event.datesBetween(date("2026-01-01"), date("2026-03-31"))

    assertEquals(dates("2026-01-30", "2026-02-27", "2026-03-27"), result)
  }

  @Test
  fun yearlyOnTheSecondSundayOfMay() {
    val rule = Recurrence(Freq.YEARLY, nth = NthWeekday(2, SUNDAY))
    val event = allDay("2026-05-10", rule = rule)

    val result = event.datesBetween(date("2026-01-01"), date("2027-12-31"))

    assertEquals(dates("2026-05-10", "2027-05-09"), result)
  }

  @Test
  fun yearlyLeapDayEventSkipsCommonYears() {
    val event = allDay("2024-02-29", rule = Recurrence(Freq.YEARLY))

    val result = event.datesBetween(date("2024-01-01"), date("2028-12-31"))

    assertEquals(dates("2024-02-29", "2028-02-29"), result)
  }

  @Test
  fun leapDayBirthdayFallsBackToTheLastDayOfFebruary() {
    val event = birthday("2024-02-29", zone = null)

    val result = event.datesBetween(date("2025-01-01"), date("2026-12-31"))

    assertEquals(dates("2025-02-28", "2026-02-28"), result)
  }

  @Test
  fun untilIsInclusive() {
    val rule = Recurrence(Freq.DAILY, until = date("2026-01-03"))
    val event = allDay("2026-01-01", rule = rule)

    val result = event.datesBetween(date("2026-01-01"), date("2026-01-31"))

    assertEquals(dates("2026-01-01", "2026-01-02", "2026-01-03"), result)
  }

  @Test
  fun countIncludesTheStartAndExcludedDates() {
    val rule = Recurrence(Freq.DAILY, count = 4)
    val event = allDay("2026-01-01", rule = rule)
      .copy(exceptions = setOf(date("2026-01-02")))

    val result = event.datesBetween(date("2026-01-01"), date("2026-01-31"))

    assertEquals(dates("2026-01-01", "2026-01-03", "2026-01-04"), result)
  }

  @Test
  fun countAppliesWhenTheRangeStartsLater() {
    val rule = Recurrence(Freq.WEEKLY, count = 3)
    val event = allDay("2026-01-05", rule = rule)

    val result = event.datesBetween(date("2026-01-10"), date("2026-12-31"))

    assertEquals(dates("2026-01-12", "2026-01-19"), result)
  }
}
