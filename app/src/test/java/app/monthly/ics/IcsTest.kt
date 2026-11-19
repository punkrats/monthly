package app.monthly.ics

import app.monthly.BERLIN
import app.monthly.LOS_ANGELES
import app.monthly.NEW_YORK
import app.monthly.TOKYO
import app.monthly.allDay
import app.monthly.birthday
import app.monthly.date
import app.monthly.model.Event
import app.monthly.model.EventColor
import app.monthly.model.EventKind
import app.monthly.model.Freq
import app.monthly.model.Recurrence
import app.monthly.model.Reminder
import app.monthly.time
import app.monthly.timed
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsTest {

  private val stamp = Instant.parse("2026-11-18T12:00:00Z")
  private val utc = ZoneId.of("UTC")
  private val theirMorning = Reminder.OnDay(0, time("09:00"), true)
  private val myEveningBefore = Reminder.OnDay(1, time("18:00"), false)

  private fun write(vararg events: Event) =
    IcsWriter.write(events.toList(), emptyList(), "Cycle start", stamp)

  @Test
  fun roundTripsEveryKindOfEvent() {
    val weekly = Recurrence(
      Freq.WEEKLY, byDay = setOf(WEDNESDAY, FRIDAY), until = date("2027-03-01")
    )
    val events = listOf(
      birthday(
        "1990-03-05", TOKYO, listOf(theirMorning, myEveningBefore), 1990
      ).copy(uid = "b1", color = EventColor.PINK, notes = "Likes tea"),
      timed(
        "2026-11-18", "10:00", "11:30", NEW_YORK, weekly,
        listOf(Reminder.Before(15), Reminder.Before(1440))
      ).copy(
        uid = "t1", location = "Room 4; floor 2",
        exceptions = setOf(date("2026-11-20"))
      ),
      allDay("2026-12-24", "2026-12-26", title = "Holidays")
        .copy(uid = "a1", exceptions = emptySet()),
      timed("2026-11-19", "09:00", "10:00", zone = null).copy(uid = "f1"),
      timed(
        "2026-11-19", "23:00", "01:00", utc,
        Recurrence(Freq.DAILY, count = 5), endDate = "2026-11-20"
      ).copy(uid = "u1", exceptions = setOf(date("2026-11-21")))
    )
    val cycle = listOf(date("2026-10-01"), date("2026-10-29"))

    val text = IcsWriter.write(events, cycle, "Cycle start", stamp)
    val back = IcsReader.read(text, BERLIN)

    assertEquals(events, back.events)
    assertEquals(cycle, back.cycleStarts)
    assertEquals(0, back.simplified)
    assertEquals(0, back.unknownZones)
  }

  @Test
  fun writesBirthdayAsYearlyAllDayWithFriendsZone() {
    val reminders = listOf(theirMorning, myEveningBefore)
    val event = birthday("1990-03-05", TOKYO, reminders).copy(uid = "b1")

    val lines = write(event).split("\r\n")

    listOf(
      "DTSTART;VALUE=DATE:19900305",
      "DTEND;VALUE=DATE:19900306",
      "RRULE:FREQ=YEARLY",
      "CATEGORIES:BIRTHDAY",
      "X-MONTHLY-KIND:BIRTHDAY",
      "X-MONTHLY-ZONE:Asia/Tokyo",
      "TRIGGER:PT9H",
      "TRIGGER:-PT6H",
      "X-MONTHLY-CLOCK:THEIRS",
      "X-MONTHLY-CLOCK:MINE"
    ).forEach { assertTrue(it, it in lines) }
  }

  @Test
  fun writesTimeZoneDefinitionsForZonedEvents() {
    val berlin = timed("2026-11-18", "10:00", "11:00", BERLIN).copy(uid = "x")
    val newYork = timed("2026-11-18", "10:00", "11:00", NEW_YORK)
      .copy(uid = "y")
    val tokyo = timed("2026-11-18", "10:00", "11:00", TOKYO).copy(uid = "z")

    val text = write(berlin, newYork, tokyo)

    val expected = listOf(
      block(
        "DAYLIGHT", "+0100", "+0200", "19700329T020000",
        "FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU"
      ),
      block(
        "STANDARD", "+0200", "+0100", "19701025T030000",
        "FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU"
      ),
      block(
        "DAYLIGHT", "-0500", "-0400", "19700308T020000",
        "FREQ=YEARLY;BYMONTH=3;BYDAY=2SU"
      ),
      block(
        "STANDARD", "-0400", "-0500", "19701101T020000",
        "FREQ=YEARLY;BYMONTH=11;BYDAY=1SU"
      ),
      "BEGIN:STANDARD\r\nTZOFFSETFROM:+0900\r\nTZOFFSETTO:+0900\r\n" +
        "DTSTART:19700101T000000\r\nEND:STANDARD",
      "DTSTART;TZID=Europe/Berlin:20261118T100000"
    )
    expected.forEach { assertTrue(it, it in text) }
  }

  private fun block(
    kind: String,
    from: String,
    to: String,
    start: String,
    rule: String
  ) = "BEGIN:$kind\r\nTZOFFSETFROM:$from\r\nTZOFFSETTO:$to\r\n" +
    "DTSTART:$start\r\nRRULE:$rule\r\nEND:$kind"

  @Test
  fun foldsLongLinesAndKeepsSpecialCharacters() {
    val title = "Party, food; drinks \\ fun\nsecond line " + "🎂".repeat(40)
    val event = allDay("2026-11-18", title = title).copy(uid = "long")

    val text = write(event)
    val back = IcsReader.read(text, BERLIN)

    val longest = text.split("\r\n").maxOf { it.toByteArray().size }
    assertTrue("longest line $longest", longest <= 75)
    assertEquals(title, back.events.single().title)
  }

  @Test
  fun leavesCycleDataOutUnlessGiven() {
    val text = write(allDay("2026-11-18").copy(uid = "a"))

    assertTrue("CYCLE" !in text)
  }

  @Test
  fun readsAForeignCalendar() {
    val result = IcsReader.read(FOREIGN, LOS_ANGELES)
    val byUid = result.events.associateBy { it.uid }

    assertEquals(8, result.events.size)
    assertEquals(1, result.simplified)
    assertEquals(1, result.unknownZones)

    val weekly = byUid.getValue("weekly@example.com")
    assertEquals("Team call, weekly", weekly.title)
    assertEquals(
      "Line one\nLine two that is long enough to be folded across two lines",
      weekly.notes
    )
    assertEquals(NEW_YORK, weekly.zone)
    assertEquals(time("11:00"), weekly.endTime)
    assertEquals(setOf(WEDNESDAY), weekly.recurrence?.byDay)
    val skipped = setOf(date("2026-11-25"), date("2026-12-02"))
    assertEquals(skipped, weekly.exceptions)
    assertEquals(listOf(Reminder.Before(30)), weekly.reminders)

    val moved = result.events.single { it.title == "Team call (moved)" }
    assertEquals(time("12:00"), moved.startTime)
    assertEquals(null, moved.recurrence)

    val holidays = byUid.getValue("holiday@example.com")
    assertTrue(holidays.allDay)
    assertEquals(date("2026-12-26"), holidays.endDate)
    assertEquals(
      listOf(Reminder.OnDay(1, time("18:00"), true)), holidays.reminders
    )

    val inUtc = byUid.getValue("utc@example.com")
    assertEquals(utc, inUtc.zone)
    assertEquals(time("10:30"), inUtc.endTime)

    assertEquals(BERLIN, byUid.getValue("windows@example.com").zone)
    assertEquals(LOS_ANGELES, byUid.getValue("mars@example.com").zone)
    assertEquals(null, byUid.getValue("setpos@example.com").recurrence)

    val party = byUid.getValue("bday@example.com")
    assertEquals(EventKind.BIRTHDAY, party.kind)
    assertEquals(Freq.YEARLY, party.recurrence?.freq)
    assertEquals(EventColor.PINK, party.color)
  }

  private companion object {
    val FOREIGN = """
      BEGIN:VCALENDAR
      PRODID:-//Example//Calendar//EN
      VERSION:2.0
      BEGIN:VTIMEZONE
      TZID:W. Europe Standard Time
      X-LIC-LOCATION:Europe/Berlin
      END:VTIMEZONE
      BEGIN:VEVENT
      DTSTART;TZID=America/New_York:20261118T100000
      DTEND;TZID=America/New_York:20261118T110000
      RRULE:FREQ=WEEKLY;BYDAY=WE
      EXDATE;TZID=America/New_York:20261125T100000
      UID:weekly@example.com
      SUMMARY:Team call\, weekly
      DESCRIPTION:Line one\nLine two that is long enough to be folded across
        two lines
      BEGIN:VALARM
      ACTION:DISPLAY
      TRIGGER:-PT30M
      END:VALARM
      END:VEVENT
      BEGIN:VEVENT
      DTSTART;TZID=America/New_York:20261202T120000
      DTEND;TZID=America/New_York:20261202T130000
      RECURRENCE-ID;TZID=America/New_York:20261202T100000
      UID:weekly@example.com
      SUMMARY:Team call (moved)
      END:VEVENT
      BEGIN:VEVENT
      DTSTART;VALUE=DATE:20261224
      DTEND;VALUE=DATE:20261227
      UID:holiday@example.com
      SUMMARY:Holidays
      BEGIN:VALARM
      TRIGGER;RELATED=START:-PT6H
      END:VALARM
      BEGIN:VALARM
      TRIGGER;VALUE=DATE-TIME:20261223T170000Z
      END:VALARM
      END:VEVENT
      BEGIN:VEVENT
      DTSTART:20261120T090000Z
      DURATION:PT90M
      UID:utc@example.com
      SUMMARY:UTC meeting
      END:VEVENT
      BEGIN:VEVENT
      DTSTART;TZID=W. Europe Standard Time:20261121T080000
      DTEND;TZID=W. Europe Standard Time:20261121T090000
      UID:windows@example.com
      SUMMARY:Outlook zone
      END:VEVENT
      BEGIN:VEVENT
      DTSTART;TZID=Mars/Olympus:20261122T080000
      UID:mars@example.com
      SUMMARY:Unknown zone
      END:VEVENT
      BEGIN:VEVENT
      DTSTART;VALUE=DATE:20261201
      RRULE:FREQ=MONTHLY;BYDAY=MO,TU;BYSETPOS=1
      UID:setpos@example.com
      SUMMARY:Unsupported rule
      END:VEVENT
      BEGIN:VEVENT
      DTSTART;VALUE=DATE:19850704
      UID:bday@example.com
      CATEGORIES:Birthday
      SUMMARY:Sam
      END:VEVENT
      BEGIN:VEVENT
      DTSTART;VALUE=DATE:20261203
      UID:cancelled@example.com
      STATUS:CANCELLED
      SUMMARY:Gone
      END:VEVENT
      END:VCALENDAR
    """.trimIndent()
  }
}
