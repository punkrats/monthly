package app.monthly.logic

import app.monthly.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CycleForecastTest {

  @Test
  fun needsAtLeastOneCompleteCycle() {
    assertNull(CycleForecast.from(emptyList()))
    assertNull(CycleForecast.from(listOf(date("2026-01-01"))))
  }

  @Test
  fun oneCycleProjectsItsLength() {
    val forecast = CycleForecast.from(
      listOf(date("2026-01-01"), date("2026-01-29"))
    )

    assertEquals(28, forecast?.averageLength)
    assertEquals(date("2026-02-26"), forecast?.expected?.first())
  }

  @Test
  fun averagesOnlyTheLastThreeCyclesAndRounds() {
    val starts = listOf(
      date("2026-01-01"), // 20
      date("2026-01-21"), // 40
      date("2026-03-02"), // 28
      date("2026-03-30"), // 29
      date("2026-04-28"), // 29
      date("2026-05-27")
    )

    val forecast = CycleForecast.from(starts)

    assertEquals(29, forecast?.averageLength)
    assertEquals(date("2026-06-25"), forecast?.expected?.first())
  }

  @Test
  fun ignoresOrderAndDuplicates() {
    val starts = listOf(
      date("2026-01-29"), date("2026-01-01"), date("2026-01-29")
    )

    assertEquals(28, CycleForecast.from(starts)?.averageLength)
  }

  @Test
  fun projectsSeveralCyclesAhead() {
    val forecast = CycleForecast.from(
      listOf(date("2026-01-01"), date("2026-01-29")), horizon = 3
    )

    assertEquals(
      listOf(date("2026-02-26"), date("2026-03-26"), date("2026-04-23")),
      forecast?.expected
    )
  }

  @Test
  fun shadeRampsUpTowardsTheExpectedDay() {
    val forecast = CycleForecast.from(
      listOf(date("2026-01-01"), date("2026-01-29")), horizon = 1
    )!!

    val levels = (21..27).map { day ->
      forecast.shade(date("2026-02-%02d".format(day)), leadDays = 3)
    }

    assertEquals(listOf(0, 0, 1, 2, 3, 4, 0), levels)
  }

  @Test
  fun skipsTheGapOfAForgottenStart() {
    val starts = listOf(
      date("2026-08-05"), // 28
      date("2026-09-02"), // 60, one start was never recorded
      date("2026-11-01"), // 28
      date("2026-11-29")
    )

    val forecast = CycleForecast.from(starts)

    assertEquals(28, forecast?.averageLength)
    assertEquals(date("2026-12-27"), forecast?.expected?.first())
  }

  @Test
  fun skipsADoubledTap() {
    val starts = listOf(
      date("2026-01-01"), // 28
      date("2026-01-29"), // 1, tapped again the next day
      date("2026-01-30"), // 27
      date("2026-02-26")
    )

    assertEquals(28, CycleForecast.from(starts)?.averageLength)
  }

  @Test
  fun withTwoCyclesTheShorterOneSetsTheTypicalLength() {
    val starts = listOf(
      date("2026-01-01"), // 28
      date("2026-01-29"), // 60
      date("2026-03-30")
    )

    assertEquals(28, CycleForecast.from(starts)?.averageLength)
  }

  @Test
  fun keepsAGenuinelyLateCycle() {
    val starts = listOf(
      date("2026-01-01"), // 28
      date("2026-01-29"), // 28
      date("2026-02-26"), // 40
      date("2026-04-07")
    )

    assertEquals(32, CycleForecast.from(starts)?.averageLength)
  }

  @Test
  fun keepsConsistentlyLongCycles() {
    val starts = listOf(
      date("2026-01-01"), // 40
      date("2026-02-10"), // 38
      date("2026-03-20"), // 41
      date("2026-04-30"), // 80
      date("2026-07-19")
    )

    assertEquals(40, CycleForecast.from(starts)?.averageLength)
  }

  @Test
  fun doubledTapsAloneGiveNoForecast() {
    val starts = listOf(date("2026-01-01"), date("2026-01-02"))

    assertNull(CycleForecast.from(starts))
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
