package app.monthly.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.monthly.R
import app.monthly.ics.IcsImport

@Composable
fun MonthlyRoot(model: AppViewModel) {
  val snackbar = remember { SnackbarHostState() }
  val undoLabel = stringResource(R.string.undo)

  BackHandler(enabled = model.screens.size > 1) { model.back() }
  AskForNotifications(model)

  Box(Modifier.fillMaxSize()) {
    when (val screen = model.screens.last()) {
      Screen.Calendar -> CalendarScreen(model)
      Screen.Agenda -> AgendaScreen(model)
      Screen.Settings -> SettingsScreen(model)
      is Screen.Editor -> EditorScreen(model, screen)
    }

    SnackbarHost(
      snackbar,
      Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 72.dp)
    )
  }

  model.detail?.let { EventSheet(model, it) }
  model.pendingImport?.let { ImportDialog(model, it) }

  LaunchedEffect(model.notice) {
    val notice = model.notice ?: return@LaunchedEffect
    val result = snackbar.showSnackbar(
      notice.text,
      actionLabel = if (notice.undo != null) undoLabel else null,
      duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) notice.undo?.invoke()
    model.notice = null
  }
}

/** Asks once per process start until granted; denial shows a banner. */
@Composable
private fun AskForNotifications(model: AppViewModel) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

  var asked by rememberSaveable { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { model.resume() }
  val context = LocalContext.current
  LaunchedEffect(Unit) {
    val enabled = NotificationManagerCompat.from(context)
      .areNotificationsEnabled()
    if (asked || enabled) return@LaunchedEffect
    asked = true
    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }
}

@Composable
private fun ImportDialog(model: AppViewModel, result: IcsImport) {
  val format = rememberFormat()
  val events = format.plural(R.plurals.import_events, result.events.size)
  val cycle = result.cycleStarts.size
  val notes = buildList {
    add(events)
    if (cycle > 0) add(format.plural(R.plurals.import_cycle, cycle))
    if (result.simplified > 0) {
      add(format.plural(R.plurals.import_simplified, result.simplified))
    }
    if (result.unknownZones > 0) {
      add(format.plural(R.plurals.import_unknown_zones, result.unknownZones))
    }
  }

  AlertDialog(
    onDismissRequest = { model.pendingImport = null },
    title = { Text(stringResource(R.string.import_title)) },
    text = {
      Column { notes.forEach { Text(it) } }
    },
    confirmButton = {
      TextButton(onClick = { model.confirmImport() }) {
        Text(stringResource(R.string.import_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = { model.pendingImport = null }) {
        Text(stringResource(R.string.cancel))
      }
    }
  )
}
