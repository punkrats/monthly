package app.monthly.data

import android.content.Context
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [weekStart] null follows the locale. [eventReminder] is the default
 * minutes-before for new timed events, null for none.
 */
data class Settings(
  val weekStart: DayOfWeek? = null,
  val cycleEnabled: Boolean = true,
  val leadDays: Int = 3,
  val eventReminder: Int? = 15,
  val birthdayTime: LocalTime = LocalTime.of(9, 0)
)

class SettingsStore(context: Context) {

  private val prefs =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val state = MutableStateFlow(read())

  val settings: StateFlow<Settings> = state.asStateFlow()

  /** Upper bound of alerts already delivered. */
  var lastCheck: Instant?
    get() = prefs.getLong("last_check", 0).takeIf { it > 0 }
      ?.let { Instant.ofEpochMilli(it) }
    set(value) {
      prefs.edit().putLong("last_check", value?.toEpochMilli() ?: 0).apply()
    }

  fun update(transform: (Settings) -> Settings) {
    val next = transform(state.value)
    prefs.edit()
      .putInt("week_start", next.weekStart?.value ?: 0)
      .putBoolean("cycle_enabled", next.cycleEnabled)
      .putInt("lead_days", next.leadDays)
      .putInt("event_reminder", next.eventReminder ?: -1)
      .putString("birthday_time", next.birthdayTime.toString())
      .apply()
    state.value = next
  }

  private fun read(): Settings {
    val defaults = Settings()
    val weekStart = prefs.getInt("week_start", 0)
    val reminder = prefs.getInt("event_reminder", defaults.eventReminder ?: -1)
    val time = prefs.getString("birthday_time", null)
      ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    return Settings(
      weekStart = if (weekStart in 1..7) DayOfWeek.of(weekStart) else null,
      cycleEnabled = prefs.getBoolean("cycle_enabled", defaults.cycleEnabled),
      leadDays = prefs.getInt("lead_days", defaults.leadDays),
      eventReminder = reminder.takeIf { it >= 0 },
      birthdayTime = time ?: defaults.birthdayTime
    )
  }
}
