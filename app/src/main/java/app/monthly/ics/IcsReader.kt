package app.monthly.ics

import app.monthly.model.Event
import app.monthly.model.EventColor
import app.monthly.model.EventKind
import app.monthly.model.Freq
import app.monthly.model.Recurrence
import app.monthly.model.Reminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * [simplified]: events whose repeat rule could not be represented and were
 * imported as single events. [unknownZones]: distinct TZIDs that fell back
 * to the device zone.
 */
data class IcsImport(
  val events: List<Event>,
  val cycleStarts: List<LocalDate>,
  val simplified: Int,
  val unknownZones: Int
)

object IcsReader {

  fun read(
    text: String,
    device: ZoneId,
    newUid: () -> String = { UUID.randomUUID().toString() }
  ): IcsImport {
    val root = parse(text)
    val components = root.descendants()
    val aliases = components
      .filter { it.name == "VTIMEZONE" }
      .mapNotNull { zone ->
        val id = zone.value("TZID") ?: return@mapNotNull null
        val location = zone.value("X-LIC-LOCATION") ?: return@mapNotNull null
        zoneOrNull(location)?.let { id to it }
      }
      .toMap()

    val mapper = EventMapper(device, aliases, newUid)
    val parsed = mutableListOf<Parsed>()
    val cycle = mutableListOf<LocalDate>()
    for (component in components.filter { it.name == "VEVENT" }) {
      if (component.value(IcsWriter.KIND) == IcsWriter.KIND_CYCLE) {
        component.first("DTSTART")?.let { IcsText.parseDate(it.value) }
          ?.let { cycle += it }
        continue
      }
      mapper.map(component)?.let { parsed += it }
    }

    return IcsImport(
      events = applyOverrides(parsed),
      cycleStarts = cycle.distinct().sorted(),
      simplified = mapper.simplified,
      unknownZones = mapper.unknownZones.size
    )
  }

  /**
   * A RECURRENCE-ID entry replaces one instance: it becomes its own event
   * and the master gets an exception for that date.
   */
  private fun applyOverrides(parsed: List<Parsed>): List<Event> {
    val masters = parsed
      .filter { it.overrides == null }
      .associateBy { it.event.uid }
      .mapValues { it.value.event }
      .toMutableMap()
    val singles = mutableListOf<Event>()

    for (item in parsed) {
      val date = item.overrides ?: continue
      val uid = item.event.uid
      masters[uid]?.let {
        masters[uid] = it.copy(exceptions = it.exceptions + date)
      }
      singles += item.event.copy(uid = "$uid/${IcsText.date(date)}")
    }

    val ordered = parsed
      .filter { it.overrides == null }
      .map { it.event.uid }
      .distinct()
      .mapNotNull { masters[it] }
    return ordered + singles
  }

  private class Parsed(val event: Event, val overrides: LocalDate?)

  private class EventMapper(
    private val device: ZoneId,
    private val aliases: Map<String, ZoneId>,
    private val newUid: () -> String
  ) {

    var simplified = 0
    val unknownZones = mutableSetOf<String>()

    fun map(c: Component): Parsed? {
      if (c.value("STATUS").equals("CANCELLED", ignoreCase = true)) return null
      val start = c.first("DTSTART") ?: return null
      val event = if (isDate(start)) allDay(c, start) else timed(c, start)
      event ?: return null

      val overrides = c.first("RECURRENCE-ID")
        ?.let { dateIn(it, it.value, event.zoneOr(device)) }
      return Parsed(event, overrides)
    }

    private fun allDay(c: Component, start: Prop): Event? {
      val startDate = IcsText.parseDate(start.value) ?: return null
      val endDate = c.first("DTEND")
        ?.let { IcsText.parseDate(it.value)?.minusDays(1) }
        ?: c.value("DURATION")?.let { duration ->
          val days = (IcsText.parseDuration(duration) ?: 0) / 1440
          startDate.plusDays((days - 1).coerceAtLeast(0))
        }
        ?: startDate
      val zone = c.value(IcsWriter.ZONE)?.let { zoneOrNull(it) }
      val event = common(c, startDate, zone).copy(
        allDay = true,
        endDate = maxOf(endDate, startDate),
        reminders = alarms(c, allDay = true)
      )
      return asBirthdayIfTagged(c, event)
    }

    private fun timed(c: Component, start: Prop): Event? {
      val local = IcsText.parseDateTime(start.value) ?: return null
      val zone = zoneOf(start)
      val end = endOf(c, local, zone)
      return common(c, local.toLocalDate(), zone).copy(
        allDay = false,
        startTime = local.toLocalTime(),
        endDate = end.toLocalDate(),
        endTime = end.toLocalTime(),
        reminders = alarms(c, allDay = false)
      )
    }

    private fun common(c: Component, startDate: LocalDate, zone: ZoneId?) =
      Event(
        uid = c.value("UID")?.let { IcsText.unescape(it) } ?: newUid(),
        title = IcsText.unescape(c.value("SUMMARY").orEmpty()).trim(),
        allDay = false,
        startDate = startDate,
        zone = zone,
        location = IcsText.unescape(c.value("LOCATION").orEmpty()).trim(),
        notes = IcsText.unescape(c.value("DESCRIPTION").orEmpty()).trim(),
        color = EventColor.fromCss(c.value("COLOR")) ?: EventColor.BLUE,
        recurrence = recurrence(c, startDate, zone),
        exceptions = exceptions(c, zone ?: device),
        birthYear = c.value(IcsWriter.BIRTH_YEAR)?.toIntOrNull()
      )

    private fun asBirthdayIfTagged(c: Component, event: Event): Event {
      val categories = c.all("CATEGORIES")
        .flatMap { it.value.split(',') }
        .map { it.trim().uppercase() }
      val tagged = c.value(IcsWriter.KIND) == IcsWriter.KIND_BIRTHDAY ||
        "BIRTHDAY" in categories
      if (!tagged) return event

      val yearly = event.recurrence ?: Recurrence(Freq.YEARLY)
      val color = if (c.value("COLOR") == null) EventColor.PINK else event.color
      return event.copy(
        kind = EventKind.BIRTHDAY, recurrence = yearly, color = color
      )
    }

    private fun endOf(
      c: Component,
      start: LocalDateTime,
      zone: ZoneId?
    ): LocalDateTime {
      val endProp = c.first("DTEND")
      if (endProp != null) {
        val local = IcsText.parseDateTime(endProp.value) ?: return start
        val endZone = zoneOf(endProp)
        if (zone == null || endZone == null || endZone == zone) return local
        return local.atZone(endZone).withZoneSameInstant(zone).toLocalDateTime()
      }

      val minutes = c.value("DURATION")?.let { IcsText.parseDuration(it) }
      return start.plusMinutes(minutes ?: 0)
    }

    private fun recurrence(
      c: Component,
      start: LocalDate,
      zone: ZoneId?
    ): Recurrence? {
      val text = c.value("RRULE") ?: return null
      val rule = RRule.parse(text, start, zone ?: device)
      if (rule == null) simplified++
      return rule
    }

    private fun exceptions(c: Component, zone: ZoneId): Set<LocalDate> =
      c.all("EXDATE")
        .flatMap { prop ->
          prop.value.split(',').mapNotNull { dateIn(prop, it.trim(), zone) }
        }
        .toSet()

    /** The calendar date of a DATE or DATE-TIME value, seen in [zone]. */
    private fun dateIn(prop: Prop, value: String, zone: ZoneId): LocalDate? {
      if (value.length == 8) return IcsText.parseDate(value)

      val local = IcsText.parseDateTime(value) ?: return null
      val from = zoneOf(prop.copy(value = value)) ?: return local.toLocalDate()
      return local.atZone(from).withZoneSameInstant(zone).toLocalDate()
    }

    private fun alarms(c: Component, allDay: Boolean): List<Reminder> =
      c.children
        .filter { it.name == "VALARM" }
        .mapNotNull { alarm ->
          val trigger = alarm.first("TRIGGER") ?: return@mapNotNull null
          if (trigger.params["VALUE"] == "DATE-TIME") return@mapNotNull null
          val minutes = IcsText.parseDuration(trigger.value)
            ?: return@mapNotNull null
          if (!allDay) return@mapNotNull Reminder.Before((-minutes).toInt())

          val days = -Math.floorDiv(minutes, 1440L)
          val clock = Math.floorMod(minutes, 1440L)
          val time = LocalTime.MIDNIGHT.plusMinutes(clock)
          val theirs = alarm.value(IcsWriter.CLOCK) != "MINE"
          Reminder.OnDay(days.toInt(), time, theirs)
        }
        .distinct()

    private fun isDate(prop: Prop) =
      prop.params["VALUE"] == "DATE" || prop.value.length == 8

    /** Null means floating local time. */
    private fun zoneOf(prop: Prop): ZoneId? {
      if (IcsText.isUtc(prop.value)) return ZoneId.of("UTC")
      val tzid = prop.params["TZID"] ?: return null
      aliases[tzid]?.let { return it }
      zoneOrNull(tzid)?.let { return it }
      unknownZones += tzid
      return device
    }
  }

  private fun zoneOrNull(id: String): ZoneId? =
    runCatching { ZoneId.of(id.trim().removePrefix("/")) }.getOrNull()

  // Content lines

  private data class Prop(
    val name: String,
    val params: Map<String, String>,
    val value: String
  )

  private class Component(val name: String) {
    val props = mutableListOf<Prop>()
    val children = mutableListOf<Component>()

    fun first(name: String) = props.firstOrNull { it.name == name }

    fun all(name: String) = props.filter { it.name == name }

    fun value(name: String) = first(name)?.value

    fun descendants(): List<Component> =
      children.flatMap { listOf(it) + it.descendants() }
  }

  private fun parse(text: String): Component {
    val root = Component("ROOT")
    val stack = ArrayDeque(listOf(root))
    for (line in IcsText.unfold(text)) {
      val prop = parseLine(line) ?: continue
      when (prop.name) {
        "BEGIN" -> {
          val child = Component(prop.value.trim().uppercase())
          stack.last().children += child
          stack.addLast(child)
        }
        "END" -> if (stack.size > 1) stack.removeLast()
        else -> stack.last().props += prop
      }
    }
    return root
  }

  private fun parseLine(line: String): Prop? {
    val colon = indexOutsideQuotes(line, ':')
    if (colon < 0) return null

    val head = splitOutsideQuotes(line.substring(0, colon), ';')
    val params = head.drop(1).mapNotNull { segment ->
      val eq = segment.indexOf('=')
      if (eq < 0) return@mapNotNull null
      val key = segment.substring(0, eq).trim().uppercase()
      key to segment.substring(eq + 1).trim().trim('"')
    }
    val name = head.first().trim().uppercase()
    return Prop(name, params.toMap(), line.substring(colon + 1))
  }

  private fun indexOutsideQuotes(text: String, target: Char): Int {
    var quoted = false
    text.forEachIndexed { i, c ->
      if (c == '"') quoted = !quoted
      if (c == target && !quoted) return i
    }
    return -1
  }

  private fun splitOutsideQuotes(text: String, separator: Char): List<String> {
    val parts = mutableListOf<String>()
    var rest = text
    while (true) {
      val at = indexOutsideQuotes(rest, separator)
      if (at < 0) break
      parts += rest.substring(0, at)
      rest = rest.substring(at + 1)
    }
    return parts + rest
  }
}
