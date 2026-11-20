package app.monthly.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shorter gaps between starts come from a doubled tap. */
private const val MIN_CYCLE_DAYS = 15

/**
 * Expected cycle starts, spaced by the rounded average of the most recent
 * regular cycle lengths and counted from the last recorded start. Without
 * a regular cycle yet, [DEFAULT_LENGTH] is [assumed].
 */
data class CycleForecast(
  val averageLength: Int,
  val assumed: Boolean,
  val expected: List<LocalDate>
) {

  /**
   * 0 for no shade. The [leadDays] days before an expected start ramp from
   * 1 (light) to [leadDays] (strong); the expected day itself is
   * `leadDays + 1`.
   */
  fun shade(day: LocalDate, leadDays: Int): Int =
    expected.maxOfOrNull { level(day, it, leadDays) } ?: 0

  private fun level(day: LocalDate, start: LocalDate, leadDays: Int): Int {
    val before = ChronoUnit.DAYS.between(day, start)
    return if (before in 0..leadDays) (leadDays + 1 - before).toInt() else 0
  }

  companion object {

    /** Cycles the typical length is taken from. */
    private const val TYPICAL_WINDOW = 12

    /** Cycle length assumed until one regular cycle is recorded. */
    const val DEFAULT_LENGTH = 28

    /**
     * Expected starts up to [until], inclusive, and at least the next one.
     * Null without any recorded start.
     */
    fun from(
      starts: Collection<LocalDate>,
      until: LocalDate,
      window: Int = 3
    ): CycleForecast? {
      val sorted = starts.toSortedSet().toList()
      val last = sorted.lastOrNull() ?: return null
      val lengths = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }
      val recent = regular(lengths).takeLast(window)

      val assumed = recent.isEmpty()
      val average =
        if (assumed) DEFAULT_LENGTH else recent.average().roundToInt()
      val span = ChronoUnit.DAYS.between(last, until) / average
      val expected = (1..maxOf(1, span)).map { last.plusDays(average * it) }
      return CycleForecast(average, assumed, expected)
    }

    /**
     * Lengths within 2/3 to 3/2 of the typical one; a forgotten start
     * doubles a cycle and falls outside. Typical is the lower median of
     * the recent lengths, so of two cycles the shorter one wins.
     */
    private fun regular(lengths: List<Long>): List<Long> {
      val plausible = lengths.filter { it >= MIN_CYCLE_DAYS }
      if (plausible.isEmpty()) return emptyList()

      val ordered = plausible.takeLast(TYPICAL_WINDOW).sorted()
      val typical = ordered[(ordered.size - 1) / 2].toDouble()
      val range = typical * 2 / 3..typical * 3 / 2
      return plausible.filter { it.toDouble() in range }
    }
  }
}

/**
 * 1-based day within the cycle containing [day]: counted from the latest
 * recorded start on or before it. Null before the first start or when the
 * gap exceeds [maxDays] (a start was probably not recorded).
 */
fun cycleDayOf(
  starts: Collection<LocalDate>,
  day: LocalDate,
  maxDays: Int = 60
): Int? {
  val start = starts.filter { it <= day }.maxOrNull() ?: return null
  val number = ChronoUnit.DAYS.between(start, day).toInt() + 1
  return number.takeIf { it <= maxDays }
}

/**
 * The recorded start closest to [day] and fewer than [MIN_CYCLE_DAYS] away,
 * the later one on a tie. A new start that close would make a cycle too
 * short to count, so the user may rather mean to move this one. Null when
 * [day] is a start itself.
 */
fun nearbyStart(starts: Collection<LocalDate>, day: LocalDate): LocalDate? {
  if (day in starts) return null

  val gap = { start: LocalDate -> abs(ChronoUnit.DAYS.between(start, day)) }
  return starts
    .filter { gap(it) < MIN_CYCLE_DAYS }
    .sortedDescending()
    .minByOrNull(gap)
}
