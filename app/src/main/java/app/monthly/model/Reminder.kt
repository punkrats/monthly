package app.monthly.model

import java.time.LocalTime
import java.util.Locale

sealed interface Reminder {

  /** Timed events: an offset before the start instant. */
  data class Before(val minutes: Int) : Reminder

  /**
   * All-day events: fires at [time] on the day [daysBefore] days ahead of
   * the event, on the clock of the event's zone ([theirZone]) or mine.
   */
  data class OnDay(
    val daysBefore: Int,
    val time: LocalTime,
    val theirZone: Boolean
  ) : Reminder

  companion object {

    /** Compact storage form, e.g. "B15;D1@1800@M;D0@0900@T". */
    fun encode(reminders: List<Reminder>): String =
      reminders.joinToString(";") { reminder ->
        when (reminder) {
          is Before -> "B${reminder.minutes}"
          is OnDay -> {
            val time = String.format(
              Locale.ROOT, "%02d%02d", reminder.time.hour,
              reminder.time.minute
            )
            val zone = if (reminder.theirZone) "T" else "M"
            "D${reminder.daysBefore}@$time@$zone"
          }
        }
      }

    fun decode(text: String): List<Reminder> =
      text.split(';').mapNotNull { decodeOne(it.trim()) }

    private fun decodeOne(token: String): Reminder? {
      if (token.startsWith("B")) {
        return token.drop(1).toIntOrNull()?.let { Before(it) }
      }
      if (!token.startsWith("D")) return null

      val parts = token.drop(1).split('@')
      if (parts.size != 3 || parts[1].length != 4) return null
      val days = parts[0].toIntOrNull() ?: return null
      val hour = parts[1].take(2).toIntOrNull() ?: return null
      val minute = parts[1].drop(2).toIntOrNull() ?: return null
      if (hour !in 0..23 || minute !in 0..59) return null
      return OnDay(days, LocalTime.of(hour, minute), parts[2] == "T")
    }
  }
}
