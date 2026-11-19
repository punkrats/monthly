package app.monthly

import android.app.Application
import app.monthly.alarm.Notifier
import app.monthly.alarm.ReminderScheduler
import app.monthly.data.Repository
import app.monthly.data.SettingsStore

class MonthlyApp : Application() {

  lateinit var repository: Repository
    private set

  lateinit var settings: SettingsStore
    private set

  override fun onCreate() {
    super.onCreate()
    settings = SettingsStore(this)
    repository = Repository(this) { ReminderScheduler.afterEdit(this) }
    Notifier.createChannels(this)
  }
}
