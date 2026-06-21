package com.junnz.shared.parsing

import com.junnz.shared.domain.Trigger
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Result of turning a free-form transcript into a structured reminder. */
data class ParsedReminder(
    val text: String,
    val triggers: List<Trigger>,
)

/**
 * Heuristic, offline natural-language parser. Splits a transcript into a clean
 * task title plus zero or one [Trigger] (time / location / app / semantic).
 *
 * Pure Kotlin + kotlinx.datetime so it is shared by phone and watch and unit
 * testable with a fixed clock. It is intentionally forgiving: when in doubt it
 * keeps the whole sentence as the task and attaches no trigger.
 */
class ReminderParser(
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {

    fun parse(transcript: String): ParsedReminder {
        val cleaned = transcript.trim().replace(WHITESPACE, " ")
        if (cleaned.isEmpty()) return ParsedReminder("", emptyList())

        val splitIndex = TRIGGER_START.find(cleaned)?.range?.first ?: -1
        val taskPart: String
        val clause: String?
        if (splitIndex >= 0) {
            taskPart = cleaned.substring(0, splitIndex)
            clause = cleaned.substring(splitIndex)
        } else {
            taskPart = cleaned
            clause = null
        }

        val title = cleanTitle(taskPart.ifBlank { cleaned })
        val trigger = clause?.let { classifyClause(it) }
        return ParsedReminder(title, listOfNotNull(trigger))
    }

    // ── Title cleanup ─────────────────────────────────────────────────────────

    private fun cleanTitle(raw: String): String {
        var t = raw.trim().trim(',', '.', ';', ':', '-', ' ')
        val lower = t.lowercase()
        for (lead in LEAD_INS) {
            if (lower.startsWith(lead)) {
                t = t.substring(lead.length)
                break
            }
        }
        t = t.trim()
        if (t.lowercase().startsWith("to ")) t = t.substring(3).trim()
        t = t.trim(',', '.', ';', ':', '-', ' ')
        return t.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    // ── Clause classification ─────────────────────────────────────────────────

    private fun classifyClause(rawClause: String): Trigger? {
        val clause = rawClause.trim()
        val lower = clause.lowercase()
        val meat = stripWhenPrefix(lower)

        // App names are unambiguous, so check them first. A clear temporal signal
        // ("at 9 pm", "tomorrow", "saturday") must beat the location cue, which
        // otherwise greedily claims "at <time>" phrases.
        appTrigger(meat)?.let { return it }
        if (TIME_SIGNAL.containsMatchIn(lower)) {
            timeTrigger(lower)?.let { return it }
        }
        locationTrigger(meat)?.let { return it }
        timeTrigger(lower)?.let { return it }

        // Fallback: a "when …" cue we couldn't structure → semantic anchor.
        return if (meat.isNotBlank() && lower != meat) {
            Trigger.SemanticTrigger(
                anchorText = meat.replaceFirstChar { it.titlecase() },
                threshold = SEMANTIC_THRESHOLD,
            )
        } else {
            null
        }
    }

    private fun stripWhenPrefix(lower: String): String {
        var s = lower
        for (p in WHEN_PREFIXES) {
            if (s.startsWith(p)) {
                s = s.substring(p.length)
                break
            }
        }
        return s.trim()
    }

    // ── App context ───────────────────────────────────────────────────────────

    private fun appTrigger(meat: String): Trigger.AppContextTrigger? {
        val found = KNOWN_APPS.filter { meat.contains(it.name.lowercase()) }
        if (found.isNotEmpty()) {
            return Trigger.AppContextTrigger(
                packageNames = found.map { it.pkg },
                label = found.joinToString(", ") { it.name },
            )
        }
        // "open/launch/using <something>" with an unknown app name.
        val m = OPEN_APP.find(meat) ?: return null
        val name = m.groupValues[1].trim().trimEnd('.', ',').words(2)
        if (name.isBlank()) return null
        return Trigger.AppContextTrigger(
            packageNames = emptyList(),
            label = name.replaceFirstChar { it.titlecase() },
        )
    }

    // ── Location ──────────────────────────────────────────────────────────────

    private fun locationTrigger(meat: String): Trigger.GeofenceTrigger? {
        val m = LOCATION_CUE.find(meat) ?: return null
        val rest = m.groupValues[1].trim()
            .removePrefix("the ").trim()
            .trimEnd('.', ',')
        val label = rest.words(3)
        if (label.isBlank()) return null
        return Trigger.GeofenceTrigger(
            lat = 0.0,
            lng = 0.0,
            label = label.replaceFirstChar { it.titlecase() },
        )
    }

    // ── Time ──────────────────────────────────────────────────────────────────

    private fun timeTrigger(clauseLower: String): Trigger.TimeTrigger? {
        val now = clock.now()
        val nowLdt = now.toLocalDateTime(zone)
        val today = nowLdt.date

        // Relative: "in 10 minutes", "in 2 hours", "in 3 days"
        REL.find(clauseLower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            val unitStr = m.groupValues[2]
            val instant = when {
                unitStr.startsWith("min") -> now.plus(n, DateTimeUnit.MINUTE)
                unitStr.startsWith("h") -> now.plus(n, DateTimeUnit.HOUR)
                unitStr.startsWith("d") -> now.plus(n, DateTimeUnit.DAY, zone)
                unitStr.startsWith("w") -> now.plus(n * 7, DateTimeUnit.DAY, zone)
                else -> return@let
            }
            return Trigger.TimeTrigger(fireAt = instant)
        }

        // Clock time (9, 9:30, 9 pm, noon, midnight)
        val clock24 = parseClock(clauseLower)

        // Target date
        var date: LocalDate? = null
        var partOfDayHour: Int? = null

        when {
            clauseLower.contains("tomorrow") -> date = today.plus(1, DateTimeUnit.DAY)
            clauseLower.contains("tonight") -> { date = today; partOfDayHour = 20 }
            clauseLower.contains("today") -> date = today
        }
        WEEKDAY_NAMES.entries.firstOrNull { clauseLower.contains(it.key) }?.let { (name, dow) ->
            val forceNext = clauseLower.contains("next $name")
            date = nextWeekday(today, dow, forceNext)
        }
        if (date == null && clauseLower.contains("next week")) {
            date = today.plus(7, DateTimeUnit.DAY)
        }
        when {
            clauseLower.contains("morning") -> partOfDayHour = partOfDayHour ?: 9
            clauseLower.contains("afternoon") -> partOfDayHour = partOfDayHour ?: 14
            clauseLower.contains("evening") -> partOfDayHour = partOfDayHour ?: 18
            clauseLower.contains("night") -> partOfDayHour = partOfDayHour ?: 20
        }

        val hour: Int
        val minute: Int
        when {
            clock24 != null -> { hour = clock24.first; minute = clock24.second }
            partOfDayHour != null -> { hour = partOfDayHour; minute = 0 }
            date != null -> { hour = 9; minute = 0 }
            else -> return null // no temporal signal at all
        }

        val targetDate = date ?: today
        var instant = LocalDateTime(
            targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth, hour, minute,
        ).toInstant(zone)
        // If we landed in the past (e.g. "at 8" but it's 9pm), push to tomorrow.
        if (instant <= now && date == null) {
            val tmr = today.plus(1, DateTimeUnit.DAY)
            instant = LocalDateTime(tmr.year, tmr.monthNumber, tmr.dayOfMonth, hour, minute).toInstant(zone)
        }
        return Trigger.TimeTrigger(fireAt = instant)
    }

    /** Returns (hour24, minute) or null. */
    private fun parseClock(s: String): Pair<Int, Int>? {
        if (s.contains("noon")) return 12 to 0
        if (s.contains("midnight")) return 0 to 0
        val m = CLOCK.find(s) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val mer = m.groupValues[3].replace(".", "").lowercase()
        when (mer) {
            "pm" -> if (hour < 12) hour += 12
            "am" -> if (hour == 12) hour = 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    private fun nextWeekday(today: LocalDate, target: DayOfWeek, forceNext: Boolean): LocalDate {
        var ahead = (target.isoDayNumber - today.dayOfWeek.isoDayNumber + 7) % 7
        if (ahead == 0) ahead = 7          // upcoming occurrence, never "today"
        if (forceNext) ahead += 7          // "next <day>" = following week
        return today.plus(ahead, DateTimeUnit.DAY)
    }

    private fun String.words(n: Int): String =
        trim().split(' ').filter { it.isNotBlank() }.take(n).joinToString(" ")

    private data class KnownApp(val name: String, val pkg: String)

    companion object {
        /**
         * Match threshold for parser-created semantic anchors. Tuned for the
         * default on-device lexical embedding, whose cosine for a short anchor
         * vs. a full notification sentence runs ~0.3–0.5 when related and ~0 when
         * not. With a cloud embedding service configured, raise this toward ~0.6.
         */
        const val SEMANTIC_THRESHOLD = 0.3f

        private val WHITESPACE = Regex("""\s+""")

        private val LEAD_INS = listOf(
            "remind me to ", "remind me ", "reminder to ", "set a reminder to ",
            "set a reminder ", "remember to ", "remember ", "don't forget to ",
            "dont forget to ", "i need to ", "i have to ", "i should ",
            "make a note to ", "note to ", "please ",
        )

        private val WHEN_PREFIXES = listOf(
            "when i'm ", "when i am ", "when im ", "when i ", "when ",
        )

        private const val WD = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"

        private val TRIGGER_START = Regex(
            """\b(when\b""" +
                """|at\s+(?:\d|noon|midnight|the\b)""" +
                """|in\s+\d""" +
                """|on\s+(?:$WD)""" +
                """|tomorrow\b|tonight\b|today\b""" +
                """|this\s+(?:morning|afternoon|evening|night|$WD)""" +
                """|next\s+(?:week|$WD)""" +
                """|every\s+(?:day|morning|evening|night|$WD)""" +
                """|near\b""" +
                """|(?:$WD))""",
            RegexOption.IGNORE_CASE,
        )

        private val OPEN_APP = Regex("""(?:open|launch|using)\s+(.+)""")

        private val LOCATION_CUE = Regex(
            """(?:reach|arrive at|arriving at|arrive|get to|getting to|am at|near|at)\s+(.+)""",
        )

        private val REL = Regex("""\bin\s+(\d+)\s*(minutes?|mins?|hours?|hrs?|days?|weeks?)\b""")

        /** A strong temporal cue that should outrank the location cue. */
        private val TIME_SIGNAL = Regex(
            """(\bat\s+\d|\d\s*(?:a\.?m\.?|p\.?m\.?)|\d:\d{2}""" +
                """|\bnoon\b|\bmidnight\b|\btomorrow\b|\btonight\b|\btoday\b""" +
                """|\bnext\s+week\b|\b(?:morning|afternoon|evening|night)\b""" +
                """|\bin\s+\d|\b(?:$WD)\b)""",
            RegexOption.IGNORE_CASE,
        )

        private val CLOCK = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?""")

        private val WEEKDAY_NAMES = linkedMapOf(
            "monday" to DayOfWeek.MONDAY,
            "tuesday" to DayOfWeek.TUESDAY,
            "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY,
            "friday" to DayOfWeek.FRIDAY,
            "saturday" to DayOfWeek.SATURDAY,
            "sunday" to DayOfWeek.SUNDAY,
        )

        private val KNOWN_APPS = listOf(
            KnownApp("Blinkit", "com.grofers.customerapp"),
            KnownApp("Zepto", "com.zeptoconsumerapp"),
            KnownApp("Swiggy", "in.swiggy.android"),
            KnownApp("Instamart", "in.swiggy.android"),
            KnownApp("Zomato", "com.application.zomato"),
            KnownApp("Amazon", "in.amazon.mShop.android.shopping"),
            KnownApp("Flipkart", "com.flipkart.android"),
            KnownApp("WhatsApp", "com.whatsapp"),
            KnownApp("Instagram", "com.instagram.android"),
            KnownApp("Spotify", "com.spotify.music"),
        )
    }
}
