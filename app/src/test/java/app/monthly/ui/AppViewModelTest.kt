package app.monthly.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.monthly.MonthlyApp
import app.monthly.R
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppViewModelTest {

  private val app: MonthlyApp = ApplicationProvider.getApplicationContext()
  private val today = LocalDate.now()
  private val model = AppViewModel(app)

  private fun mark(vararg days: LocalDate) =
    days.forEach { app.repository.setCycleStart(it, true) }

  /** Writes run in the background; waits until the starts match. */
  private fun awaitStarts(vararg days: LocalDate) = runBlocking {
    withTimeout(5_000) { model.cycleStarts.first { it == days.toList() } }
  }

  private fun text(id: Int, day: LocalDate) =
    app.getString(id, Format(app).dayMonth(day))

  @Test
  fun togglingAFreeDayMarksItWithUndo() {
    model.toggleCycleStart(today)

    awaitStarts(today)
    assertEquals(text(R.string.cycle_saved, today), model.notice?.text)

    model.notice?.undo?.invoke()
    awaitStarts()
  }

  @Test
  fun togglingAStartClearsItWithUndo() {
    mark(today)

    model.toggleCycleStart(today)

    awaitStarts()
    assertEquals(text(R.string.cycle_removed, today), model.notice?.text)

    model.notice?.undo?.invoke()
    awaitStarts(today)
  }

  @Test
  fun togglingNextToAStartAsksToMoveIt() {
    val start = today.minusDays(2)
    mark(start)

    model.toggleCycleStart(today)

    assertEquals(CycleMove(start, today), model.pendingMove)
    assertNull(model.notice)
    assertEquals(listOf(start), model.cycleStarts.value)
  }

  @Test
  fun togglingAFullCycleAfterAStartMarksWithoutAsking() {
    val start = today.minusDays(28)
    mark(start)

    model.toggleCycleStart(today)

    assertNull(model.pendingMove)
    awaitStarts(start, today)
  }

  @Test
  fun movingAStartAndUndoingMovesItBack() {
    val start = today.minusDays(2)
    mark(start)
    model.toggleCycleStart(today)

    model.moveCycleStart(model.pendingMove!!)

    assertNull(model.pendingMove)
    awaitStarts(today)
    assertEquals(text(R.string.cycle_moved, today), model.notice?.text)

    model.notice?.undo?.invoke()
    awaitStarts(start)
  }
}
