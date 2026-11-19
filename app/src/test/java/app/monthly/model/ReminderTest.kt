package app.monthly.model

import app.monthly.time
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTest {

  @Test
  fun roundTripsTheStorageForm() {
    val reminders = listOf(
      Reminder.Before(15),
      Reminder.Before(0),
      Reminder.OnDay(1, time("18:00"), theirZone = false),
      Reminder.OnDay(0, time("09:05"), theirZone = true)
    )

    val text = Reminder.encode(reminders)

    assertEquals("B15;B0;D1@1800@M;D0@0905@T", text)
    assertEquals(reminders, Reminder.decode(text))
  }

  @Test
  fun dropsGarbageTokens() {
    val decoded = Reminder.decode("B15;;X;D1@2500@T;D1@0900")

    assertEquals(listOf(Reminder.Before(15)), decoded)
  }
}
