# Data Model

This document defines every persistent and in-flight data structure in Junnz: domain models in shared Kotlin, Room entities on phone, vector store schema, and the sync payload schema between phone and watch.

The order of authority is:
1. **Domain models in `shared-domain`** are the truth.
2. **Room entities** are an Android persistence projection of the domain.
3. **Sync messages** are a wire-format projection of the domain.

When they disagree, domain wins. Room entities and sync messages map *to and from* domain via explicit mappers, never via shared types.

## Domain models

Defined in `shared-domain`. Pure Kotlin, no Android dependencies, no platform calls. Annotated with `kotlinx.serialization.Serializable` only where they cross a wire (sync).

### `Reminder`

The central aggregate.

```kotlin
data class Reminder(
    val id: ReminderId,
    val title: String,                       // user-visible short text, max 120 chars
    val body: String?,                       // optional longer text, max 2000 chars
    val tags: Set<Tag>,                      // derived + user-editable
    val triggers: List<Trigger>,             // 1..n triggers
    val status: ReminderStatus,
    val captureContext: CaptureContext,      // how/when this was captured
    val createdAt: Instant,
    val updatedAt: Instant,
    val firedAt: List<Instant>,              // every time this reminder fired
    val actedOnAt: Instant?,                 // when user marked acted-on, if ever
    val embeddingVersion: Int,               // bumped when re-embedded after model upgrade
)
```

Notes:
- `id` is a wrapper around `Uuid` (kotlinx.uuid). Wrapping prevents accidental cross-id assignments.
- `firedAt` is a list, not a single instant. A reminder can fire multiple times (e.g., recurring time trigger, or context trigger that re-fires after dismissal).
- `embeddingVersion` is an integer tied to `EmbeddingModel.version`. When we upgrade the model, all reminders are re-embedded by `EmbedReminderWorker`; we know which are stale by version mismatch.

### `Trigger` (sealed class)

```kotlin
sealed class Trigger {
    abstract val id: TriggerId

    data class Time(
        override val id: TriggerId,
        val fireAt: Instant,
        val recurrence: Recurrence?,         // null = one-shot
        val timeZoneId: String,              // IANA tz, e.g. "Asia/Kolkata"
    ) : Trigger()

    data class Geofence(
        override val id: TriggerId,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,             // 50..5000
        val placeName: String?,              // user-visible label, e.g. "Home"
        val transitionType: GeofenceTransition, // ENTER, EXIT, DWELL
        val dwellSeconds: Int?,              // required if transitionType == DWELL
    ) : Trigger()

    data class AppContext(
        override val id: TriggerId,
        val packageNames: Set<String>,       // explicit allowlist, e.g. ["app.blinkit", "in.zomato.app"]
        val categoryHints: Set<AppCategory>, // GROCERY, FOOD, SHOPPING — derived hint, fuzzy match
    ) : Trigger()

    data class Semantic(
        override val id: TriggerId,
        val anchorEmbedding: FloatArray,     // 384-dim float vector (MiniLM) or 768-dim (Gemini)
        val anchorText: String,              // human-readable seed text, e.g. "buying groceries"
        val similarityThreshold: Float,      // 0.0..1.0, default 0.72 (see MATCHING_ENGINE.md)
    ) : Trigger()
}
```

Notes:
- A reminder can have multiple triggers of the same kind (e.g., two geofences). Triggers are OR-combined: any one firing fires the reminder.
- `Semantic` is the differentiator. `anchorText` is preserved for debugging and re-embedding on model upgrade — never delete it.
- `equals`/`hashCode` for `Trigger.Semantic` ignore the `anchorEmbedding` (it's a float array; identity is by id).

### `Recurrence`

```kotlin
data class Recurrence(
    val rule: RecurrenceRule,
    val until: Instant?,
    val occurrenceCount: Int?,               // alternative to until
)

enum class RecurrenceRule { DAILY, WEEKDAYS, WEEKLY, MONTHLY, YEARLY, CUSTOM_RRULE }
```

For v1: `CUSTOM_RRULE` is a placeholder. We support the five fixed rules; full RFC 5545 RRULE comes in v2 if users ask.

### `ReminderStatus`

```kotlin
enum class ReminderStatus {
    PENDING,         // active, awaiting trigger
    SNOOZED,         // user dismissed, will resurface
    COMPLETED,       // user acted on it
    ARCHIVED,        // user explicitly dismissed without action
    EXPIRED,         // time-based, time passed without firing or acting
}
```

State transitions are limited and explicit:
- `PENDING` → `SNOOZED` (user dismisses)
- `PENDING` → `COMPLETED` (user marks done)
- `PENDING` → `ARCHIVED` (user archives)
- `PENDING` → `EXPIRED` (system, on time-trigger expiry)
- `SNOOZED` → `PENDING` (snooze period elapsed)
- `SNOOZED` → `COMPLETED` / `ARCHIVED`

### `CaptureContext`

```kotlin
data class CaptureContext(
    val source: CaptureSource,               // WATCH_BUTTON, PHONE_BUTTON, SHARE_SHEET, MANUAL
    val capturedAt: Instant,
    val capturedAtLocation: GeoPoint?,       // null if location not granted
    val rawTranscript: String?,              // pre-NLP, for debug + re-parsing on NLP upgrade
    val asrEngine: AsrEngineId,
    val asrConfidence: Float?,               // engine-reported, 0..1, nullable
)
```

`rawTranscript` is retained per the user's voice retention policy. If the user has voice retention off, `rawTranscript` is also discarded after parsing. We keep the parsed `Reminder` only.

### `Tag`

```kotlin
@JvmInline
value class Tag(val value: String) {
    init { require(value.matches(Regex("^[a-z0-9-]{1,32}$"))) }
}
```

Tags are normalized (lowercase, dash-separated). Derived during parsing (e.g., "groceries" → tag `grocery`) and user-editable. Used for fast pre-filtering before semantic search.

### `MatchCandidate`

Not persisted. In-memory result of matching engine.

```kotlin
data class MatchCandidate(
    val reminder: Reminder,
    val trigger: Trigger,
    val score: Float,                        // 0..1, higher is better
    val reason: MatchReason,                 // for debug + UI explanation
)

sealed class MatchReason {
    data class TimeFired(val scheduledFor: Instant) : MatchReason()
    data class GeofenceEntered(val placeName: String?) : MatchReason()
    data class AppOpened(val packageName: String) : MatchReason()
    data class SemanticMatch(val anchorText: String, val querySnippet: String) : MatchReason()
}
```

`MatchReason` populates the user-facing "why is this firing" text on the notification.

## Room schema (phone only)

Database name: `junnz.db`. Encrypted with SQLCipher. Schema version starts at 1; migrations are explicit and tested.

### Tables

```
reminders
├── id                  TEXT PRIMARY KEY      -- UUID string
├── title               TEXT NOT NULL
├── body                TEXT
├── status              TEXT NOT NULL          -- enum name
├── created_at          INTEGER NOT NULL       -- epoch ms
├── updated_at          INTEGER NOT NULL
├── acted_on_at         INTEGER
├── embedding_version   INTEGER NOT NULL DEFAULT 0
└── (indexes on status, updated_at)

triggers
├── id                  TEXT PRIMARY KEY
├── reminder_id         TEXT NOT NULL REFERENCES reminders(id) ON DELETE CASCADE
├── kind                TEXT NOT NULL          -- TIME, GEOFENCE, APP_CONTEXT, SEMANTIC
├── payload             TEXT NOT NULL          -- JSON, schema depends on kind
└── (indexes on reminder_id, kind)

reminder_tags
├── reminder_id         TEXT NOT NULL REFERENCES reminders(id) ON DELETE CASCADE
├── tag                 TEXT NOT NULL
└── PRIMARY KEY (reminder_id, tag)

reminder_fire_history
├── id                  INTEGER PRIMARY KEY AUTOINCREMENT
├── reminder_id         TEXT NOT NULL REFERENCES reminders(id) ON DELETE CASCADE
├── fired_at            INTEGER NOT NULL
├── trigger_id          TEXT
├── reason              TEXT NOT NULL          -- JSON of MatchReason
├── score               REAL
└── (indexes on reminder_id, fired_at)

capture_contexts
├── reminder_id         TEXT PRIMARY KEY REFERENCES reminders(id) ON DELETE CASCADE
├── source              TEXT NOT NULL
├── captured_at         INTEGER NOT NULL
├── captured_lat        REAL
├── captured_lon        REAL
├── raw_transcript      TEXT                   -- nullable per voice retention policy
├── asr_engine          TEXT NOT NULL
└── asr_confidence      REAL

context_observations
├── id                  INTEGER PRIMARY KEY AUTOINCREMENT
├── observed_at         INTEGER NOT NULL
├── source              TEXT NOT NULL          -- NOTIF_LISTENER, FOREGROUND_APP, VOICED
├── package_name        TEXT
├── category            TEXT
├── voiced_text         TEXT                   -- only if source = VOICED
└── (index on observed_at)
-- rolling window: rows older than 1 hour deleted by ContextWindowExpiryWorker

audio_files
├── reminder_id         TEXT REFERENCES reminders(id) ON DELETE CASCADE
├── file_path           TEXT NOT NULL          -- relative, app-private
├── created_at          INTEGER NOT NULL
├── encryption_key_alias TEXT NOT NULL
└── PRIMARY KEY (reminder_id)

embeddings
├── reminder_id         TEXT PRIMARY KEY REFERENCES reminders(id) ON DELETE CASCADE
├── model_id            TEXT NOT NULL          -- e.g. "minilm-l6-v2"
├── model_version       INTEGER NOT NULL
├── created_at          INTEGER NOT NULL
└── (vector lives in vector_index, this row is metadata)

settings
├── key                 TEXT PRIMARY KEY
├── value               TEXT NOT NULL
└── updated_at          INTEGER NOT NULL
```

### Vector index

Using sqlite-vec, defined in the same database file but in its own virtual table:

```
CREATE VIRTUAL TABLE vector_index USING vec0(
    reminder_id TEXT PRIMARY KEY,
    embedding FLOAT[384]            -- 384 for MiniLM; rebuild on model change to 768 for Gemini
);
```

We commit to a single embedding dimensionality per model version. Switching models is a major migration: all reminders re-embedded, vector_index dropped and rebuilt.

### Migrations

Migration strategy:
- Each schema bump has a `Migration_X_to_Y` class with both `migrate()` and a test using a fixture DB.
- Tests load a fixture v(N) DB and assert the v(N+1) DB looks correct, runs queries, etc.
- Never edit a past migration. Never reorder.
- For destructive migrations (e.g., embedding model change), the migration enqueues a `BulkReembedWorker` rather than blocking the migration on a long ML job.

## KMP serialization for sync

All sync types live in `shared-sync` and are `@Serializable`. We use **CBOR** (compact binary) on the wire, not JSON, to minimize watch CPU.

### Compact wire types

The wire types are slimmer than domain types — they include only what the watch needs:

```kotlin
@Serializable
data class ReminderSummaryWire(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val statusOrdinal: Int,
    @ProtoNumber(4) val nextFireAtMillis: Long?,
    @ProtoNumber(5) val triggerKindBitmask: Int,    // bit 0=TIME, 1=GEOFENCE, 2=APP_CONTEXT, 3=SEMANTIC
)
```

The watch never sees the body, the embeddings, or the raw transcript. By design.

### Sync messages (recap from `ARCHITECTURE.md`)

```kotlin
@Serializable
sealed class SyncMessage {
    @Serializable data class CaptureRequested(...) : SyncMessage()
    @Serializable data class CaptureAudioChunk(...) : SyncMessage()
    @Serializable data class CaptureCompleted(...) : SyncMessage()
    @Serializable data class ReminderFired(...) : SyncMessage()
    @Serializable data class ReminderDismissed(...) : SyncMessage()
    @Serializable data class ReminderActedOn(...) : SyncMessage()
    @Serializable data class ReminderListSnapshot(val reminders: List<ReminderSummaryWire>) : SyncMessage()
    @Serializable data class ContextAnnouncement(...) : SyncMessage()
    @Serializable data class Heartbeat(...) : SyncMessage()
}

@Serializable
data class SyncEnvelope(
    val protocolVersion: Int,
    val messageId: String,
    val timestampMillis: Long,
    val body: SyncMessage,
)
```

Protocol versioning rule: new optional fields don't bump the version; new sealed subclasses or required fields do.

### Watch-local Room schema

Single table:

```
local_captures (watch-only)
├── id                  TEXT PRIMARY KEY
├── recorded_at         INTEGER NOT NULL
├── duration_ms         INTEGER NOT NULL
├── audio_path          TEXT NOT NULL          -- watch-local file
├── pending_sync        INTEGER NOT NULL       -- 0 or 1
└── created_reminder_id TEXT                   -- set after phone confirms
```

This exists only to handle the "watch captured offline, phone disconnected" case. Drained on reconnect.

## Export format (data portability)

User's "Export my data" feature produces a single ZIP file:

```
junnz-export-{timestamp}/
├── reminders.json          # All reminders, full domain shape, JSON
├── triggers.json           # All triggers, denormalized
├── fire_history.json       # All fire events
├── capture_contexts.json
├── settings.json
├── audio/                  # Voice files (only if user retained them)
│   ├── {reminder_id}.m4a
│   └── ...
└── README.txt              # Human-readable explanation of the export format
```

JSON schema is documented at `docs/export-schema.md` (separate doc, generated from `kotlinx.serialization` schema). Stable across versions; new fields added with defaults so old exports remain parseable.

Embeddings are NOT included in the export — they're machine-generated and rebuildable. Keeping them out reduces export size and avoids privacy surface area if the export is mishandled.

## Identifiers

- All IDs are UUIDv7 (time-ordered) wrapped in value classes.
- Why v7: time-ordered makes index locality good, helps with debugging (you can sort by id and get chronological).
- Generated client-side. No server, no central authority.

## Rules for evolving the data model

1. **Domain model changes** require: a corresponding mapper update, a Room migration if persisted, and a sync protocol decision (compatible? version bump?).
2. **Adding a field**: default value required, migration adds it with that default, mapper handles missing.
3. **Renaming a field**: avoid. Add new, deprecate old, remove in a major version.
4. **Removing a field**: only after at least one minor version of soft-deprecation. Migration drops the column.
5. **Changing semantics of a field without renaming**: forbidden. Add a new field instead.
6. **Embedding model upgrades**: bump `EmbeddingModel.version`. Existing reminders flagged stale via `embedding_version` mismatch. `BulkReembedWorker` re-embeds on charge.

## Open questions

- **Do we need a `place` table** to deduplicate `Geofence` triggers across reminders that reference the same place (e.g., "Home")? Likely yes by v2; YAGNI for v0.
- **Do we maintain a tag taxonomy** (parent-child tags) or stay flat? Flat for v1.
- **Do we expose a query DSL** for power users to find reminders matching arbitrary trigger+tag conditions? Defer until users ask.
- **Should `embeddings` table store the vector itself in addition to vector_index**, for cross-DB-engine portability? Trade off: storage cost vs. resilience. Defer.
