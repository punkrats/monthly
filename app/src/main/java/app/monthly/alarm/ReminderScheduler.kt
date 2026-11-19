package app.monthly.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.monthly.MonthlyApp
import app.monthly.logic.Alerts
import app.monthly.model.Event
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps exactly one alarm armed for the earliest upcoming alert. Delivery
 * covers everything in (lastCheck, now], so late or missed alarms (device
 * off, Doze) still notify, at most a day back.
 */
object ReminderScheduler {

  private val lookback = Duration.ofHours(24)

  /** After an edit: skip alerts that already lie in the past. */
  @Synchronized
  fun afterEdit(context: Context) {
    val app = context.applicationContext as MonthlyApp
    val now = Instant.now()
    app.settings.lastCheck = now
    schedule(context, app.repository.snapshot(), now)
  }

  /** Alarm, boot, clock change, app opened: deliver what is due. */
  @Synchronized
  fun deliverDue(context: Context) {
    val app = context.applicationContext as MonthlyApp
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val events = app.repository.snapshot()
    val since = app.settings.lastCheck
      ?.let { maxOf(it, now.minus(lookback)) }
      ?: now

    Alerts.between(events, since, now, zone)
      .forEach { Notifier.show(context, it, zone) }
    app.settings.lastCheck = now
    schedule(context, events, now)
  }

  private fun schedule(context: Context, events: List<Event>, now: Instant) {
    val manager = context.getSystemService(AlarmManager::class.java)
    val intent = Intent(context, AlarmReceiver::class.java)
    val flags =
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
    val next = Alerts.next(events, now, ZoneId.systemDefault())
    if (next == null) {
      manager.cancel(pending)
      return
    }

    val at = next.toEpochMilli()
    val type = AlarmManager.RTC_WAKEUP
    if (canBeExact(manager)) {
      manager.setExactAndAllowWhileIdle(type, at, pending)
    } else {
      manager.setAndAllowWhileIdle(type, at, pending)
    }
  }

  fun canBeExact(manager: AlarmManager) =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
      manager.canScheduleExactAlarms()
}
