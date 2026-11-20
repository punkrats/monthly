package app.monthly.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import app.monthly.ics.RRule
import app.monthly.model.Event
import app.monthly.model.EventColor
import app.monthly.model.EventKind
import app.monthly.model.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Two tables: events (one row per series) and cycle start dates. */
class Database(context: Context) :
  SQLiteOpenHelper(context, "monthly.db", null, 1) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        uid TEXT NOT NULL UNIQUE,
        kind TEXT NOT NULL,
        title TEXT NOT NULL,
        all_day INTEGER NOT NULL,
        start_date TEXT NOT NULL,
        start_time TEXT,
        end_date TEXT NOT NULL,
        end_time TEXT,
        zone TEXT,
        location TEXT NOT NULL,
        notes TEXT NOT NULL,
        color TEXT NOT NULL,
        rrule TEXT,
        exdates TEXT NOT NULL,
        reminders TEXT NOT NULL,
        birth_year INTEGER
      )
      """.trimIndent()
    )
    db.execSQL("CREATE TABLE cycle_starts (date TEXT PRIMARY KEY)")
  }

  override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit

  fun events(): List<Event> =
    readableDatabase.query("events", null, null, null, null, null, "id")
      .use { cursor ->
        generateSequence { if (cursor.moveToNext()) cursor else null }
          .mapNotNull { eventOf(it) }
          .toList()
      }

  fun cycleStarts(): List<LocalDate> =
    readableDatabase.query(
      "cycle_starts", arrayOf("date"), null, null, null, null, "date"
    ).use { cursor ->
      generateSequence { if (cursor.moveToNext()) cursor else null }
        .map { it.getString(0) }
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .toList()
    }

  /** Inserts when [Event.id] is 0, otherwise updates; returns the row id. */
  fun save(event: Event): Long {
    val values = valuesOf(event)
    val db = writableDatabase
    if (event.id == 0L) return db.insertOrThrow("events", null, values)

    db.update("events", values, "id = ?", arrayOf("${event.id}"))
    return event.id
  }

  fun delete(id: Long) {
    writableDatabase.delete("events", "id = ?", arrayOf("$id"))
  }

  /** Same UID replaces the stored row, keeping its id. */
  fun upsertAll(events: List<Event>, cycle: List<LocalDate>) {
    writableDatabase.transaction {
      val ids = idsByUid(this)
      events.forEach { save(it.copy(id = ids[it.uid] ?: 0L)) }
      cycle.forEach { addCycleStart(it) }
    }
  }

  fun addCycleStart(date: LocalDate) {
    val values = ContentValues().apply { put("date", date.toString()) }
    writableDatabase.insertWithOnConflict(
      "cycle_starts", null, values, SQLiteDatabase.CONFLICT_IGNORE
    )
  }

  fun removeCycleStart(date: LocalDate) {
    writableDatabase.delete("cycle_starts", "date = ?", arrayOf("$date"))
  }

  fun moveCycleStart(from: LocalDate, to: LocalDate) {
    writableDatabase.transaction {
      removeCycleStart(from)
      addCycleStart(to)
    }
  }

  private fun idsByUid(db: SQLiteDatabase): Map<String, Long> =
    db.query("events", arrayOf("uid", "id"), null, null, null, null, null)
      .use { cursor ->
        generateSequence { if (cursor.moveToNext()) cursor else null }
          .associate { it.getString(0) to it.getLong(1) }
      }

  private fun valuesOf(event: Event) = ContentValues().apply {
    put("uid", event.uid)
    put("kind", event.kind.name)
    put("title", event.title)
    put("all_day", if (event.allDay) 1 else 0)
    put("start_date", event.startDate.toString())
    put("start_time", event.startTime?.toString())
    put("end_date", event.endDate.toString())
    put("end_time", event.endTime?.toString())
    put("zone", event.zone?.id)
    put("location", event.location)
    put("notes", event.notes)
    put("color", event.color.name)
    put("rrule", event.recurrence?.let { RRule.format(it, event.startDate) })
    put("exdates", event.exceptions.sorted().joinToString(","))
    put("reminders", Reminder.encode(event.reminders))
    put("birth_year", event.birthYear)
  }

  private fun eventOf(row: Cursor): Event? = runCatching {
    val startDate = LocalDate.parse(row.text("start_date"))
    val zone = row.textOrNull("zone")?.let { ZoneId.of(it) }
    Event(
      id = row.getLong(row.getColumnIndexOrThrow("id")),
      uid = row.text("uid"),
      kind = EventKind.valueOf(row.text("kind")),
      title = row.text("title"),
      allDay = row.getInt(row.getColumnIndexOrThrow("all_day")) == 1,
      startDate = startDate,
      startTime = row.textOrNull("start_time")?.let { LocalTime.parse(it) },
      endDate = LocalDate.parse(row.text("end_date")),
      endTime = row.textOrNull("end_time")?.let { LocalTime.parse(it) },
      zone = zone,
      location = row.text("location"),
      notes = row.text("notes"),
      color = EventColor.valueOf(row.text("color")),
      recurrence = row.textOrNull("rrule")?.let {
        RRule.parse(it, startDate, zone ?: ZoneOffset.UTC)
      },
      exceptions = row.text("exdates")
        .split(',')
        .filter { it.isNotBlank() }
        .map { LocalDate.parse(it) }
        .toSet(),
      reminders = Reminder.decode(row.text("reminders")),
      birthYear = row.intOrNull("birth_year")
    )
  }.getOrNull()

  private fun Cursor.text(column: String) =
    getString(getColumnIndexOrThrow(column))

  private fun Cursor.textOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
  }

  private fun Cursor.intOrNull(column: String): Int? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getInt(index)
  }
}
