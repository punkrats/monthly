package app.monthly.logic

import app.monthly.date
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CycleForecastTest {

  private val farAhead = date("2027-12-31")

  private fun forecast(vararg starts: String, until: LocalDate = farAhead) =
    CycleForecast.from(starts.map(::date), until)

  @Test
  fun needsAtLeastOneStart() {
    assertNull(CycleForecast.from(emptyList(), farAhead))
  }

  @Test
  fun oneStartAssumesTheDefaultLength() {
    val forecast = forecast("2026-01-01", until = date("2026-03-31"))

    assertEquals(28, forecast?.averageLength)
    assertTrue(forecast!!.assumed)
    assertEquals(
      listOf(date("2026-01-29"), date("2026-02-26"), date("2026-03-26")),
      forecast.expected
    )
  }

  @Test
  fun oneCycleProjectsItsLength() {
    val forecast = forecast("2026-01-01", "2026-01-30")

    assertEquals(29, forecast?.averageLength)
    assertFalse(forecast!!.assumed)
    assertEquals(date("2026-02-28"), forecast.expected.first())
  }

  @Test
  fun averagesOnlyTheLastThreeCyclesAndRounds() {
    val forecast = forecast(
      "2026-01-01", // 20
      "2026-01-21", // 40
      "2026-03-02", // 28
      "2026-03-30", // 29
      "2026-04-28", // 29
      "2026-05-27"
    )

    assertEquals(29, forecast?.averageLength)
    assertEquals(date("2026-06-25"), forecast?.expected?.first())
  }

  @Test
  fun ignoresOrderAndDuplicates() {
    val forecast = forecast("2026-01-30", "2026-01-01", "2026-01-30")

    assertEquals(29, forecast?.averageLength)
    assertFalse(forecast!!.assumed)
  }

  @Test
  fun projectsUntilTheGivenDayInclusive() {
    val starts = arrayOf("2026-01-01", "2026-01-29")

    assertEquals(
      listOf(date("2026-02-26"), date("2026-03-26"), date("2026-04-23")),
      forecast(*starts, until = date("2026-04-23"))?.expected
    )
    assertEquals(
      listOf(date("2026-02-26"), date("2026-03-26")),
      forecast(*starts, until = date("2026-04-22"))?.expected
    )
  }

  @Test
  fun alwaysProjectsTheNextStart() {
    val forecast = forecast("2026-01-01", until = date("2026-01-10"))

    assertEquals(listOf(date("2026-01-29")), forecast?.expected)
  }

  @Test
  fun keepsProjectingPastUnrecordedStarts() {
    val forecast = forecast(
      "2026-01-01", "2026-01-29", until = date("2026-06-30")
    )

    assertEquals(5, forecast?.expected?.size)
    assertEquals(date("2026-06-18"), forecast?.expected?.last())
  }

  @Test
  fun shadeRampsUpTowardsTheExpectedDay() {
    val forecast = forecast(
      "2026-01-01", "2026-01-29", until = date("2026-02-26")
    )!!

    val levels = (21..27).map { day ->
      forecast.shade(date("2026-02-%02d".format(day)), leadDays = 3)
    }

    assertEquals(listOf(0, 0, 1, 2, 3, 4, 0), levels)
  }

  @Test
  fun skipsTheGapOfAForgottenStart() {
    val forecast = forecast(
      "2026-08-05", // 28
      "2026-09-02", // 60, one start was never recorded
      "2026-11-01", // 28
      "2026-11-29"
    )

    assertEquals(28, forecast?.averageLength)
    assertEquals(date("2026-12-27"), forecast?.expected?.first())
  }

  @Test
  fun skipsADoubledTap() {
    val forecast = forecast(
      "2026-01-01", // 28
      "2026-01-29", // 1, tapped again the next day
      "2026-01-30", // 27
      "2026-02-26"
    )

    assertEquals(28, forecast?.averageLength)
  }

  @Test
  fun withTwoCyclesTheShorterOneSetsTheTypicalLength() {
    val forecast = forecast(
      "2026-01-01", // 28
      "2026-01-29", // 60
      "2026-03-30"
    )

    assertEquals(28, forecast?.averageLength)
  }

  @Test
  fun keepsAGenuinelyLateCycle() {
    val forecast = forecast(
      "2026-01-01", // 28
      "2026-01-29", // 28
      "2026-02-26", // 40
      "2026-04-07"
    )

    assertEquals(32, forecast?.averageLength)
  }

  @Test
  fun keepsConsistentlyLongCycles() {
    val forecast = forecast(
      "2026-01-01", // 40
      "2026-02-10", // 38
      "2026-03-20", // 41
      "2026-04-30", // 80
      "2026-07-19"
    )

    assertEquals(40, forecast?.averageLength)
  }

  @Test
  fun doubledTapsAloneAssumeTheDefaultFromTheLastTap() {
    val forecast = forecast("2026-01-01", "2026-01-02")

    assertEquals(28, forecast?.averageLength)
    assertTrue(forecast!!.assumed)
    assertEquals(date("2026-01-30"), forecast.expected.first())
  }

  @Test
  fun nearbyStartFindsAStartLessThanACycleAway() {
    val starts = listOf(date("2026-11-20"))

    assertEquals(date("2026-11-20"), nearbyStart(starts, date("2026-11-22")))
    assertEquals(date("2026-11-20"), nearbyStart(starts, date("2026-11-06")))
    assertEquals(date("2026-11-20"), nearbyStart(starts, date("2026-12-04")))
    assertNull(nearbyStart(starts, date("2026-11-05")))
    assertNull(nearbyStart(starts, date("2026-12-05")))
  }

  @Test
  fun nearbyStartIgnoresTheDayItself() {
    val starts = listOf(date("2026-11-20"))

    assertNull(nearbyStart(starts, date("2026-11-20")))
    assertNull(nearbyStart(emptyList(), date("2026-11-20")))
  }

  @Test
  fun nearbyStartPrefersTheCloserAndThenTheLaterStart() {
    val starts = listOf(date("2026-11-01"), date("2026-11-11"))

    assertEquals(date("2026-11-01"), nearbyStart(starts, date("2026-11-04")))
    assertEquals(date("2026-11-11"), nearbyStart(starts, date("2026-11-06")))
  }

  @Test
  fun cycleDayCountsFromTheLatestStartBeforeTheDay() {
    val starts = listOf(date("2026-01-01"), date("2026-01-29"))

    assertNull(cycleDayOf(starts, date("2025-12-31")))
    assertEquals(1, cycleDayOf(starts, date("2026-01-01")))
    assertEquals(28, cycleDayOf(starts, date("2026-01-28")))
    assertEquals(1, cycleDayOf(starts, date("2026-01-29")))
    assertEquals(60, cycleDayOf(starts, date("2026-03-29")))
    assertNull(cycleDayOf(starts, date("2026-03-30")))
  }
}
