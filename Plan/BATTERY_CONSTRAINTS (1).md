# Battery Constraints

The product's value proposition — "always ready to capture, always ready to fire at the right moment" — is in direct tension with battery life, especially on the watch. This document defines the rules we live by, the budgets we set, and the things we explicitly do *not* do.

---

## Hardware budgets

### Watch (Galaxy Watch 6/7, ~300mAh typical)

The watch is the more constrained device. Total daily budget if Junnz is to feel "free" on top of normal usage:

| Activity | Budget | Notes |
|---|---|---|
| Active capture sessions | 5 sessions/day @ ~30s each | User-initiated. Mic + BT transmit. ~2.5 minutes of active mic per day. |
| Tile rendering | 20 renders/day | Triggered when user views the tile. |
| Data layer sync (incoming) | 50 messages/day | Reminder list updates, fire events. |
| Complication updates | Per `getComplicationData` call from watchface | We do not push; we let the watchface ask. |
| Background work | **None.** | The watch app does no background work. No services, no workers, no continuous sensors. |

**Total estimated daily drain from Junnz on watch: ≤2% of battery.** We will measure this in v1 and adjust.

### Phone (modern Android, 4000–5000mAh typical)

| Activity | Budget |
|---|---|
| Foreground service active hours | ~6 hours/day average (only while semantic-triggered reminders are pending) |
| ASR runs | 5 transcriptions/day @ ~1s each on AICore, ~3s on Whisper |
| Embedding runs | 10 embeddings/day @ ~50ms each |
| Notification listener events processed | 100/day average (most filtered out by allowlist) |
| Foreground app polls | 30s interval × 6 hours = 720 polls/day, each <5ms |
| Geofence registrations | Up to 100 active, OS-managed |
| Location requests | None continuous. Geofencing only. |

**Total estimated daily drain from Junnz on phone: ≤3% of battery on a typical day.**

---

## What we do not do

These are non-negotiable. They've been considered and rejected.

| Anti-pattern | Why we don't |
|---|---|
| Continuous mic on the watch | Catastrophic battery cost. Even DSP-assisted always-on listening (Porcupine) is a 10–20% daily drain. v0–v2 ships button-to-talk only. v3 may add wake word as opt-in with full disclosure of cost. |
| Continuous mic on the phone | Same reason, plus privacy story dies. |
| Foreground service always running | Service lifecycle is bound to existence of active semantic triggers. If the user only has time-and-geofence reminders, the service is not running. |
| Continuous location updates | Use `GeofencingClient` (OS-batched) instead. |
| `WakeLock` held outside of ASR runtime | Partial wake locks are scoped to an ASR session and released in a `try/finally` block. Any held wake lock that lives more than 5 seconds is a bug. |
| Polling alarms | We use `setExactAndAllowWhileIdle` per individual reminder time, not a periodic check loop. |
| `JobScheduler` periodic jobs at <15min intervals | If we need it, we use WorkManager with the OS minimum (15 min) and accept the schedule. |
| `NotificationListenerService` doing work on the binder thread | Listener emits to a `SharedFlow`; processing happens on a background dispatcher with rate limiting. |
| Embedding the user's voice in real-time as they speak | We embed once after transcription. No streaming embedding. |
| Re-embedding existing reminders on app start | Embeddings are cached in Room. We re-embed only on text edit. |
| Pulling reminders from the watch on view | Watch reads from its DataItem cache. Phone pushes updates. No pulls. |

---

## Foreground service rules

The phone foreground service is the largest single battery contributor in the design. Its lifecycle is:

```
START conditions (any of):
  - Boot completed AND there are active reminders with semantic-context triggers
  - User adds a new reminder with a semantic-context trigger
  - User says "I'm doing X" (CONTEXT_ANNOUNCE intent received)
  - Watch capture session begins

STOP condition:
  - There are no reminders with semantic-context or companion-app triggers in non-fired state
  AND
  - No active capture session
  AND
  - Service has been idle for ≥5 minutes
```

The 5-minute idle window is to avoid thrashing if a user is rapidly creating and dismissing reminders.

### Service notification

Required by Android. Minimal and informative:
- Title: "Junnz is listening for the right moment"
- Text: dynamic — e.g. "3 reminders on standby" or "Watching for groceries trip" if a single trigger is dominant
- Action: "Pause matching for 1 hour" — sets a flag, stops semantic matching, keeps service alive only for time/geofence flow

User can hide this from the lockscreen but not from the notification shade (Android requirement). We minimize visual noise: no large icon, no expandable content.

### Doze and App Standby

Junnz must work in Doze. Specific behaviors:

- **Time triggers** use `setExactAndAllowWhileIdle` — fires through Doze (limited to once per 9 minutes per app, which is fine because user-set reminder times are rarely closer than that).
- **Geofence triggers** are OS-managed; Doze does not block them.
- **Semantic triggers** require the foreground service. Doze-Light does not stop foreground services. Doze-Deep can defer network and CPU but not foreground services.
- **NotificationListenerService** continues to receive callbacks during Doze.
- **WorkManager periodic jobs** are deferred during Doze; we tag maintenance jobs with `setExpedited` only when essential (rare).

If the user puts the device in Battery Saver mode, we comply: matching semantic triggers continues but at a lower frequency (process events at most once per 60s instead of immediately). The user is told this in Settings.

### Battery optimization exemption

We **do not** ask for `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. That's a Play Store policy violation for most use cases and a UX red flag. Junnz works under normal Doze rules; if it doesn't, that's a bug.

---

## Watch capture flow battery cost

The single most expensive thing the watch does is a capture session. Cost breakdown for a 30-second session:

| Component | Cost |
|---|---|
| `AudioRecord` at 16kHz mono | ~30mAh-equivalent if held for an hour; for 30s, negligible (~0.25 mAh) |
| Bluetooth transmit of ~480KB audio | ~0.5 mAh |
| Compose UI updates @ 30fps for 30s | ~0.3 mAh |
| Haptic on capture-end | ~0.05 mAh |
| **Total per session** | **~1.1 mAh** |

At 5 sessions/day, that's ~5.5 mAh, or ~1.8% of a 300mAh watch. Within budget.

### Capture optimizations

- **Audio is sent in 1-second chunks**, not buffered for the full session. This lets the phone start ASR earlier (parallel to capture continuing) and reduces the worst-case loss if BT drops mid-capture.
- **Compose UI uses a static layout** with one animating element (level meter). No recompositions outside that.
- **Capture activity is its own task** so it can be killed independently when the session ends.
- **Microphone is released on the same thread that started it** to avoid the audio HAL holding power state.

### Audio compression

Question: should we compress audio before BT transmit?

- Raw 16kHz 16-bit mono PCM = 32KB/s. 30-second capture = 960KB.
- Opus at 16kbps would compress this to ~60KB. ~16x reduction in BT transmit time.
- BUT Opus encoding burns CPU on the watch — roughly equal to the BT transmit savings for 30s clips.

Decision: **send raw PCM in v1.** Revisit if we see real BT contention or transmit time issues. Simpler is better here.

---

## Foreground app monitoring cost

When the foreground service is running, we sample `UsageStatsManager.queryEvents` every 30 seconds:

```kotlin
private suspend fun pollForegroundApp() {
    while (currentCoroutineContext().isActive) {
        delay(30.seconds)
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastPollAt, now)
        // ... emit ContextEvent if foregrounded app is in allowlist
        lastPollAt = now
    }
}
```

This is event-batched — we don't poll continuously, we ask "what happened since last poll." Cost per poll: <5ms CPU, negligible memory. 30s interval is a tradeoff: lower means faster reactions to "I just opened Blinkit," higher means less battery. 30s feels right; reactions feel snappy enough because most users don't immediately complete a task within 30 seconds of opening an app.

**Adaptive polling:** if no foreground change has been detected in 5 consecutive polls, we extend to 60s. Reset to 30s on any change. This handles "phone is sitting idle" and "user is in the middle of using one app for a long time."

---

## Embedding and ASR scheduling

### ASR

- Runs only in response to a capture session ending. Never speculative.
- AICore path: ~500ms for a 30-second clip. Negligible battery.
- Whisper.cpp path: ~3s for a 30-second clip on a Snapdragon 8 Gen 2. Held wake lock for the duration. ~5 mAh per run.
- We do not run ASR while battery <15% unless user explicitly requests.

### Embeddings

- Run on reminder save and on context-announce utterance.
- ONNX Runtime with quantized bge-small: ~50ms per embed.
- Embeddings are cached in Room. Re-run only on text edit.
- Batched: when the matching engine is evaluating multiple reminders, embeddings are loaded from cache, not recomputed.

---

## WorkManager jobs

Periodic maintenance work scheduled via WorkManager:

| Job | Interval | Constraints |
|---|---|---|
| `EmbeddingCleanupWorker` | Daily | Battery not low, charging or >50% battery |
| `GeofenceReconciliationWorker` | Every 6 hours | None |
| `ContextEventPruneWorker` | Daily | None (small DB op) |
| `TelemetryFlushWorker` | Daily | Network available, charging preferred |
| `FireHistoryAggregateWorker` | Daily | Charging |

All jobs respect Doze. None requires expedited execution. None runs more often than the user opens the app on a typical day.

---

## Boot behavior

On `BOOT_COMPLETED`:

1. Re-register all geofences from Room (geofences don't survive reboot)
2. Re-schedule all time alarms from Room
3. Check if any reminder requires the foreground service; if yes, start it
4. Otherwise: do nothing. App is dormant until user opens it or a trigger fires.

We do **not** start the foreground service on boot if there are no semantic-context reminders. We do **not** poll for state on boot. We do **not** trigger any network activity on boot.

The boot receiver completes in <500ms typical.

---

## Battery telemetry (opt-in)

If the user has opted into telemetry, we record (locally first, flushed daily):

- Foreground service uptime per day
- Number of capture sessions
- Number of fires per trigger type
- Average ASR latency
- Average embedding latency
- Number of NotificationListener events processed (count only, no content)

These metrics are **anonymous** and used to detect regressions (e.g. "v1.4 increased average uptime by 30%, what changed?"). They never include reminder content, location, or app names. The only "app name" we'd ever record is package count, e.g. "the listener processed 14 events from 6 distinct allowlisted packages today" — never which packages.

---

## Profiling and verification

Before each release:

1. **Battery Historian** trace over a 24-hour developer session. Required to land a release: ≤3% phone drain, ≤2% watch drain attributable to Junnz.
2. **Doze testing**: forcibly enter Doze (`adb shell dumpsys deviceidle force-idle`), confirm time alarms still fire and geofences still trigger.
3. **Background restrictions testing**: enable "Restricted" battery setting on the app, confirm core trigger flows (time, geofence) still work. Semantic matching is allowed to degrade in this mode (we tell the user).
4. **Bluetooth-disconnected testing**: confirm watch captures queue and replay on reconnect, confirm phone matching continues when watch is disconnected.

---

## Battery-related user-visible features

In Settings, the user sees:

- **Pause matching** — pauses semantic + companion-app triggers. Time and geofence still work. Toggleable, with optional 1h/4h/until-tomorrow timers.
- **Battery saver behavior** — explainer of what changes when device battery saver is on.
- **Junnz's battery use today** — reads from `BatteryStats` API, shows our app's contribution.
- **Diagnostic mode** (hidden, dev menu) — dumps a 24h activity log to `Documents/junnz-diagnostic.txt` for debugging.

---

## Open questions

1. **Should the phone use Bluetooth Low Energy advertising to detect "watch is in range" without active connection?** This would let us decide whether to run reminders that require the watch (e.g. haptic-only fires). Current answer: no, the existing Wearable Data Layer "node connected" callback is sufficient.
2. **Wake lock during BT transmit on watch?** Current answer: no. The audio capture itself keeps the watch awake. BT transmit is fast enough (1s chunks) that letting the watch sleep between chunks would create reconnect overhead.
3. **Should `ForegroundAppMonitor` use `AccessibilityService` instead of `UsageStatsManager` for faster reactions?** Current answer: no, in v0–v2. AccessibilityService is more capable but draws Play Store scrutiny. UsageStatsManager + 30s polling is good enough.
