package app.monthly.ics

import app.monthly.BERLIN
import app.monthly.NEW_YORK
import app.monthly.date
import app.monthly.model.Freq
import app.monthly.model.NthWeekday
import app.monthly.model.Recurrence
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RRuleTest {

  private val start = date("2026-11-18")

  @Test
  fun formatsWeeklyRuleWithDaysAndUntil() {
    val rule = Recurrence(
      Freq.WEEKLY, interval = 2, byDay = setOf(WEDNESDAY, MONDAY),
      until = date("2026-12-31")
    )

    val text = RRule.format(rule, start)

    assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20261231", text)
  }

  @Test
  fun formatsNthWeekdayAndCount() {
    val rule = Recurrence(
      Freq.MONTHLY, nth = NthWeekday(-1, FRIDAY), count = 6
    )

    assertEquals("FREQ=MONTHLY;BYDAY=-1FR;COUNT=6", RRule.format(rule, start))
  }

  @Test
  fun formatsYearlyNthWeekdayWithItsMonth() {
    val rule = Recurrence(Freq.YEARLY, nth = NthWeekday(2, SUNDAY))

    val text = RRule.format(rule, date("2026-05-10"))

    assertEquals("FREQ=YEARLY;BYMONTH=5;BYDAY=2SU", text)
  }

  @Test
  fun formatsUntilAsUtcEndOfDayForZonedEvents() {
    val rule = Recurrence(Freq.DAILY, until = date("2026-12-31"))

    val text = RRule.format(rule, start, UntilForm.Utc(BERLIN))

    assertEquals("FREQ=DAILY;UNTIL=20261231T225959Z", text)
  }

  @Test
  fun roundTripsEveryField() {
    val rules = listOf(
      Recurrence(Freq.DAILY, interval = 3),
      Recurrence(Freq.WEEKLY, byDay = setOf(MONDAY, FRIDAY), count = 10),
      Recurrence(Freq.MONTHLY, nth = NthWeekday(2, TUESDAY)),
      Recurrence(Freq.YEARLY, nth = NthWeekday(-1, WEDNESDAY)),
      Recurrence(Freq.YEARLY, until = date("2030-01-01"))
    )

    rules.forEach { rule ->
      val parsed = RRule.parse(RRule.format(rule, start), start, BERLIN)
      assertEquals(rule, parsed)
    }
  }

  @Test
  fun parsesUtcUntilIntoTheEventZoneDate() {
    val text = "FREQ=DAILY;UNTIL=20270101T035959Z"

    val berlin = RRule.parse(text, start, BERLIN)
    val newYork = RRule.parse(text, start, NEW_YORK)

    assertEquals(date("2027-01-01"), berlin?.until)
    assertEquals(date("2026-12-31"), newYork?.until)
  }

  @Test
  fun turnsDailyWorkdaysIntoWeeklyDays() {
    val text = "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR"

    val rule = RRule.parse(text, start, BERLIN)

    assertEquals(Freq.WEEKLY, rule?.freq)
    assertEquals(5, rule?.byDay?.size)
  }

  @Test
  fun acceptsYearlyNthWeekdayInTheStartMonth() {
    val text = "FREQ=YEARLY;BYMONTH=5;BYDAY=2SU"

    val rule = RRule.parse(text, date("2026-05-10"), BERLIN)

    assertEquals(NthWeekday(2, SUNDAY), rule?.nth)
  }

  @Test
  fun acceptsMonthDayMatchingTheStart() {
    val rule = RRule.parse("FREQ=MONTHLY;BYMONTHDAY=18", start, BERLIN)

    assertEquals(Recurrence(Freq.MONTHLY), rule)
  }

  @Test
  fun rejectsWhatTheModelCannotExpress() {
    val unsupported = listOf(
      "FREQ=HOURLY",
      "FREQ=MONTHLY;BYDAY=MO;BYSETPOS=1",
      "FREQ=MONTHLY;BYMONTHDAY=1,15",
      "FREQ=MONTHLY;BYMONTHDAY=3",
      "FREQ=MONTHLY;BYMONTH=11",
      "FREQ=YEARLY;BYMONTH=1,7",
      "FREQ=YEARLY;BYDAY=20MO",
      "FREQ=WEEKLY;BYDAY=1MO",
      "FREQ=DAILY;INTERVAL=0",
      "INTERVAL=2"
    )

    unsupported.forEach { text ->
      assertNull(text, RRule.parse(text, start, BERLIN))
    }
  }
}
