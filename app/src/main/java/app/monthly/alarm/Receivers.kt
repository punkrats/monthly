package app.monthly.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** The armed reminder alarm went off. */
class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) =
    deliverInBackground(context)
}

/** Boot, clock or time zone change, app update, exact-alarm grant. */
class SystemReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) =
    deliverInBackground(context)
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
