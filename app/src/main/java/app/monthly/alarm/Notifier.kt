package app.monthly.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.monthly.MainActivity
import app.monthly.R
import app.monthly.logic.Alert
import app.monthly.ui.Format
import java.time.ZoneId

object Notifier {

  private const val CHANNEL_EVENTS = "events"
  private const val CHANNEL_BIRTHDAYS = "birthdays"

  fun createChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val importance = NotificationManager.IMPORTANCE_HIGH
    val channels = listOf(
      NotificationChannel(
        CHANNEL_EVENTS, context.getString(R.string.channel_events), importance
      ),
      NotificationChannel(
        CHANNEL_BIRTHDAYS, context.getString(R.string.channel_birthdays),
        importance
      )
    )
    manager.createNotificationChannels(channels)
  }

  /** Without the permission the app shows a banner instead. */
  fun show(context: Context, alert: Alert, device: ZoneId) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val permission = Manifest.permission.POST_NOTIFICATIONS
      val state = ContextCompat.checkSelfPermission(context, permission)
      if (state != PackageManager.PERMISSION_GRANTED) return
    }

    val format = Format(context)
    val title = format.alertTitle(alert)
    val body = format.alertBody(alert, device)
    val id = listOf(alert.event.uid, alert.date, alert.reminder).hashCode()
    val birthday = alert.event.isBirthday
    val channel = if (birthday) CHANNEL_BIRTHDAYS else CHANNEL_EVENTS

    val open = Intent(context, MainActivity::class.java)
      .putExtra(MainActivity.EXTRA_DATE, alert.date.toString())
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val flags =
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    val tap = PendingIntent.getActivity(context, id, open, flags)

    val notification = NotificationCompat.Builder(context, channel)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setWhen(alert.at.toEpochMilli())
      .setShowWhen(true)
      .setContentIntent(tap)
      .setAutoCancel(true)
      .build()

    NotificationManagerCompat.from(context).notify(id, notification)
  }

}
