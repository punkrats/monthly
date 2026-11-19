package app.monthly.logic

import app.monthly.BERLIN
import app.monthly.NEW_YORK
import app.monthly.allDay
import app.monthly.date
import app.monthly.model.Freq
import app.monthly.model.Recurrence
import app.monthly.time
import app.monthly.timed
import org.junit.Assert.assertEquals
import org.junit.Test

class OccurrencesTest {

  @Test
  fun lateEveningInNewYorkLandsOnTheNextDayInBerlin() {
    val event = timed("2026-11-18", "23:00", "23:30", NEW_YORK)

    val found = Occurrences.between(
      listOf(event), date("2026-11-01"), date("2026-11-30"), BERLIN
    )

    val occurrence = found.single()
    assertEquals(date("2026-11-19"), occurrence.firstDay)
    assertEquals(time("05:00"), occurrence.start?.toLocalTime())
    assertEquals(date("2026-11-18"), occurrence.date)
  }

  @Test
  fun weeklyMeetingKeepsItsLocalTimeAcrossDaylightSaving() {
    val rule = Recurrence(Freq.WEEKLY)
    val event = timed("2026-10-21", "10:00", "11:00", NEW_YORK, rule)

    val found = Occurrences.between(
      listOf(event), date("2026-10-21"), date("2026-11-04"), BERLIN
    )

    val starts = found.map { it.start?.toLocalTime() }
    assertEquals(listOf(time("16:00"), time("15:00"), time("16:00")), starts)
  }

  @Test
  fun multiDayAllDayEventCoversEveryDay() {
    val event = allDay("2026-11-18", "2026-11-20")

    val byDay = Occurrences.byDay(
      listOf(event), date("2026-11-01"), date("2026-11-30"), BERLIN
    )

    assertEquals(
      setOf(date("2026-11-18"), date("2026-11-19"), date("2026-11-20")),
      byDay.keys
    )
  }

  @Test
  fun eventEndingAtMidnightStaysOnItsDay() {
    val event = timed(
      "2026-11-18", "22:00", "00:00", BERLIN, endDate = "2026-11-19"
    )

    val byDay = Occurrences.byDay(
      listOf(event), date("2026-11-01"), date("2026-11-30"), BERLIN
    )

    assertEquals(setOf(date("2026-11-18")), byDay.keys)
  }

  @Test
  fun eventStartedBeforeTheRangeStillShowsInside() {
    val event = allDay("2026-10-30", "2026-11-02")

    val found = Occurrences.between(
      listOf(event), date("2026-11-01"), date("2026-11-30"), BERLIN
    )

    assertEquals(1, found.size)
  }

  @Test
  fun floatingEventFollowsTheDeviceZone() {
    val event = timed("2026-11-18", "09:00", "10:00", zone = null)

    val inBerlin = Occurrences.between(
      listOf(event), date("2026-11-18"), date("2026-11-18"), BERLIN
    )
    val inNewYork = Occurrences.between(
      listOf(event), date("2026-11-18"), date("2026-11-18"), NEW_YORK
    )

    assertEquals(time("09:00"), inBerlin.single().start?.toLocalTime())
    assertEquals(time("09:00"), inNewYork.single().start?.toLocalTime())
  }

  @Test
  fun sortsAllDayFirstThenByStart() {
    val events = listOf(
      timed("2026-11-18", "14:00", "15:00", BERLIN, title = "Late"),
      timed("2026-11-18", "08:00", "09:00", BERLIN, title = "Early"),
      allDay("2026-11-18", title = "Whole day")
    )

    val found = Occurrences.between(
      events, date("2026-11-18"), date("2026-11-18"), BERLIN
    )

    assertEquals(
      listOf("Whole day", "Early", "Late"),
      found.map { it.event.title }
    )
  }
}
