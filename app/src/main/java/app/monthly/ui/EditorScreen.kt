package app.monthly.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monthly.R
import app.monthly.logic.Alerts
import app.monthly.model.EventColor
import app.monthly.model.EventKind
import app.monthly.model.Reminder
import app.monthly.ui.theme.LocalCalendarColors
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(model: AppViewModel, screen: Screen.Editor) {
  val settings by model.settings.collectAsStateWithLifecycle()
  val format = rememberFormat()
  val state = remember(screen) {
    EditorState(screen.event, screen.kind, screen.date, settings, model.zone)
  }
  val heading =
    if (state.original == null) R.string.new_entry else R.string.edit_entry

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = { model.back() }) {
            Icon(Icons.Filled.Close, stringResource(R.string.cancel))
          }
        },
        title = { Text(stringResource(heading)) },
        actions = {
          TextButton(onClick = { state.toEvent()?.let { model.save(it) } }) {
            Text(stringResource(R.string.save))
          }
        }
      )
    }
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .fillMaxSize()
        .imePadding()
        .verticalScroll(rememberScrollState())
    ) {
      if (state.original == null) KindSwitch(state)
      TitleField(state)
      if (state.isBirthday) {
        BirthdayFields(state, format)
      } else {
        EventFields(state, format)
      }
      RemindersSection(state, format)
      ColorRow(state)
      if (!state.isBirthday) {
        TextInput(state.location, R.string.location) { state.location = it }
      }
      TextInput(state.notes, R.string.notes, singleLine = false) {
        state.notes = it
      }

      state.original?.let { original ->
        OutlinedButton(
          onClick = { model.delete(original) },
          modifier = Modifier.padding(16.dp)
        ) {
          Text(
            stringResource(R.string.delete),
            color = MaterialTheme.colorScheme.error
          )
        }
      }
      Spacer(Modifier.height(32.dp))
    }
  }

  EditorDialogs(state)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSwitch(state: EditorState) {
  val kinds = listOf(
    EventKind.EVENT to R.string.kind_event,
    EventKind.BIRTHDAY to R.string.kind_birthday
  )
  SingleChoiceSegmentedButtonRow(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    kinds.forEachIndexed { index, (kind, label) ->
      SegmentedButton(
        selected = state.kind == kind,
        onClick = { state.switchKind(kind) },
        shape = SegmentedButtonDefaults.itemShape(index, kinds.size)
      ) {
        Text(stringResource(label))
      }
    }
  }
}

@Composable
private fun TitleField(state: EditorState) {
  val label = if (state.isBirthday) R.string.name else R.string.title
  OutlinedTextField(
    value = state.title,
    onValueChange = {
      state.title = it
      state.titleError = false
    },
    label = { Text(stringResource(label)) },
    isError = state.titleError,
    supportingText = if (state.titleError) {
      { Text(stringResource(R.string.title_required)) }
    } else {
      null
    },
    singleLine = true,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
  )
}

@Composable
private fun TextInput(
  value: String,
  label: Int,
  singleLine: Boolean = true,
  onChange: (String) -> Unit
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(stringResource(label)) },
    singleLine = singleLine,
    minLines = if (singleLine) 1 else 3,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
  )
}

@Composable
private fun EventFields(state: EditorState, format: Format) {
  ListItem(
    trailingContent = {
      Switch(
        checked = state.allDay,
        onCheckedChange = { state.changeAllDay(it) }
      )
    },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
  ) {
    Text(stringResource(R.string.all_day))
  }
  DateTimeRow(state, start = true, format)
  DateTimeRow(state, start = false, format)

  if (!state.allDay) {
    val zone = format.zoneLabel(state.zone ?: state.device)
    PickerRow(Icons.Outlined.Public, stringResource(R.string.time_zone), zone) {
      state.dialog = EditorDialog.Zone
    }
  }
  val repeat = format.repeat(state.recurrence, state.startDate)
  PickerRow(Icons.Outlined.Repeat, stringResource(R.string.repeat), repeat) {
    state.dialog = EditorDialog.Repeat
  }
}

@Composable
private fun DateTimeRow(state: EditorState, start: Boolean, format: Format) {
  val date = if (start) state.startDate else state.endDate
  val time = if (start) state.startTime else state.endTime
  val label = if (start) R.string.starts else R.string.ends
  val dateText = format.shortWeekday(date) +
    if (date.year != LocalDate.now().year) " ${date.year}" else ""

  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      stringResource(label),
      Modifier.width(72.dp),
      style = MaterialTheme.typography.bodyLarge
    )
    TextButton(onClick = {
      state.dialog = if (start) EditorDialog.StartDate else EditorDialog.EndDate
    }) {
      Text(dateText)
    }
    Spacer(Modifier.weight(1f))
    if (!state.allDay) {
      val dialog = if (start) EditorDialog.StartTime else EditorDialog.EndTime
      TextButton(onClick = { state.dialog = dialog }) {
        Text(format.time(time))
      }
    }
  }
}

@Composable
private fun BirthdayFields(state: EditorState, format: Format) {
  val date = if (state.yearKnown) {
    format.date(state.startDate)
  } else {
    format.dayMonth(state.startDate)
  }
  PickerRow(Icons.Outlined.Cake, stringResource(R.string.birthday_date), date) {
    state.dialog = EditorDialog.StartDate
  }
  ListItem(
    trailingContent = {
      Switch(
        checked = state.yearKnown,
        onCheckedChange = { state.yearKnown = it }
      )
    },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
  ) {
    Text(stringResource(R.string.include_year))
  }

  val zone = state.zone
  val zoneText = if (zone == null) {
    stringResource(R.string.same_as_mine)
  } else {
    format.zoneLabel(zone)
  }
  val zoneLabel = stringResource(R.string.their_time_zone)
  PickerRow(Icons.Outlined.Public, zoneLabel, zoneText) {
    state.dialog = EditorDialog.Zone
  }
}

@Composable
private fun RemindersSection(state: EditorState, format: Format) {
  val draft = state.draft()
  Text(
    stringResource(R.string.reminders),
    Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary
  )

  state.reminders.toList().forEach { reminder ->
    ListItem(
      leadingContent = { Icon(Icons.Outlined.Notifications, null) },
      trailingContent = {
        IconButton(onClick = { state.reminders.remove(reminder) }) {
          Icon(Icons.Outlined.Clear, stringResource(R.string.remove))
        }
      },
      supportingContent = preview(state, reminder, format)
        ?.let { { Text(it) } },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ) {
      Text(format.reminder(reminder, draft))
    }
  }

  TextButton(
    onClick = { state.dialog = EditorDialog.NewReminder },
    modifier = Modifier.padding(horizontal = 8.dp)
  ) {
    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
    Spacer(Modifier.width(6.dp))
    Text(stringResource(R.string.add_reminder))
  }
}

/** When this reminder fires next, on my clock and, if different, theirs. */
@Composable
private fun preview(
  state: EditorState,
  reminder: Reminder,
  format: Format
): String? {
  val device = state.device
  val event = state.draft().copy(reminders = listOf(reminder))
  val next = Alerts.next(listOf(event), Instant.now(), device) ?: return null
  val here = next.atZone(device)
  val today = LocalDate.now(device)
  val text = format.relativeDay(here.toLocalDate(), today) + " " +
    format.time(here.toLocalTime())

  val zone = event.zone
  if (zone == null || format.sameClock(zone, device, next)) {
    return stringResource(R.string.next_alert, text)
  }
  val there = format.time(next.atZone(zone).toLocalTime())
  val city = format.city(zone)
  return stringResource(R.string.next_alert_zoned, text, there, city)
}

@Composable
private fun ColorRow(state: EditorState) {
  val colors = LocalCalendarColors.current
  val outline = MaterialTheme.colorScheme.onSurface
  Row(
    Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    EventColor.entries.forEach { color ->
      val ring = if (state.color == color) outline else Color.Transparent
      ColorDot(
        colors.of(color),
        size = 32.dp,
        modifier = Modifier
          .border(3.dp, ring, CircleShape)
          .clickable { state.color = color }
      )
    }
  }
}

@Composable
private fun EditorDialogs(state: EditorState) {
  val dialog = state.dialog ?: return
  val close = { state.dialog = null }

  when (dialog) {
    EditorDialog.StartDate ->
      DateDialog(state.startDate, close) { state.moveStart(date = it) }
    EditorDialog.StartTime ->
      TimeDialog(state.startTime, close) { state.moveStart(time = it) }
    EditorDialog.EndDate ->
      DateDialog(state.endDate, close) { state.endDate = it }
    EditorDialog.EndTime ->
      TimeDialog(state.endTime, close) { state.endTime = it }
    EditorDialog.Zone ->
      ZoneDialog(state.zone, state.isBirthday, state.device, close) {
        state.zone = it
      }
    EditorDialog.Repeat ->
      RepeatDialog(state.recurrence, state.startDate, close) {
        state.recurrence = it
      }
    EditorDialog.NewReminder -> if (state.remindsOnDay) {
      val zoned = state.zone != null
      OnDayDialog(zoned, close) { state.addReminder(it) }
    } else {
      BeforeDialog(close) { state.addReminder(it) }
    }
  }
}
