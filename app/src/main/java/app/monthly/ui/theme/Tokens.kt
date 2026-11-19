package app.monthly.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.monthly.model.EventColor

/** The only place colour literals live. */
internal object Tokens {

  val light = lightColorScheme(
    primary = Color(0xFF8E3B5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF3A0020),
    secondary = Color(0xFF75565E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E1),
    onSecondaryContainer = Color(0xFF2B151B),
    tertiary = Color(0xFF7C5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC1),
    onTertiaryContainer = Color(0xFF2E1500),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191B),
    surfaceVariant = Color(0xFFF3DDE1),
    onSurfaceVariant = Color(0xFF524346),
    outline = Color(0xFF847376),
    outlineVariant = Color(0xFFD6C2C5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainer = Color(0xFFFBEAEC),
    surfaceContainerHigh = Color(0xFFF5E4E6),
    surfaceContainerHighest = Color(0xFFEFDEE1)
  )

  val dark = darkColorScheme(
    primary = Color(0xFFFFB0CB),
    onPrimary = Color(0xFF561D33),
    primaryContainer = Color(0xFF722449),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = Color(0xFFE3BDC5),
    onSecondary = Color(0xFF422930),
    secondaryContainer = Color(0xFF5B3F46),
    onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = Color(0xFFEFBD94),
    onTertiary = Color(0xFF48290C),
    tertiaryContainer = Color(0xFF623F20),
    onTertiaryContainer = Color(0xFFFFDCC1),
    background = Color(0xFF1A1113),
    onBackground = Color(0xFFF0DEE1),
    surface = Color(0xFF1A1113),
    onSurface = Color(0xFFF0DEE1),
    surfaceVariant = Color(0xFF524346),
    onSurfaceVariant = Color(0xFFD6C2C5),
    outline = Color(0xFF9F8C90),
    outlineVariant = Color(0xFF524346),
    surfaceContainerLowest = Color(0xFF140C0E),
    surfaceContainerLow = Color(0xFF22191B),
    surfaceContainer = Color(0xFF271D1F),
    surfaceContainerHigh = Color(0xFF32282A),
    surfaceContainerHighest = Color(0xFF3D3234)
  )

  val lightCalendar = CalendarColors(
    cycle = Color(0xFFE0447A),
    events = mapOf(
      EventColor.BLUE to Color(0xFF3F6FD8),
      EventColor.TEAL to Color(0xFF00897B),
      EventColor.GREEN to Color(0xFF3C8D4F),
      EventColor.YELLOW to Color(0xFFB88A00),
      EventColor.ORANGE to Color(0xFFE0671B),
      EventColor.RED to Color(0xFFD64545),
      EventColor.PINK to Color(0xFFD63C8C),
      EventColor.PURPLE to Color(0xFF7E57C2),
      EventColor.GRAY to Color(0xFF6B7280)
    )
  )

  val darkCalendar = CalendarColors(
    cycle = Color(0xFFFF8FB3),
    events = mapOf(
      EventColor.BLUE to Color(0xFF9DB8FF),
      EventColor.TEAL to Color(0xFF6FD8C8),
      EventColor.GREEN to Color(0xFF8FD69C),
      EventColor.YELLOW to Color(0xFFF0C850),
      EventColor.ORANGE to Color(0xFFFFAB78),
      EventColor.RED to Color(0xFFFF9B92),
      EventColor.PINK to Color(0xFFFF9CCB),
      EventColor.PURPLE to Color(0xFFC5A8FF),
      EventColor.GRAY to Color(0xFFB5BBC6)
    )
  )

  /** Day number and event labels inside the compact month grid. */
  val cellDay = TextStyle(
    fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium
  )
  val cellLabel = TextStyle(fontSize = 10.sp, lineHeight = 12.sp)

  /** Strongest cycle shade; lighter steps scale down from here. */
  const val CYCLE_SHADE_MAX = 0.42f

  /** Background tint of event labels in the month grid. */
  const val EVENT_TINT = 0.22f
}

@Immutable
data class CalendarColors(
  val cycle: Color,
  val events: Map<EventColor, Color>
) {
  fun of(color: EventColor): Color = events.getValue(color)

  /** [level] 1..max is a lead day, max is the expected start itself. */
  fun cycleShade(level: Int, max: Int): Color {
    val alpha = Tokens.CYCLE_SHADE_MAX * level / max
    return cycle.copy(alpha = alpha)
  }
}
