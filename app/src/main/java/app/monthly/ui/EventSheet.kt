package app.monthly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.monthly.R
import app.monthly.logic.Occurrence
import app.monthly.ui.theme.LocalCalendarColors
import java.time.Instant
import java.time.ZoneId

/** Details of one occurrence with edit and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(model: AppViewModel, occurrence: Occurrence) {
  val format = rememberFormat()
  val event = occurrence.event
  val color = LocalCalendarColors.current.of(event.color)
  var askDelete by remember(occurrence) { mutableStateOf(false) }
  val repeating = event.recurrence != null && !event.isBirthday

  ModalBottomSheet(onDismissRequest = { model.detail = null }) {
    Column(
      Modifier
        .padding(horizontal = 24.dp)
        .padding(bottom = 24.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color, 14.dp)
        Spacer(Modifier.width(12.dp))
        Text(
          event.title.ifBlank { stringResource(R.string.untitled) },
          style = MaterialTheme.typography.headlineSmall
        )
      }

      DetailLine(Icons.Outlined.Schedule, whenText(occurrence, format))
      zoneText(occurrence, format, model.zone)?.let {
        DetailLine(Icons.Outlined.Public, it)
      }
      event.ageOn(occurrence.date)?.takeIf { it > 0 }?.let {
        DetailLine(Icons.Outlined.Cake, stringResource(R.string.turns_age, it))
      }
      if (repeating) {
        val rule = format.repeat(event.recurrence, event.startDate)
        DetailLine(Icons.Outlined.Repeat, rule)
      }
      if (event.reminders.isNotEmpty()) {
        val text = event.reminders
          .joinToString("\n") { format.reminder(it, event) }
        DetailLine(Icons.Outlined.Notifications, text)
      }
      if (event.location.isNotBlank()) {
        DetailLine(Icons.Outlined.Place, event.location)
      }
      if (event.notes.isNotBlank()) {
        DetailLine(Icons.AutoMirrored.Outlined.Notes, event.notes)
      }

      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(onClick = {
          if (repeating) askDelete = true else model.delete(event)
        }) {
          Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.delete))
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
          model.open(Screen.Editor(event, event.kind, occurrence.date))
        }) {
          Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(stringResource(R.string.edit))
        }
      }
    }
  }

  if (askDelete) {
    AlertDialog(
      onDismissRequest = { askDelete = false },
      title = { Text(stringResource(R.string.delete_repeating)) },
      text = {
        Column {
          TextButton(onClick = { model.deleteOne(occurrence) }) {
            Text(stringResource(R.string.delete_this))
          }
          TextButton(onClick = { model.delete(event) }) {
            Text(stringResource(R.string.delete_all))
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { askDelete = false }) {
          Text(stringResource(R.string.cancel))
        }
      }
    )
  }
}

@Composable
private fun DetailLine(icon: ImageVector, text: String) {
  Row(verticalAlignment = Alignment.Top) {
    Icon(
      icon, null,
      Modifier.size(20.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.width(16.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge)
  }
}

/** Device-clock date and time span of the occurrence. */
private fun whenText(occurrence: Occurrence, format: Format): String {
  val start = occurrence.start
  val end = occurrence.end
  if (start == null || end == null) {
    if (occurrence.firstDay == occurrence.lastDay) {
      return format.weekday(occurrence.firstDay)
    }
    val first = format.shortWeekday(occurrence.firstDay)
    return "$first - ${format.shortWeekday(occurrence.lastDay)}"
  }

  val from = format.time(start.toLocalTime())
  val to = format.time(end.toLocalTime())
  if (occurrence.firstDay == occurrence.lastDay) {
    return "${format.weekday(occurrence.firstDay)}\n$from - $to"
  }
  val first = format.shortWeekday(occurrence.firstDay)
  val last = format.shortWeekday(occurrence.lastDay)
  return "$first $from - $last $to"
}

/**
 * Timed: the span on the event's own clock. All-day with a zone (a
 * friend's birthday): their zone, their clock now, and when their day
 * starts and ends on my clock.
 */
@Composable
private fun zoneText(
  occurrence: Occurrence,
  format: Format,
  device: ZoneId
): String? {
  val zone = occurrence.event.zone ?: return null
  val start = occurrence.start
  val end = occurrence.end
  val now = Instant.now()

  if (start != null && end != null) {
    if (format.sameClock(zone, device, start.toInstant())) return null
    val from = format.time(start.withZoneSameInstant(zone).toLocalTime())
    val to = format.time(end.withZoneSameInstant(zone).toLocalTime())
    return stringResource(R.string.span_in, from, to, format.zoneLabel(zone))
  }

  if (format.sameClock(zone, device, now)) {
    return stringResource(R.string.their_zone, format.zoneLabel(zone))
  }

  val dayStart = occurrence.date.atStartOfDay(zone).withZoneSameInstant(device)
  val dayEnd = occurrence.date.plusDays(1).atStartOfDay(zone)
    .withZoneSameInstant(device)
  val from = "${format.shortWeekday(dayStart.toLocalDate())} " +
    format.time(dayStart.toLocalTime())
  val to = "${format.shortWeekday(dayEnd.toLocalDate())} " +
    format.time(dayEnd.toLocalTime())
  val clock = format.time(now.atZone(zone).toLocalTime())
  val label = format.zoneLabel(zone)
  val zoneLine = stringResource(R.string.their_zone_now, label, clock)
  return zoneLine + "\n" + stringResource(R.string.their_day, from, to)
}
