package app.monthly.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monthly.R
import app.monthly.logic.CycleForecast
import app.monthly.logic.Occurrence
import app.monthly.logic.Occurrences
import app.monthly.model.Event
import app.monthly.model.EventKind
import app.monthly.ui.theme.LocalCalendarColors
import app.monthly.ui.theme.Tokens
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.abs

private const val PAGES = 2401
private const val CENTER = 1200
private const val MAX_LABELS = 3
private val CELL_HEIGHT = 64.dp
private val CELL_SHAPE = RoundedCornerShape(8.dp)

/** Everything the month grid and day panel draw from, computed once. */
@Immutable
data class CalendarData(
  val events: List<Event>,
  val starts: Set<LocalDate>,
  val forecast: CycleForecast?,
  val cycleEnabled: Boolean,
  val leadDays: Int,
  val weekStart: DayOfWeek
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(model: AppViewModel) {
  val events by model.events.collectAsStateWithLifecycle()
  val cycle by model.cycleStarts.collectAsStateWithLifecycle()
  val settings by model.settings.collectAsStateWithLifecycle()
  val format = rememberFormat()
  val base = remember { YearMonth.now() }
  val selectedMonth = YearMonth.from(model.selected)
  val pager = rememberPagerState(pageOf(base, selectedMonth)) { PAGES }
  val visible = base.plusMonths(pager.currentPage - CENTER.toLong())

  val weekStart = settings.weekStart
    ?: WeekFields.of(format.locale).firstDayOfWeek
  val today = model.today
  val data = remember(events, cycle, settings, weekStart, today) {
    val until = today.plusYears(1)
    val forecast =
      if (settings.cycleEnabled) CycleForecast.from(cycle, until) else null
    CalendarData(
      events, cycle.toSet(), forecast, settings.cycleEnabled,
      settings.leadDays, weekStart
    )
  }

  LaunchedEffect(selectedMonth) {
    val target = pageOf(base, selectedMonth)
    if (abs(target - pager.currentPage) > 1) {
      pager.scrollToPage(target)
    } else {
      pager.animateScrollToPage(target)
    }
  }

  // Swiping to another month selects today there, else its first day.
  LaunchedEffect(pager) {
    snapshotFlow { pager.settledPage }.collect { page ->
      val month = base.plusMonths(page - CENTER.toLong())
      if (YearMonth.from(model.selected) == month) return@collect
      val today = model.today
      val inMonth = YearMonth.from(today) == month
      model.selected = if (inMonth) today else month.atDay(1)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            format.month(visible),
            Modifier.clickable { model.jumpTo(model.today) }
          )
        },
        actions = {
          IconButton(onClick = { model.jumpTo(model.today) }) {
            Icon(Icons.Outlined.Today, stringResource(R.string.today))
          }
          IconButton(onClick = { model.open(Screen.Agenda) }) {
            Icon(Icons.Outlined.Search, stringResource(R.string.search))
          }
          IconButton(onClick = { model.open(Screen.Settings) }) {
            Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
          }
        }
      )
    },

    // One full-width row puts the cycle button in the bottom-left corner
    // and the plus in the bottom-right, on the same baseline.
    floatingActionButton = {
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
      ) {
        if (data.cycleEnabled) CycleButton(model, data.starts)
        Spacer(Modifier.weight(1f))
        val editor = Screen.Editor(null, EventKind.EVENT, model.selected)
        FloatingActionButton(onClick = { model.open(editor) }) {
          Icon(Icons.Filled.Add, stringResource(R.string.new_event))
        }
      }
    },
    floatingActionButtonPosition = FabPosition.Center
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .fillMaxSize()
    ) {
      WeekHeader(data.weekStart, format)
      HorizontalPager(
        pager,
        beyondViewportPageCount = 1,
        verticalAlignment = Alignment.Top
      ) { page ->
        MonthGrid(model, data, base.plusMonths(page - CENTER.toLong()))
      }
      DayPanel(model, data, Modifier.weight(1f))
    }
  }

  model.pendingMove?.let { CycleMoveDialog(model, it) }
}

/**
 * Marks the selected day as a cycle start, or clears it when it is one;
 * the icon shows which. Hidden on days after today.
 */
@Composable
private fun CycleButton(model: AppViewModel, starts: Set<LocalDate>) {
  val day = model.selected
  val started = day in starts
  val icon =
    if (started) Icons.Filled.FormatColorReset else Icons.Filled.WaterDrop
  val label = if (started) R.string.cycle_remove else R.string.cycle_started

  AnimatedVisibility(
    day <= model.today,
    enter = scaleIn() + fadeIn(),
    exit = scaleOut() + fadeOut()
  ) {
    FloatingActionButton(
      onClick = { model.toggleCycleStart(day) },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      contentColor = LocalCalendarColors.current.cycle,
      elevation = FloatingActionButtonDefaults.loweredElevation()
    ) {
      Icon(icon, stringResource(label))
    }
  }
}

/** A start marked close to another: move that one, or begin a new cycle. */
@Composable
private fun CycleMoveDialog(model: AppViewModel, move: CycleMove) {
  val format = rememberFormat()
  val from = format.dayMonth(move.from)
  val to = format.dayMonth(move.to)
  val close = { model.pendingMove = null }

  AlertDialog(
    onDismissRequest = close,
    title = { Text(stringResource(R.string.cycle_move_title)) },
    text = { Text(stringResource(R.string.cycle_move_text, from, to)) },
    confirmButton = {
      TextButton(onClick = { model.moveCycleStart(move) }) {
        Text(stringResource(R.string.cycle_move))
      }
    },
    dismissButton = {
      Row {
        TextButton(onClick = close) {
          Text(stringResource(R.string.cancel))
        }
        TextButton(onClick = {
          close()
          model.setCycleStart(move.to, true)
        }) {
          Text(stringResource(R.string.cycle_new))
        }
      }
    }
  )
}

private fun pageOf(base: YearMonth, month: YearMonth) =
  CENTER + ChronoUnit.MONTHS.between(base, month).toInt()

@Composable
private fun WeekHeader(weekStart: DayOfWeek, format: Format) {
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp, vertical = 4.dp)
  ) {
    for (offset in 0L until 7L) {
      Text(
        format.dayName(weekStart.plus(offset)),
        Modifier.weight(1f),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun MonthGrid(
  model: AppViewModel,
  data: CalendarData,
  month: YearMonth
) {
  val first = month.atDay(1)
    .with(TemporalAdjusters.previousOrSame(data.weekStart))
  val byDay = remember(data.events, model.zone, first) {
    Occurrences.byDay(data.events, first, first.plusDays(41), model.zone)
  }
  val format = rememberFormat()

  Column(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp)
  ) {
    for (week in 0L until 6L) {
      Row(Modifier.fillMaxWidth()) {
        for (weekday in 0L until 7L) {
          val day = first.plusDays(week * 7 + weekday)
          DayCell(
            model, data, format, day, YearMonth.from(day) == month,
            byDay[day].orEmpty(), Modifier.weight(1f)
          )
        }
      }
    }
  }
}

@Composable
private fun DayCell(
  model: AppViewModel,
  data: CalendarData,
  format: Format,
  day: LocalDate,
  inMonth: Boolean,
  occurrences: List<Occurrence>,
  modifier: Modifier
) {
  val colors = LocalCalendarColors.current
  val scheme = MaterialTheme.colorScheme
  val strongest = data.leadDays + 1
  val level = data.forecast?.shade(day, data.leadDays) ?: 0
  val shade =
    if (level > 0) colors.cycleShade(level, strongest) else Color.Transparent
  val selected = day == model.selected
  val started = day in data.starts
  val expected = level == strongest
  val description =
    cellDescription(format, day, occurrences.size, started, expected)

  // Recorded starts show a filled drop; forecasts a hollow one and a
  // dashed ring.
  val drop = when {
    started -> Icons.Filled.WaterDrop
    expected -> Icons.Outlined.WaterDrop
    else -> null
  }
  val expectedRing =
    if (expected) Modifier.dashedBorder(colors.cycle) else Modifier
  val border = Modifier.border(2.dp, scheme.primary, CELL_SHAPE)
  val selectedRing = if (selected) border else Modifier

  Box(
    modifier
      .height(CELL_HEIGHT)
      .padding(1.dp)
      .clip(CELL_SHAPE)
      .background(shade)
      .then(expectedRing)
      .then(selectedRing)
      .clickable { model.selected = day }
      .semantics { contentDescription = description }
      .padding(3.dp)
  ) {
    val fade = if (inMonth) 1f else 0.45f
    Column(Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        DayNumber(day, day == model.today, fade)
        Spacer(Modifier.weight(1f))
        drop?.let {
          val tint = colors.cycle.copy(alpha = fade)
          Icon(it, null, Modifier.size(12.dp), tint = tint)
        }
      }

      val crowded = occurrences.size > MAX_LABELS
      val shown = if (crowded) MAX_LABELS - 1 else MAX_LABELS
      occurrences.take(shown).forEach { EventLabel(it, fade) }
      if (occurrences.size > shown) {
        Text(
          "+${occurrences.size - shown}",
          style = Tokens.cellLabel,
          color = scheme.onSurfaceVariant.copy(alpha = fade),
          modifier = Modifier.padding(start = 2.dp)
        )
      }
    }
  }
}

@Composable
private fun cellDescription(
  format: Format,
  day: LocalDate,
  count: Int,
  started: Boolean,
  expected: Boolean
): String {
  val parts = mutableListOf(format.weekday(day))
  if (count > 0) parts += pluralStringResource(R.plurals.entries, count, count)
  if (started) parts += stringResource(R.string.cd_cycle_start)
  if (expected) parts += stringResource(R.string.cd_expected_start)
  return parts.joinToString(", ")
}

@Composable
private fun DayNumber(day: LocalDate, today: Boolean, fade: Float) {
  val scheme = MaterialTheme.colorScheme
  val text = "${day.dayOfMonth}"
  if (!today) {
    Text(
      text,
      style = Tokens.cellDay,
      color = scheme.onSurface.copy(alpha = fade),
      modifier = Modifier.padding(start = 3.dp)
    )
    return
  }

  Box(
    Modifier
      .size(18.dp)
      .clip(CircleShape)
      .background(scheme.primary),
    contentAlignment = Alignment.Center
  ) {
    Text(text, style = Tokens.cellDay, color = scheme.onPrimary)
  }
}

@Composable
private fun EventLabel(occurrence: Occurrence, fade: Float) {
  val event = occurrence.event
  val color = LocalCalendarColors.current.of(event.color)
  val title = event.title.ifBlank { stringResource(R.string.untitled) }
  Row(
    Modifier
      .fillMaxWidth()
      .padding(top = 1.dp)
      .clip(RoundedCornerShape(3.dp))
      .background(color.copy(alpha = Tokens.EVENT_TINT * fade))
      .padding(horizontal = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(1.dp)
  ) {
    if (event.isBirthday) {
      Icon(Icons.Outlined.Cake, null, Modifier.size(10.dp), tint = color)
    }
    Text(
      title,
      style = Tokens.cellLabel,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = fade),
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Clip
    )
  }
}

/** Marks the expected cycle start: a dashed ring inside the cell. */
private fun Modifier.dashedBorder(color: Color) = drawBehind {
  val width = 1.5.dp.toPx()
  val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
  drawRoundRect(
    color = color,
    topLeft = Offset(width / 2, width / 2),
    size = Size(size.width - width, size.height - width),
    cornerRadius = CornerRadius(8.dp.toPx()),
    style = Stroke(width = width, pathEffect = dash)
  )
}

@Composable
internal fun ColorBar(color: Color) {
  Box(
    Modifier
      .width(4.dp)
      .height(36.dp)
      .clip(RoundedCornerShape(2.dp))
      .background(color)
  )
}
