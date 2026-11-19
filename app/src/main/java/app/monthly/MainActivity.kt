package app.monthly

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import app.monthly.ui.AppViewModel
import app.monthly.ui.MonthlyRoot
import app.monthly.ui.theme.MonthlyTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

  private val model: AppViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (savedInstanceState == null) handle(intent)
    setContent {
      MonthlyTheme { MonthlyRoot(model) }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handle(intent)
  }

  override fun onResume() {
    super.onResume()
    model.resume()
  }

  /** Notification taps carry a date; .ics files arrive via VIEW or SEND. */
  private fun handle(intent: Intent?) {
    intent ?: return
    intent.getStringExtra(EXTRA_DATE)
      ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
      ?.let { model.jumpTo(it) }

    val file = when (intent.action) {
      Intent.ACTION_VIEW -> intent.data
      Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
        intent, Intent.EXTRA_STREAM, Uri::class.java
      )
      else -> null
    }
    file?.let { model.prepareImport(it) }
  }

  companion object {
    const val EXTRA_DATE = "app.monthly.DATE"
  }
}
