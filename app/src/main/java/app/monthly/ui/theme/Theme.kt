package app.monthly.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalCalendarColors = staticCompositionLocalOf { Tokens.lightCalendar }

@Composable
fun MonthlyTheme(content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  val scheme = if (dark) Tokens.dark else Tokens.light
  val calendar = if (dark) Tokens.darkCalendar else Tokens.lightCalendar
  CompositionLocalProvider(LocalCalendarColors provides calendar) {
    MaterialTheme(colorScheme = scheme, content = content)
  }
}
