package app.monthly.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessAlarm
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monthly.BuildConfig
import app.monthly.R
import app.monthly.alarm.ReminderScheduler
import app.monthly.data.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

private enum class SettingsDialog {
  WEEK_START, EVENT_REMINDER, BIRTHDAY_TIME, EXPORT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(model: AppViewModel) {
  val settings by model.settings.collectAsStateWithLifecycle()
  val format = rememberFormat()
  val context = LocalContext.current
  var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
  var includeCycle by rememberSaveable { mutableStateOf(false) }

  val importer = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
  ) { uri -> uri?.let { model.prepareImport(it) } }
  val exporter = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("text/calendar")
  ) { uri -> uri?.let { model.export(it, includeCycle) } }

  val weekStart = settings.weekStart
    ?.let { format.dayName(it, TextStyle.FULL) }
    ?: stringResource(R.string.locale_default)
  val reminder = settings.eventReminder
    ?.let { format.before(it) }
    ?: stringResource(R.string.none)
  val alarms = context.getSystemService(AlarmManager::class.java)
  val exact = ReminderScheduler.canBeExact(alarms)

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = { model.back() }) {
            Icon(
              Icons.AutoMirrored.Filled.ArrowBack,
              stringResource(R.string.back)
            )
          }
        },
        title = { Text(stringResource(R.string.settings)) }
      )
    }
  ) { padding ->
    LazyColumn(contentPadding = padding) {
      item { Section(R.string.section_calendar) }
      item {
        PickerRow(
          Icons.Outlined.CalendarViewWeek,
          stringResource(R.string.week_starts),
          weekStart
        ) { dialog = SettingsDialog.WEEK_START }
      }
      item {
        PickerRow(
          Icons.Outlined.Notifications,
          stringResource(R.string.default_reminder),
          reminder
        ) { dialog = SettingsDialog.EVENT_REMINDER }
      }
      item {
        PickerRow(
          Icons.Outlined.Cake,
          stringResource(R.string.birthday_time),
          format.time(settings.birthdayTime)
        ) { dialog = SettingsDialog.BIRTHDAY_TIME }
      }

      item { Section(R.string.section_cycle) }
      item { CycleSettings(model, settings) }

      item { Section(R.string.section_data) }
      item {
        PickerRow(
          Icons.Outlined.FileDownload,
          stringResource(R.string.import_file),
          stringResource(R.string.import_hint)
        ) { importer.launch(arrayOf("*/*")) }
      }
      item {
        PickerRow(
          Icons.Outlined.FileUpload,
          stringResource(R.string.export_file),
          stringResource(R.string.export_hint)
        ) { dialog = SettingsDialog.EXPORT }
      }

      item { Section(R.string.section_alerts) }
      item {
        val state = if (model.notificationsOn) R.string.on else R.string.off_tap
        PickerRow(
          Icons.Outlined.Notifications,
          stringResource(R.string.notifications),
          stringResource(state)
        ) {
          val intent = Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
          context.startActivity(intent)
        }
      }
      if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        item {
          PickerRow(
            Icons.Outlined.AccessAlarm,
            stringResource(R.string.exact_alarms),
            stringResource(R.string.exact_alarms_off)
          ) {
            val action = SystemSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            val intent = Intent(action)
              .setData(Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
          }
        }
      }

      item { Section(R.string.about) }
      item {
        Text(
          stringResource(R.string.about_text, BuildConfig.VERSION_NAME),
          Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }

  val close = { dialog = null }
  when (dialog) {
    SettingsDialog.WEEK_START -> WeekStartDialog(model, settings, close)
    SettingsDialog.EVENT_REMINDER -> ReminderDialog(model, settings, close)
    SettingsDialog.BIRTHDAY_TIME ->
      TimeDialog(settings.birthdayTime, close) { time ->
        model.updateSettings { it.copy(birthdayTime = time) }
      }
    SettingsDialog.EXPORT ->
      ExportDialog(includeCycle, { includeCycle = it }, close) {
        exporter.launch("monthly-${LocalDate.now()}.ics")
      }
    null -> Unit
  }
}

@Composable
private fun Section(title: Int) {
  Text(
    stringResource(title),
    Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary
  )
}

@Composable
private fun CycleSettings(model: AppViewModel, settings: Settings) {
  val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
  Column {
    ListItem(
      headlineContent = { Text(stringResource(R.string.cycle_tracking)) },
      supportingContent = {
        Text(stringResource(R.string.cycle_tracking_hint))
      },
      leadingContent = { Icon(Icons.Outlined.WaterDrop, null) },
      trailingContent = {
        Switch(
          checked = settings.cycleEnabled,
          onCheckedChange = { on ->
            model.updateSettings { it.copy(cycleEnabled = on) }
          }
        )
      },
      colors = transparent
    )
    if (!settings.cycleEnabled) return@Column

    ListItem(
      headlineContent = { Text(stringResource(R.string.lead_days)) },
      trailingContent = {
        Stepper(settings.leadDays, 1..7, "${settings.leadDays}") { days ->
          model.updateSettings { it.copy(leadDays = days) }
        }
      },
      colors = transparent
    )
  }
}

@Composable
private fun WeekStartDialog(
  model: AppViewModel,
  settings: Settings,
  onDismiss: () -> Unit
) {
  val format = rememberFormat()
  val choices =
    listOf(null, DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
  val fallback = stringResource(R.string.locale_default)
  ChoiceDialog(R.string.week_starts, onDismiss) {
    choices.forEach { day ->
      val label = day?.let { format.dayName(it, TextStyle.FULL) } ?: fallback
      RadioRow(label, settings.weekStart == day, {
        model.updateSettings { it.copy(weekStart = day) }
        onDismiss()
      })
    }
  }
}

@Composable
private fun ReminderDialog(
  model: AppViewModel,
  settings: Settings,
  onDismiss: () -> Unit
) {
  val format = rememberFormat()
  val none = stringResource(R.string.none)
  val choices = listOf<Int?>(null) + REMINDER_PRESETS
  ChoiceDialog(R.string.default_reminder, onDismiss) {
    choices.forEach { minutes ->
      val label = minutes?.let { format.before(it) } ?: none
      RadioRow(label, settings.eventReminder == minutes, {
        model.updateSettings { it.copy(eventReminder = minutes) }
        onDismiss()
      })
    }
  }
}

@Composable
private fun ChoiceDialog(
  title: Int,
  onDismiss: () -> Unit,
  content: @Composable () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(title)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) { content() }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
  )
}

@Composable
private fun ExportDialog(
  includeCycle: Boolean,
  onIncludeCycle: (Boolean) -> Unit,
  onDismiss: () -> Unit,
  onExport: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.export_title)) },
    text = {
      Column {
        Row(
          Modifier.toggleable(
            value = includeCycle,
            role = Role.Checkbox,
            onValueChange = onIncludeCycle
          ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Checkbox(checked = includeCycle, onCheckedChange = null)
          Text(stringResource(R.string.include_cycle))
        }
        Text(
          stringResource(R.string.include_cycle_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onDismiss()
        onExport()
      }) {
        Text(stringResource(R.string.export_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
  )
}
