# Architecture

This document describes the runtime architecture of Junnz: modules, components, processes, IPC, and data flow. It is paired with `DATA_MODEL.md` (schema), `MATCHING_ENGINE.md` (the matching algorithm), and `BATTERY_CONSTRAINTS.md` (execution rules).

---

## Topology

```
┌─────────────────────────────────────┐         ┌─────────────────────────┐
│            PHONE (brain)            │   BLE   │      WATCH (client)     │
│                                     │ ◀─────▶ │                         │
│  ┌─────────────────────────────┐    │  WDL    │  ┌──────────────────┐   │
│  │  Foreground Service          │    │         │  │ Capture Activity │   │
│  │  (matching loop, owner of    │    │         │  │ (button → mic    │   │
│  │   trigger registry)          │    │         │  │  → chunked send) │   │
│  └─────────────────────────────┘    │         │  └──────────────────┘   │
│        │             │               │         │                         │
│        ▼             ▼               │         │  ┌──────────────────┐   │
│  ┌──────────┐  ┌────────────┐        │         │  │ Reminder List UI │   │
│  │  ASR     │  │ Embeddings │        │         │  │ Detail / Snooze  │   │
│  │ (Nano)   │  │  (ONNX)    │        │         │  └──────────────────┘   │
│  └──────────┘  └────────────┘        │         │                         │
│        │             │               │         │  ┌──────────────────┐   │
│        ▼             ▼               │         │  │ Tile + Complication
│  ┌─────────────────────────────┐    │         │  │ (mascot canvas in v3)│
│  │  Matching Engine            │    │         │  └──────────────────┘   │
│  │  (KMP shared module)        │    │         │                         │
│  └─────────────────────────────┘    │         │  ┌──────────────────┐   │
│        │                             │         │  │ Ongoing Activity │   │
│        ▼                             │         │  │ (live capture    │   │
│  ┌─────────────────────────────┐    │         │  │  status)         │   │
│  │  Trigger Evaluators         │    │         │  └──────────────────┘   │
│  │  Time | Geofence |          │    │         │                         │
│  │  Semantic | CompanionApp    │    │         │                         │
│  └─────────────────────────────┘    │         │                         │
│        │                             │         │                         │
│        ▼                             │         │                         │
│  ┌─────────────────────────────┐    │         │                         │
│  │  Room + sqlite-vec          │    │         │                         │
│  │  Reminder + Embedding store │    │         │                         │
│  └─────────────────────────────┘    │         │                         │
│                                     │         │                         │
│  Side inputs:                       │         │                         │
│   • NotificationListenerService     │         │                         │
│   • UsageStatsManager (foreground)  │         │                         │
│   • FusedLocation + Geofencing      │         │                         │
│   • AlarmManager (time triggers)    │         │                         │
└─────────────────────────────────────┘         └─────────────────────────┘
```

The phone owns all state. The watch is **stateless except for UI cache** — every action it takes is replayed to the phone, and every reminder it shows came from the phone. This is deliberate: it keeps the watch app small, lets us evolve schemas without watch updates, and makes the privacy story simpler (one place to encrypt, one place to delete).

---

## Module breakdown

### `shared/` — Kotlin Multiplatform

Pure Kotlin, no Android dependencies. Targets `android` and `androidWear` (and trivially `ios` later if it ever happens).

| Package | Responsibility |
|---|---|
| `domain` | Data classes: `Reminder`, `Trigger`, `ContextEvent`, `FireEvent`, `Embedding`. No logic. |
| `matching` | The matching engine. Given `Reminder`s and a `ContextEvent`, return scored matches. Deterministic and unit-testable. |
| `embedding` | Interface `EmbeddingService { suspend fun embed(text: String): FloatArray }`. Platform impls in `app-phone`. |
| `time` | `kotlinx-datetime` wrappers, recurrence rule (RRULE-lite) parsing. |

Why KMP and not just an Android library: the matching engine is the part most likely to be ported, and writing it without Android imports forces clean boundaries. There's also a small chance a server-side matching service appears in v4+; KMP makes that a `jvm` target, no rewrite.

### `app-phone/` — Android phone application

The brain. Long-running.

#### `service/JunnzForegroundService`

Class: `Service` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` (or `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` once we evaluate Play policy). Runs while there are pending reminders that need *active* matching (i.e. semantic context triggers exist). When all reminders resolve to time/geofence-only triggers — both of which are OS-managed — the service stops itself.

Lifecycle:
- Started by `BootCompletedReceiver` on boot, by `ReminderRepository` when a semantic-triggered reminder is added, and by `WatchSyncService` when a capture arrives.
- Stopped when `pendingSemanticTriggers.isEmpty()`.
- Maintains a `MutableStateFlow<MatchingState>` consumed by UI for live status.

This is the only foreground notification the user sees during normal operation.

#### `service/JunnzNotificationListener`

Class: `NotificationListenerService`. Subscribes to notifications from a curated allowlist of "context-relevant" packages (Blinkit, Zomato, Amazon, Swiggy, Ola, Uber, WhatsApp/Mom — see `MATCHING_ENGINE.md` for the full taxonomy).

For each allowed notification, emits a `ContextEvent.AppNotification(packageName, title, text, postedAt)` to the matching engine. **Notification content is processed in-memory and discarded immediately**; only the package name and a one-line context phrase derived from package metadata are persisted to `ContextEvent` history (see SECURITY_AND_PRIVACY.md).

#### `service/ForegroundAppMonitor`

Uses `UsageStatsManager.queryEvents` (with `PACKAGE_USAGE_STATS` permission, user-granted) sampled at 30s intervals when the foreground service is active. Emits `ContextEvent.AppForegrounded(packageName)` when an allowlisted app comes to the foreground.

We do *not* use AccessibilityService for this in v0. AccessibilityService gives strictly more capability but draws disproportionate Play Store scrutiny and is a larger trust ask of the user.

#### `ml/`

| Class | Role |
|---|---|
| `AsrService` | Interface. Two impls: `AICoreAsrService` (Gemini Nano on supported devices), `WhisperCppAsrService` (JNI-bound). Selection happens via `AsrServiceProvider` based on `AICoreAvailability`. |
| `EmbeddingService` | ONNX Runtime impl loading a quantized e5-small or bge-small model from `assets/`. Returns L2-normalized 384-dim float arrays. |
| `IntentClassifier` | Tiny model (or rule-based in v0) that classifies an utterance as `CREATE | QUERY | CONTEXT_ANNOUNCE | DISMISS | UNKNOWN`. See MATCHING_ENGINE.md. |
| `SlotExtractor` | Pulls structured fields (time, place, item) from a CREATE utterance. v0: regex + tiny LLM (Gemini Nano with structured prompt). v2: dedicated ONNX model if needed. |

#### `geofence/GeofenceManager`

Wraps `GeofencingClient`. Registers up to 100 active geofences (Android system limit). Uses dwell + enter triggers. Inactive reminders' geofences are deregistered.

#### `trigger/`

One evaluator per trigger type. All implement:

```kotlin
interface TriggerEvaluator {
    fun shouldFire(reminder: Reminder, event: ContextEvent): FireDecision
}
```

`FireDecision` is `Fire(score: Float, reason: String) | Skip`.

#### `sync/WatchSyncService`

Wearable Data Layer. Owns the message protocol (see `IPC` section below). Maintains a `DataClient` for state (reminder list mirror) and `MessageClient` for events (capture audio chunks, fire signals, snooze acks).

#### `ui/`

Compose. Screens: Capture, ReminderList, ReminderDetail, Settings, Onboarding, PermissionsExplain. ViewModels are Hilt-injected and observe repository flows.

### `app-wear/` — Wear OS application

#### `capture/CaptureActivity`

Triggered by:
- Long-press of stem button (configured via `KeyEvent` capture in manifest).
- Tap on the Junnz tile.
- Tap on a watchface complication.
- (v3) Custom wake word via Porcupine.

Captures audio with `AudioRecord` at 16kHz mono. Streams in 1-second chunks over the Wearable MessageClient. Shows a minimal Compose UI with a level meter and "tap to stop." Uses `OngoingActivity` so the capture status remains visible if the user lowers the wrist.

Audio is **not transcribed on watch**. It's shipped to the phone, transcribed there, and the transcript is sent back. This keeps the watch app small and uses the phone's superior compute.

If the phone is not in range / paired, capture writes to a local circular buffer and queues for sync on reconnect. Capacity: 10 captures max.

#### `tile/JunnzTile`

A single tile showing today's pending reminders, sorted by trigger urgency. Tap → opens detail. Renders with `ProtoLayout`.

#### `complication/`

- `NextReminderComplication` — text complication showing the next time/geofence reminder.
- `MascotComplication` (v3) — image complication for the mascot canvas.

#### `ui/`

Compose for Wear screens: ReminderList, ReminderDetail, Snooze, Settings.

---

## Data flow

### Capture flow (watch → reminder)

```
[1] User long-presses watch button
    ↓
[2] CaptureActivity opens, AudioRecord starts
    ↓
[3] Audio chunks (1s each) → MessageClient → phone
    ↓
[4] Phone JunnzForegroundService receives chunks, accumulates
    ↓
[5] On capture-end signal, AsrService.transcribe(audio) → text
    ↓
[6] IntentClassifier.classify(text) → intent
    ↓
[7a] If CREATE: SlotExtractor → Reminder draft → embed → save to Room → register triggers
[7b] If CONTEXT_ANNOUNCE: emit ContextEvent → MatchingEngine.scan() → fire matches
[7c] If QUERY: search reminders → return list to watch
[7d] If DISMISS: resolve current fire
    ↓
[8] Confirmation message → watch (haptic + visual)
```

### Matching flow (semantic context → fire)

```
[1] ContextEvent arrives (from voice, NotificationListener, or ForegroundAppMonitor)
    ↓
[2] MatchingEngine.evaluate(event):
       - load active reminders with SemanticContext or CompanionApp triggers
       - for each: TriggerEvaluator.shouldFire(reminder, event)
       - aggregate Fire decisions, sort by score, apply cooldown filter
    ↓
[3] For each Fire decision:
       - record FireEvent
       - send fire message to watch (haptic + card)
       - phone notification as fallback if watch unreachable
```

### Time trigger flow

```
[1] On reminder save: AlarmManager.setExactAndAllowWhileIdle(...) for the trigger time
    ↓
[2] At time: BroadcastReceiver fires
    ↓
[3] ReminderRepository.markFired(id) + send fire message to watch + phone notification
```

We use `setExactAndAllowWhileIdle` (with `SCHEDULE_EXACT_ALARM` permission, user-granted on Android 13+) only for time-triggered reminders. Geofence and semantic triggers don't use AlarmManager.

### Geofence flow

```
[1] On reminder save: GeofencingClient.addGeofences(...) for each location trigger
    ↓
[2] OS detects enter/dwell → PendingIntent → GeofenceBroadcastReceiver
    ↓
[3] Receiver dispatches ContextEvent.GeofenceEntered → matching engine
       - matching engine still gets to decide (semantic combination matters)
    ↓
[4] If matched: fire flow as above
```

---

## IPC: Wearable Data Layer

### Channels

| Channel | Direction | Mechanism | Purpose |
|---|---|---|---|
| `/capture/start` | Watch → Phone | MessageClient | Begin capture session, includes session UUID |
| `/capture/audio/<sessionId>` | Watch → Phone | MessageClient (binary) | Audio chunk, 1s, 16kHz PCM |
| `/capture/end/<sessionId>` | Watch → Phone | MessageClient | Capture complete, expect transcript |
| `/capture/transcript/<sessionId>` | Phone → Watch | MessageClient | Transcript + intent + outcome |
| `/reminders` | Phone → Watch | DataClient | Full reminder list mirror, last-write-wins |
| `/fire` | Phone → Watch | MessageClient | Fire event: reminderId + reason |
| `/action/<reminderId>` | Watch → Phone | MessageClient | User action: complete, snooze(5m/1h/tomorrow), dismiss |

### Sync model

Reminder list lives in a single DataItem at path `/reminders`. Phone is sole writer. Watch reads and renders. This is much simpler than two-way sync and avoids merge conflicts. Watch-originated mutations (snooze, complete) go through `/action/<reminderId>` messages, get applied on phone, and the phone re-publishes the DataItem.

### Versioning

Every DataItem and message includes a `protocolVersion: Int`. The phone refuses to send to older watches; the watch shows "update required" if it sees a newer protocol than it understands. v0 ships at `protocolVersion = 1`.

---

## Background execution model

See BATTERY_CONSTRAINTS.md for the full rules. Summary:

- **Foreground service runs only when needed.** Service stops when there are no active semantic-triggered reminders.
- **Time triggers** use `AlarmManager.setExactAndAllowWhileIdle` — OS-handled, zero ongoing cost.
- **Geofence triggers** use `GeofencingClient` — OS-handled, batched by the system.
- **Notification triggers** use `NotificationListenerService` which is event-driven (no polling).
- **Foreground app checks** are sampled at 30s intervals only while the foreground service is running.
- **WorkManager** handles periodic maintenance (embedding cleanup, geofence reconciliation, telemetry flush) on Doze-friendly schedules.

---

## What lives in `shared/` vs `app-phone/`

The matching engine itself — including embedding cosine similarity, threshold gates, cooldown logic, and category taxonomy lookup — lives in `shared/`. The platform specifics — actually running ONNX, talking to AICore, registering geofences, listening to notifications — live in `app-phone/`. The split is "what would I want to test in a JVM unit test?" → that's `shared/`.

```kotlin
// shared/matching/MatchingEngine.kt
class MatchingEngine(
    private val reminders: ReminderQuery,            // interface, impl on platform
    private val embeddings: EmbeddingService,        // interface, impl on platform
    private val cooldownStore: CooldownStore,        // interface, impl on platform
    private val config: MatchingConfig,
) {
    suspend fun evaluate(event: ContextEvent): List<FireDecision> { /* ... */ }
}
```

Tests for the matching engine run on JVM with fake `ReminderQuery` / `EmbeddingService` / `CooldownStore` impls and golden test cases in `shared/src/commonTest/`. This is critical — the matching engine's quality is the product.

---

## Process model

- Phone app runs in a single process (`com.junnz.phone`). The foreground service is in the same process.
- The NotificationListenerService runs in the same process but the system manages its lifecycle independently. We hot-route its events into the foreground service via a `SharedFlow`.
- Watch app runs in a single process (`com.junnz.wear`).
- No multi-process splits in v0–v2. If embedding becomes a memory hog (>100MB) we can consider an `:isolatedProcess` for the ML stack in v3.

---

## Build configuration

- `compileSdk = 34`, `targetSdk = 34`, `minSdk = 30` (phone), `minSdk = 30` (wear). minSdk 30 is the cost of using `GeofencingClient` modern features and broad AICore device support.
- Single Gradle project, version catalogs (`libs.versions.toml`) for dependencies.
- Detekt + ktlint pre-commit hooks.
- Unit tests: JUnit 5 + Turbine (coroutine flow testing) + MockK.
- Instrumented tests: minimal. Don't test what AICore does; test our integration.
- Matching engine: golden-file tests with hand-curated reminder/event/expected-fire fixtures.

---

## Open architectural questions (decide before v0)

1. **sqlite-vec vs in-memory vectors.** With <1000 reminders per user, in-memory is fine and simpler. Decision: **in-memory in v0**, migrate to sqlite-vec only if we ship a "shared family reminder pool" feature that pushes counts higher.
2. **Whisper.cpp model size.** `tiny.en` (39MB) covers English-only. `base` (74MB) covers multilingual at the cost of ~25% more inference time. Junaid uses both English and occasional Tamil/Hindi mixing. Decision: **base.q5 quantized**, multilingual.
3. **Embedding model.** `bge-small-en-v1.5` (133MB ONNX, 384-dim) vs `e5-small-v2` (133MB, 384-dim). Both perform similarly on STS. Decision: **bge-small-en-v1.5**, slight edge on retrieval-style tasks. Re-evaluate in v2 with real user data.
4. **Should the watch ever do ASR locally?** For cases where the phone is out of BT range. Whisper.cpp tiny.en runs on a Watch 6 but burns battery. Decision: **no in v0–v2**. Queue captures and transcribe on reconnect. Revisit in v3 if "watch-only" usage is meaningful.
