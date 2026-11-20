package app.monthly.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** The armed reminder alarm went off. */
class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) =
    deliverInBackground(context)
}

/**
 * Boot, clock or time zone change, app update, exact-alarm grant. Exported
 * for these system broadcasts, so other apps can call it explicitly too;
 * it acts on the system's actions only.
 */
class SystemReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action in ACTIONS) deliverInBackground(context)
  }

  companion object {

    /**
     * The manifest filter's actions, all protected broadcasts. The
     * exact-alarm one exists from API 31; its string is inlined, and older
     * releases never send it.
     */
    @SuppressLint("InlinedApi")
    val ACTIONS = setOf(
      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_TIMEZONE_CHANGED,
      Intent.ACTION_TIME_CHANGED,
      Intent.ACTION_MY_PACKAGE_REPLACED,
      AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
    )
  }
}

private fun BroadcastReceiver.deliverInBackground(context: Context) {
  val pending = goAsync()
  val app = context.applicationContext
  thread(name = "monthly-alarm") {
    try {
      ReminderScheduler.deliverDue(app)
    } finally {
      pending.finish()
    }
  }
}
