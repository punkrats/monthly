package app.monthly.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.monthly.R
import app.monthly.model.Freq
import app.monthly.model.NthWeekday
import app.monthly.model.Recurrence
import app.monthly.model.Reminder
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** Minutes-before choices for timed events. */
internal val REMINDER_PRESETS =
  listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

private val DAY_PRESETS = listOf(0, 1, 2, 3, 7, 14)

@Composable
fun RadioRow(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  trailing: @Composable () -> Unit = {}
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(text)
    trailing()
  }
}

@Composable
private fun DialogButtons(onDismiss: () -> Unit, onConfirm: () -> Unit) {
  Row {
    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
  }
}

// Date and time

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(
  date: LocalDate,
  onDismiss: () -> Unit,
  onPick: (LocalDate) -> Unit
) {
  val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  val picker = rememberDatePickerState(
    initialSelectedDateMillis = millis,
    yearRange = 1900..2100
  )
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = {
        picker.selectedDateMillis
          ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC) }
          ?.let { onPick(it.toLocalDate()) }
        onDismiss()
      }) {
        Text(stringResource(R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
  ) {
    DatePicker(picker)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(
  time: LocalTime,
  onDismiss: () -> Unit,
  onPick: (LocalTime) -> Unit
) {
  val is24 = DateFormat.is24HourFormat(LocalContext.current)
  val picker = rememberTimePickerState(time.hour, time.minute, is24)
  Dialog(onDismissRequest = onDismiss) {
    Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
      Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        TimePicker(picker)
        Row(Modifier.align(Alignment.End)) {
          DialogButtons(onDismiss) {
            onPick(LocalTime.of(picker.hour, picker.minute))
            onDismiss()
          }
        }
      }
    }
  }
}

// Time zones

/** City zones from the JDK database, plus UTC. */
private object Zones {

  private val regions = setOf(
    "Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia",
    "Europe", "Indian", "Pacific"
  )

  val all: List<ZoneId> by lazy {
    // All-caps names (Australia/ACT, Australia/NSW) are legacy aliases.
    val ids = ZoneId.getAvailableZoneIds()
      .filter { it.substringBefore('/') in regions && '/' in it }
      .filter { id -> id.substringAfterLast('/').any { it.isLowerCase() } }
    (ids + "UTC")
      .map { ZoneId.of(it) }
      .sortedBy { it.id.substringAfterLast('/') }
  }

  fun names(locale: Locale): Map<ZoneId, String> =
    all.associateWith { it.getDisplayName(TextStyle.FULL, locale) }

  /** Matches city, region or the zone's name ("japan" finds Tokyo). */
  fun search(query: String, names: Map<ZoneId, String>): List<ZoneId> {
    val text = query.trim().lowercase()
    if (text.isEmpty()) return all
    return all.filter { zone ->
      val id = zone.id.lowercase().replace('_', ' ')
      text in id || text in names[zone].orEmpty().lowercase()
    }
  }
}

/** Full-screen searchable zone list. [allowMine] adds "same as mine". */
@Composable
fun ZoneDialog(
  current: ZoneId?,
  allowMine: Boolean,
  device: ZoneId,
  onDismiss: () -> Unit,
  onPick: (ZoneId?) -> Unit
) {
  val format = rememberFormat()
  var query by remember { mutableStateOf("") }
  val names = remember(format.locale) { Zones.names(format.locale) }
  val matches = remember(query) { Zones.search(query, names) }
  val now = remember { Instant.now() }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  val pick = { zone: ZoneId? ->
    onPick(zone)
    onDismiss()
  }
  val detail = { zone: ZoneId ->
    val clock = format.time(now.atZone(zone).toLocalTime())
    "${format.offset(zone, now)} · $clock · ${names[zone].orEmpty()}"
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(Modifier.fillMaxSize()) {
      Column(
        Modifier
          .systemBarsPadding()
          .imePadding()
      ) {
        Row(
          Modifier.padding(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, stringResource(R.string.close))
          }
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_zones)) },
            singleLine = true,
            modifier = Modifier
              .weight(1f)
              .focusRequester(focus)
          )
        }

        LazyColumn {
          if (query.isBlank() && allowMine) {
            item {
              val label = stringResource(R.string.same_as_mine)
              ZoneRow(label, format.zoneLabel(device), current == null) {
                pick(null)
              }
            }
          }
          if (query.isBlank()) {
            item {
              ZoneRow(format.city(device), detail(device), current == device) {
                pick(device)
              }
            }
          }
          items(matches, key = { it.id }) { zone ->
            ZoneRow(format.city(zone), detail(zone), zone == current) {
              pick(zone)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ZoneRow(
  title: String,
  detail: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(detail) },
    trailingContent = {
      if (selected) Icon(Icons.Filled.Check, null)
    },
    modifier = Modifier.clickable(onClick = onClick)
  )
}

// Repeat rules

@Stable
private class RepeatDraft(rule: Recurrence?, val start: LocalDate) {
  var repeats by mutableStateOf(rule != null)
  var rule by mutableStateOf(rule ?: Recurrence(Freq.WEEKLY))
  var pickingUntil by mutableStateOf(false)

  fun result() = if (repeats) rule else null

  fun choose(freq: Freq) {
    repeats = true
    rule = rule.copy(freq = freq, byDay = emptySet(), nth = null)
  }

  fun toggle(day: DayOfWeek) {
    val days = rule.byDay.ifEmpty { setOf(start.dayOfWeek) }
    val next = if (day in days) days - day else days + day
    if (next.isNotEmpty()) rule = rule.copy(byDay = next)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatDialog(
  rule: Recurrence?,
  start: LocalDate,
  onDismiss: () -> Unit,
  onPick: (Recurrence?) -> Unit
) {
  val draft = remember { RepeatDraft(rule, start) }
  val labels = mapOf(
    Freq.DAILY to R.string.freq_daily,
    Freq.WEEKLY to R.string.freq_weekly,
    Freq.MONTHLY to R.string.freq_monthly,
    Freq.YEARLY to R.string.freq_yearly
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.repeat)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = !draft.repeats,
            onClick = { draft.repeats = false },
            label = { Text(stringResource(R.string.repeat_none)) }
          )
          Freq.entries.forEach { freq ->
            FilterChip(
              selected = draft.repeats && draft.rule.freq == freq,
              onClick = { draft.choose(freq) },
              label = { Text(stringResource(labels.getValue(freq))) }
            )
          }
        }
        if (draft.repeats) RepeatOptions(draft)
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onPick(draft.result())
        onDismiss()
      }) {
        Text(stringResource(R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
  )

  if (draft.pickingUntil) {
    val until = draft.rule.until ?: start.plusMonths(3)
    DateDialog(until, { draft.pickingUntil = false }) {
      draft.rule = draft.rule.copy(until = it, count = null)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatOptions(draft: RepeatDraft) {
  val format = rememberFormat()
  val rule = draft.rule
  val every = when (rule.freq) {
    Freq.DAILY -> R.plurals.every_day
    Freq.WEEKLY -> R.plurals.every_week
    Freq.MONTHLY -> R.plurals.every_month
    Freq.YEARLY -> R.plurals.every_year
  }
  Stepper(rule.interval, 1..99, format.plural(every, rule.interval)) {
    draft.rule = rule.copy(interval = it)
  }

  if (rule.freq == Freq.WEEKLY) {
    val days = rule.byDay.ifEmpty { setOf(draft.start.dayOfWeek) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      DayOfWeek.entries.forEach { day ->
        FilterChip(
          selected = day in days,
          onClick = { draft.toggle(day) },
          label = { Text(format.dayName(day)) }
        )
      }
    }
  }

  if (rule.freq == Freq.MONTHLY || rule.freq == Freq.YEARLY) {
    MonthlyOptions(draft, format)
  }
  EndOptions(draft, format)
}

/** On the start's day of month, or on its nth / last weekday. */
@Composable
private fun MonthlyOptions(draft: RepeatDraft, format: Format) {
  val start = draft.start
  val week = (start.dayOfMonth - 1) / 7 + 1
  val lastWeek = start.dayOfMonth > start.lengthOfMonth() - 7
  val options = listOfNotNull(
    NthWeekday(week, start.dayOfWeek).takeIf { week <= 4 },
    NthWeekday(-1, start.dayOfWeek).takeIf { lastWeek }
  )
  val yearly = draft.rule.freq == Freq.YEARLY
  val month = start.month.getDisplayName(TextStyle.FULL, format.locale)

  val onDate = if (yearly) {
    stringResource(R.string.repeat_on, format.dayMonth(start))
  } else {
    stringResource(R.string.repeat_on_day, start.dayOfMonth)
  }
  RadioRow(onDate, draft.rule.nth == null, {
    draft.rule = draft.rule.copy(nth = null)
  })

  options.forEach { nth ->
    val text = if (yearly) {
      stringResource(R.string.repeat_in_month, format.nthText(nth), month)
    } else {
      format.nthText(nth)
    }
    RadioRow(text, draft.rule.nth == nth, {
      draft.rule = draft.rule.copy(nth = nth)
    })
  }
}

@Composable
private fun EndOptions(draft: RepeatDraft, format: Format) {
  val rule = draft.rule
  val never = rule.until == null && rule.count == null
  RadioRow(stringResource(R.string.ends_never), never, {
    draft.rule = rule.copy(until = null, count = null)
  })

  val until = rule.until ?: draft.start.plusMonths(3)
  RadioRow(stringResource(R.string.ends_on), rule.until != null, {
    draft.pickingUntil = true
  }) {
    TextButton(onClick = { draft.pickingUntil = true }) {
      Text(format.date(until))
    }
  }

  val count = rule.count ?: 10
  RadioRow(stringResource(R.string.ends_after), rule.count != null, {
    draft.rule = rule.copy(until = null, count = count)
  }) {
    Stepper(count, 1..999, format.plural(R.plurals.times, count)) {
      draft.rule = draft.rule.copy(until = null, count = it)
    }
  }
}

// Reminders

/** All-day and birthday reminders: which day, what time, whose clock. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnDayDialog(
  zoned: Boolean,
  onDismiss: () -> Unit,
  onPick: (Reminder.OnDay) -> Unit
) {
  val format = rememberFormat()
  val is24 = DateFormat.is24HourFormat(LocalContext.current)
  var days by remember { mutableIntStateOf(0) }
  var theirs by remember { mutableStateOf(true) }
  val time = rememberTimePickerState(9, 0, is24)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.add_reminder)) },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DAY_PRESETS.forEach { n ->
            val label = if (n == 0) {
              stringResource(R.string.reminder_on_the_day)
            } else {
              format.plural(R.plurals.reminder_days, n)
            }
            FilterChip(
              selected = days == n,
              onClick = { days = n },
              label = { Text(label) }
            )
          }
        }
        TimeInput(time)
        if (zoned) ClockChoice(theirs) { theirs = it }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        val at = LocalTime.of(time.hour, time.minute)
        onPick(Reminder.OnDay(days, at, theirs || !zoned))
        onDismiss()
      }) {
        Text(stringResource(R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockChoice(theirs: Boolean, onChange: (Boolean) -> Unit) {
  val choices = listOf(true to R.string.their_time, false to R.string.my_time)
  SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
    choices.forEachIndexed { index, (value, label) ->
      SegmentedButton(
        selected = theirs == value,
        onClick = { onChange(value) },
        shape = SegmentedButtonDefaults.itemShape(index, choices.size)
      ) {
        Text(stringResource(label))
      }
    }
  }
}

/** Timed events: how long before the start. */
@Composable
fun BeforeDialog(onDismiss: () -> Unit, onPick: (Reminder.Before) -> Unit) {
  val format = rememberFormat()
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.add_reminder)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        REMINDER_PRESETS.forEach { minutes ->
          Text(
            format.before(minutes),
            Modifier
              .fillMaxWidth()
              .clickable {
                onPick(Reminder.Before(minutes))
                onDismiss()
              }
              .padding(vertical = 12.dp)
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
  )
}
