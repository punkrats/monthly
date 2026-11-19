package app.monthly.logic

import app.monthly.BERLIN
import app.monthly.LOS_ANGELES
import app.monthly.NEW_YORK
import app.monthly.TOKYO
import app.monthly.birthday
import app.monthly.date
import app.monthly.model.Freq
import app.monthly.model.Recurrence
import app.monthly.model.Reminder
import app.monthly.time
import app.monthly.timed
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertsTest {

  private val theirMorning = Reminder.OnDay(0, time("09:00"), true)
  private val myEveningBefore = Reminder.OnDay(1, time("18:00"), false)

  @Test
  fun birthdayReminderFiresOnTheFriendsClock() {
    val event = birthday("1990-03-05", TOKYO, listOf(theirMorning))

    val at = Alerts.triggerAt(
      event, date("2027-03-05"), theirMorning, LOS_ANGELES
    )

    assertEquals(Instant.parse("2027-03-05T00:00:00Z"), at)
  }

  @Test
  fun birthdayReminderCanFireOnMyClock() {
    val event = birthday("1990-03-05", TOKYO, listOf(myEveningBefore))

    val at = Alerts.triggerAt(
      event, date("2027-03-05"), myEveningBefore, LOS_ANGELES
    )

    assertEquals(Instant.parse("2027-03-05T02:00:00Z"), at)
  }

  @Test
  fun timedReminderIsAnOffsetFromTheStartInstant() {
    val reminder = Reminder.Before(15)
    val event = timed(
      "2026-11-18", "10:00", "11:00", NEW_YORK, reminders = listOf(reminder)
    )

    val at = Alerts.triggerAt(event, date("2026-11-18"), reminder, BERLIN)

    assertEquals(Instant.parse("2026-11-18T14:45:00Z"), at)
  }

  @Test
  fun betweenIsExclusiveAtTheStartAndInclusiveAtTheEnd() {
    val reminder = Reminder.Before(0)
    val rule = Recurrence(Freq.DAILY)
    val event = timed(
      "2026-11-01", "12:00", "13:00", java.time.ZoneOffset.UTC, rule,
      listOf(reminder)
    )

    val alerts = Alerts.between(
      listOf(event),
      Instant.parse("2026-11-02T12:00:00Z"),
      Instant.parse("2026-11-04T12:00:00Z"),
      BERLIN
    )

    assertEquals(
      listOf(
        Instant.parse("2026-11-03T12:00:00Z"),
        Instant.parse("2026-11-04T12:00:00Z")
      ),
      alerts.map { it.at }
    )
  }

  @Test
  fun alertForTomorrowsEventCanFireToday() {
    val event = birthday("1990-11-19", BERLIN, listOf(myEveningBefore))

    val alerts = Alerts.between(
      listOf(event),
      Instant.parse("2026-11-18T00:00:00Z"),
      Instant.parse("2026-11-18T23:00:00Z"),
      BERLIN
    )

    assertEquals(date("2026-11-19"), alerts.single().date)
  }

  @Test
  fun nextFindsABirthdayMonthsAhead() {
    val event = birthday("1990-09-01", TOKYO, listOf(theirMorning))

    val next = Alerts.next(
      listOf(event), Instant.parse("2026-11-18T10:00:00Z"), BERLIN
    )

    assertEquals(Instant.parse("2027-09-01T00:00:00Z"), next)
  }

  @Test
  fun nextIsNullWithoutReminders() {
    val event = birthday("1990-09-01", TOKYO)

    val next = Alerts.next(
      listOf(event), Instant.parse("2026-11-18T10:00:00Z"), BERLIN
    )

    assertNull(next)
  }
}
