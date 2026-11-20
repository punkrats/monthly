package app.monthly.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.monthly.MonthlyApp
import app.monthly.R
import app.monthly.ui.theme.MonthlyTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {

  @get:Rule
  val compose = createComposeRule()

  private val app: MonthlyApp = ApplicationProvider.getApplicationContext()
  private val format = Format(app)
  private val today = LocalDate.now()
  private lateinit var model: AppViewModel

  private fun show(vararg starts: LocalDate) {
    starts.forEach { app.repository.setCycleStart(it, true) }
    model = AppViewModel(app)
    compose.setContent { MonthlyTheme { MonthlyRoot(model) } }
  }

  private fun select(day: LocalDate) {
    compose.runOnIdle { model.selected = day }
    compose.waitForIdle()
  }

  private fun text(id: Int, vararg args: Any) = app.getString(id, *args)

  private fun dropButton(id: Int) =
    compose.onNodeWithContentDescription(text(id))

  private fun click(id: Int) = compose.onNodeWithText(text(id)).performClick()

  /** Writes run in the background; waits until the starts match. */
  private fun awaitStarts(vararg days: LocalDate) =
    compose.waitUntil(5_000) {
      app.repository.cycleStarts.value == days.toList()
    }

  private fun awaitText(text: String, substring: Boolean = false) =
    compose.waitUntilAtLeastOneExists(hasText(text, substring), 5_000)

  // Drop button

  @Test
  fun dropButtonMarksAndClearsTheSelectedDay() {
    show()

    dropButton(R.string.cycle_started).performClick()
    awaitStarts(today)
    compose.waitUntilAtLeastOneExists(
      hasContentDescription(text(R.string.cycle_remove)), 5_000
    )

    dropButton(R.string.cycle_remove).performClick()
    awaitStarts()
    compose.waitUntilAtLeastOneExists(
      hasContentDescription(text(R.string.cycle_started)), 5_000
    )
  }

  @Test
  fun dropButtonHidesOnFutureDays() {
    show()

    select(today.plusDays(1))
    dropButton(R.string.cycle_started).assertDoesNotExist()

    select(today)
    dropButton(R.string.cycle_started).assertIsDisplayed()
  }

  @Test
  fun dropButtonHidesWithCycleTrackingOff() {
    app.settings.update { it.copy(cycleEnabled = false) }
    show()

    dropButton(R.string.cycle_started).assertDoesNotExist()
    dropButton(R.string.new_event).assertIsDisplayed()
  }

  // Moving a start

  @Test
  fun markingNextToAStartMovesItWithUndo() {
    val start = today.minusDays(2)
    show(start)

    dropButton(R.string.cycle_started).performClick()
    val from = format.dayMonth(start)
    val to = format.dayMonth(today)
    compose.onNodeWithText(text(R.string.cycle_move_text, from, to))
      .assertIsDisplayed()

    click(R.string.cycle_move)
    awaitStarts(today)
    awaitText(text(R.string.cycle_moved, to))

    click(R.string.undo)
    awaitStarts(start)
  }

  @Test
  fun newCycleKeepsBothStarts() {
    val start = today.minusDays(2)
    show(start)

    dropButton(R.string.cycle_started).performClick()
    click(R.string.cycle_new)

    awaitStarts(start, today)
    compose.onNodeWithText(text(R.string.cycle_move_title))
      .assertDoesNotExist()
  }

  @Test
  fun cancelLeavesTheStartAlone() {
    val start = today.minusDays(2)
    show(start)

    dropButton(R.string.cycle_started).performClick()
    click(R.string.cancel)

    compose.onNodeWithText(text(R.string.cycle_move_title))
      .assertDoesNotExist()
    assertEquals(listOf(start), app.repository.cycleStarts.value)
    dropButton(R.string.cycle_started).assertIsDisplayed()
  }

  // Forecast

  @Test
  fun oneStartForecastsWithTheAssumedLength() {
    show(today)

    val next = format.dayMonth(today.plusDays(28))
    val basis = format.plural(R.plurals.cycle_assumed, 28)
    awaitText(text(R.string.cycle_next, next, basis), substring = true)
  }

  @Test
  fun recordedCyclesForecastWithTheirAverage() {
    show(today.minusDays(30), today.minusDays(1))

    val next = format.dayMonth(today.plusDays(28))
    val basis = format.plural(R.plurals.cycle_average, 29)
    awaitText(text(R.string.cycle_next, next, basis), substring = true)
  }

  @Test
  fun cellsTellRecordedAndExpectedStartsApart() {
    show(today)
    val recorded = "${format.weekday(today)}, ${text(R.string.cd_cycle_start)}"
    compose.waitUntilAtLeastOneExists(hasContentDescription(recorded), 5_000)

    val day = today.plusDays(28)
    select(day)
    val expected = "${format.weekday(day)}, ${text(R.string.cd_expected_start)}"
    compose.waitUntilAtLeastOneExists(hasContentDescription(expected), 5_000)
  }

  @Test
  fun cycleDayShowsUpToTodayOnly() {
    show(today.minusDays(3))
    awaitText(text(R.string.cycle_day, 4), substring = true)

    select(today.plusDays(1))
    compose.onNodeWithText(text(R.string.cycle_day, 5), substring = true)
      .assertDoesNotExist()
  }
}
