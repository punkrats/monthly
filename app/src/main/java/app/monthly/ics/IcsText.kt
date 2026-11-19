package app.monthly.ics

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** RFC 5545 lexical helpers: dates, durations, escaping, folding. */
internal object IcsText {

  private val dateFormat = DateTimeFormatter.ofPattern("uuuuMMdd")
  private val dateTimeFormat = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss")

  private val dayCodes = mapOf(
    DayOfWeek.MONDAY to "MO",
    DayOfWeek.TUESDAY to "TU",
    DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH",
    DayOfWeek.FRIDAY to "FR",
    DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU"
  )

  fun dayCode(day: DayOfWeek): String = dayCodes.getValue(day)

  fun dayOf(code: String): DayOfWeek? =
    dayCodes.entries.firstOrNull { it.value == code }?.key

  fun date(date: LocalDate): String = date.format(dateFormat)

  fun dateTime(dateTime: LocalDateTime): String =
    dateTime.format(dateTimeFormat)

  fun utc(dateTime: LocalDateTime): String = dateTime(dateTime) + "Z"

  fun parseDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.take(8), dateFormat) }.getOrNull()

  /** A DATE-TIME without its trailing Z; null when [text] is not one. */
  fun parseDateTime(text: String): LocalDateTime? {
    val core = text.removeSuffix("Z")
    if (core.length != 15) return null
    return runCatching { LocalDateTime.parse(core, dateTimeFormat) }.getOrNull()
  }

  fun isUtc(text: String) = text.endsWith("Z")

  fun utcInstant(text: String) =
    parseDateTime(text)?.toInstant(ZoneOffset.UTC)

  /** "-PT15M", "P1DT2H", "-P1W" into whole minutes. */
  fun parseDuration(text: String): Long? {
    val match = durationPattern.matchEntire(text.trim()) ?: return null
    val (sign, weeks, days, hours, minutes, seconds) = match.destructured
    val total = Duration.ofDays(7 * (weeks.toLongOrNull() ?: 0))
      .plusDays(days.toLongOrNull() ?: 0)
      .plusHours(hours.toLongOrNull() ?: 0)
      .plusMinutes(minutes.toLongOrNull() ?: 0)
      .plusSeconds(seconds.toLongOrNull() ?: 0)
      .toMinutes()
    return if (sign == "-") -total else total
  }

  private val durationPattern = Regex(
    "([+-]?)P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?"
  )

  fun formatDuration(minutes: Long): String {
    if (minutes == 0L) return "PT0S"

    val sign = if (minutes < 0) "-" else ""
    val total = Math.abs(minutes)
    val days = total / (24 * 60)
    val hours = total % (24 * 60) / 60
    val rest = total % 60
    val time = buildString {
      if (hours > 0) append("${hours}H")
      if (rest > 0) append("${rest}M")
    }
    val dayPart = if (days > 0) "${days}D" else ""
    val timePart = if (time.isEmpty()) "" else "T$time"
    return "${sign}P$dayPart$timePart"
  }

  fun escape(text: String): String = text
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")

  fun unescape(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
      val c = text[i]
      if (c != '\\' || i == text.length - 1) {
        out.append(c)
        i++
        continue
      }
      val next = text[i + 1]
      out.append(if (next == 'n' || next == 'N') '\n' else next)
      i += 2
    }
    return out.toString()
  }

  /** Splits a content line into 75-octet chunks without breaking UTF-8. */
  fun fold(line: String): String {
    val out = StringBuilder()
    var octets = 0
    var limit = 75
    var i = 0
    while (i < line.length) {
      val codePoint = line.codePointAt(i)
      val chars = Character.charCount(codePoint)
      val size = String(Character.toChars(codePoint)).toByteArray().size
      if (octets + size > limit) {
        out.append("\r\n ")
        octets = 0
        limit = 74
      }
      out.appendCodePoint(codePoint)
      octets += size
      i += chars
    }
    return out.toString()
  }

  fun unfold(text: String): List<String> {
    val lines = mutableListOf<String>()
    for (raw in text.split("\r\n", "\n")) {
      val continued = raw.startsWith(" ") || raw.startsWith("\t")
      if (continued && lines.isNotEmpty()) {
        lines[lines.lastIndex] = lines.last() + raw.drop(1)
        continue
      }
      if (raw.isNotEmpty()) lines += raw
    }
    return lines
  }
}
