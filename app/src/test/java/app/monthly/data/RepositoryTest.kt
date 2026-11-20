package app.monthly.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.monthly.MonthlyApp
import app.monthly.date
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

  private val app: MonthlyApp = ApplicationProvider.getApplicationContext()
  private val repository = app.repository

  /** What a fresh process would read back from SQLite. */
  private fun stored() = Repository(app) {}.apply { load() }.cycleStarts.value

  private fun mark(vararg days: String) =
    days.forEach { repository.setCycleStart(date(it), true) }

  @Test
  fun moveCycleStartReplacesTheDate() {
    mark("2026-11-20")

    repository.moveCycleStart(date("2026-11-20"), date("2026-11-18"))

    assertEquals(listOf(date("2026-11-18")), repository.cycleStarts.value)
    assertEquals(listOf(date("2026-11-18")), stored())
  }

  @Test
  fun moveCycleStartKeepsTheOtherStarts() {
    mark("2026-10-23", "2026-11-20")

    repository.moveCycleStart(date("2026-11-20"), date("2026-11-22"))

    assertEquals(listOf(date("2026-10-23"), date("2026-11-22")), stored())
  }

  @Test
  fun moveCycleStartOntoAStartLeavesOneRow() {
    mark("2026-11-18", "2026-11-20")

    repository.moveCycleStart(date("2026-11-20"), date("2026-11-18"))

    assertEquals(listOf(date("2026-11-18")), stored())
  }

  @Test
  fun setCycleStartAddsAndRemoves() {
    mark("2026-11-20", "2026-11-20")
    assertEquals(listOf(date("2026-11-20")), stored())

    repository.setCycleStart(date("2026-11-20"), false)
    assertEquals(emptyList<Any>(), stored())
  }
}
