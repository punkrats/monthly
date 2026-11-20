package app.monthly.ui

import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.monthly.R
import app.monthly.logic.Occurrence
import app.monthly.logic.Occurrences
import app.monthly.logic.cycleDayOf
import app.monthly.ui.theme.LocalCalendarColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The selected day: cycle day, forecast, and its entries. */
@Composable
fun DayPanel(model: AppViewModel, data: CalendarData, modifier: Modifier) {
  val format = rememberFormat()
  val day = model.selected
  val occurrences = remember(data.events, day, model.zone) {
    Occurrences.byDay(data.events, day, day, model.zone)[day].orEmpty()
  }

  Surface(
    modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
      item { DayHeader(model, data, day, format) }
      if (!model.notificationsOn) item { NotificationsBanner() }
      if (occurrences.isEmpty()) {
        item {
          Text(
            stringResource(R.string.nothing_planned),
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      items(occurrences, key = { "${it.event.uid}/${it.date}" }) {
        OccurrenceRow(model, it, day, format)
      }
    }
  }
}

@Composable
private fun DayHeader(
  model: AppViewModel,
  data: CalendarData,
  day: LocalDate,
  format: Format
) {
  val cycleColor = LocalCalendarColors.current.cycle
  Column(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .padding(top = 16.dp, bottom = 4.dp)
  ) {
    Text(format.weekday(day), style = MaterialTheme.typography.titleMedium)
    cycleLine(data, day, format, model.today)?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = cycleColor)
    }
  }
}

/**
 * "Cycle day 12 · Next start expected 3 Dec (average 28 days)". The cycle
 * day is known up to today only; later days belong to the forecast.
 */
@Composable
private fun cycleLine(
  data: CalendarData,
  day: LocalDate,
  format: Format,
  today: LocalDate
): String? {
  if (!data.cycleEnabled) return null

  val parts = mutableListOf<String>()
  cycleDayOf(data.starts, day)?.takeIf { day <= today }?.let {
    parts += stringResource(R.string.cycle_day, it)
  }
  data.forecast?.let { forecast ->
    val next = forecast.expected.firstOrNull { it >= today }
      ?: forecast.expected.last()
    val basis =
      if (forecast.assumed) R.plurals.cycle_assumed else R.plurals.cycle_average
    val length = format.plural(basis, forecast.averageLength)
    parts += stringResource(R.string.cycle_next, format.dayMonth(next), length)
  }
  return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun NotificationsBanner() {
  val context = LocalContext.current
  Card(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer
    )
  ) {
    Row(
      Modifier.padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(Icons.Outlined.NotificationsOff, null)
      Text(
        stringResource(R.string.notifications_off),
        Modifier
          .weight(1f)
          .padding(horizontal = 12.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium
      )
      TextButton(onClick = {
        val intent = Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
          .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
        context.startActivity(intent)
      }) {
        Text(stringResource(R.string.turn_on))
      }
    }
  }
}

/** A day-list row, shared by the day panel and the agenda. */
@Composable
fun OccurrenceRow(
  model: AppViewModel,
  occurrence: Occurrence,
  day: LocalDate,
  format: Format
) {
  val event = occurrence.event
  val color = LocalCalendarColors.current.of(event.color)
  val title = event.title.ifBlank { stringResource(R.string.untitled) }
  val subtitle = subtitle(occurrence, format, model.zone)

  Row(
    Modifier
      .fillMaxWidth()
      .clickable { model.detail = occurrence }
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    ColorBar(color)
    TimeColumn(occurrence, day, format)
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (event.isBirthday) {
          Icon(Icons.Outlined.Cake, null, Modifier.size(16.dp), tint = color)
          Spacer(Modifier.width(4.dp))
        }
        Text(
          title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      subtitle?.let {
        Text(
          it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun TimeColumn(occurrence: Occurrence, day: LocalDate, format: Format) {
  val start = occurrence.start
  val end = occurrence.end
  val style = MaterialTheme.typography.bodySmall
  val muted = MaterialTheme.colorScheme.onSurfaceVariant

  Column(Modifier.width(56.dp)) {
    if (start == null || end == null) {
      Text(stringResource(R.string.all_day), style = style)
      return@Column
    }

    val from = if (occurrence.firstDay == day) {
      format.time(start.toLocalTime())
    } else {
      format.dayMonth(occurrence.firstDay)
    }
    val to = if (occurrence.lastDay == day) {
      format.time(end.toLocalTime())
    } else {
      format.dayMonth(occurrence.lastDay)
    }
    Text(from, style = MaterialTheme.typography.bodyMedium)
    Text(to, style = style, color = muted)
  }
}

/** Age, the other zone's clock, and the location. */
@Composable
private fun subtitle(
  occurrence: Occurrence,
  format: Format,
  device: ZoneId
): String? {
  val event = occurrence.event
  val zone = event.zone
  val now = Instant.now()
  val parts = mutableListOf<String>()

  event.ageOn(occurrence.date)?.takeIf { it > 0 }?.let {
    parts += stringResource(R.string.turns_age, it)
  }

  val start = occurrence.start
  if (zone != null && start != null) {
    if (!format.sameClock(zone, start.zone, start.toInstant())) {
      val there = format.time(start.withZoneSameInstant(zone).toLocalTime())
      parts += stringResource(R.string.time_in, there, format.city(zone))
    }
  } else if (zone != null && !format.sameClock(zone, device, now)) {
    val there = format.time(now.atZone(zone).toLocalTime())
    parts += stringResource(R.string.zone_now, format.city(zone), there)
  }

  if (event.location.isNotBlank()) parts += event.location
  return parts.joinToString(" · ").ifEmpty { null }
}
