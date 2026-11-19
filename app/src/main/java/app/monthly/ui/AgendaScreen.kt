package app.monthly.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monthly.R
import app.monthly.logic.Occurrence
import app.monthly.logic.Occurrences
import app.monthly.logic.datesBetween
import app.monthly.model.Event
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val UPCOMING_DAYS = 120L

/** Upcoming entries, or search results when a query is typed. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AgendaScreen(model: AppViewModel) {
  val events by model.events.collectAsStateWithLifecycle()
  val format = rememberFormat()
  var query by rememberSaveable { mutableStateOf("") }
  val groups = remember(events, query, model.zone, model.today) {
    agenda(events, query, model.today, model.zone)
  }
  val transparent = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent
  )

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
        title = {
          TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_events)) },
            singleLine = true,
            colors = transparent,
            modifier = Modifier.fillMaxWidth()
          )
        }
      )
    }
  ) { padding ->
    LazyColumn(contentPadding = padding) {
      if (groups.isEmpty()) {
        item {
          Text(
            stringResource(R.string.nothing_found),
            Modifier.padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      groups.forEach { (day, occurrences) ->
        stickyHeader(key = "h$day") { DayHeading(day, format, model.today) }
        items(occurrences, key = { "$day/${it.event.uid}/${it.date}" }) {
          OccurrenceRow(model, it, day, format)
        }
      }
    }
  }
}

@Composable
private fun DayHeading(day: LocalDate, format: Format, today: LocalDate) {
  val near = Math.abs(ChronoUnit.DAYS.between(today, day)) <= 1
  val year = if (day.year == today.year) "" else ", ${day.year}"
  val text = if (near) {
    "${format.relativeDay(day, today)} · ${format.date(day)}"
  } else {
    format.weekday(day) + year
  }
  Surface(color = MaterialTheme.colorScheme.surface) {
    Text(
      text,
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary
    )
  }
}

/**
 * Without a query: every day of the next months. With one: each matching
 * event once, at its next occurrence (or its latest past one).
 */
private fun agenda(
  events: List<Event>,
  query: String,
  today: LocalDate,
  zone: ZoneId
): List<Pair<LocalDate, List<Occurrence>>> {
  val text = query.trim()
  if (text.isEmpty()) {
    val end = today.plusDays(UPCOMING_DAYS)
    return Occurrences.byDay(events, today, end, zone).toSortedMap().toList()
  }

  val found = events
    .filter { event ->
      listOf(event.title, event.location, event.notes)
        .any { it.contains(text, ignoreCase = true) }
    }
    .mapNotNull { nearest(it, today, zone) }
  val upcoming = found.filter { it.firstDay >= today }.sortedBy { it.firstDay }
  val past = found
    .filter { it.firstDay < today }
    .sortedByDescending { it.firstDay }
  return (upcoming + past)
    .groupBy { it.firstDay }
    .toList()
}

private fun nearest(event: Event, today: LocalDate, zone: ZoneId): Occurrence? {
  val next = event.datesBetween(today, today.plusYears(5)).firstOrNull()
  val date = next
    ?: event.datesBetween(today.minusYears(20), today).lastOrNull()
    ?: return null
  return Occurrences.of(event, date, zone)
}
