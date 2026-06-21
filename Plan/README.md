# Junnz

> A context-aware reminder system for Wear OS + Android. Speak it on your wrist, forget it, and let the phone catch the moment when it matters.

---

## What this is

Junnz is a voice-first, context-triggered reminder app. Three trigger types:

1. **Time** — standard scheduled reminders.
2. **Location** — geofenced reminders (cross this place → fire).
3. **Semantic context** — *the differentiator*. When you're doing something *like* the thing a reminder anticipated, Junnz fires.

Examples of (3):
- You said "remind me to buy fresh cream." Later, you tell Junnz "I'm ordering from Blinkit" — Junnz flashes pending grocery reminders. Even better: Junnz detects Blinkit opening on your phone and fires automatically.
- You said "remind me to ask Mom about the EPF papers." Later, you say "calling Mom" — Junnz surfaces it.
- You said "remind me to taste the biryani at Paradise." You walk into Paradise (geofence) *and* Junnz also recognizes "we're at a restaurant" semantically, even if the geofence was never set.

The product is **wrist-first**: long-press the watch button, talk, done. Phone is the brain; watch is the capture and surface device.

## Why this is different

Existing reminder apps support time and location. Almost none support **semantic intent** as a trigger. That's the wedge. Everything else (mascot, streaks, watchface presence) is layered on top later.

## Stack

| Layer | Choice |
|---|---|
| Phone app | Kotlin + Jetpack Compose + Hilt + Room + DataStore + WorkManager |
| Watch app | Kotlin + Compose for Wear OS + Horologist |
| Shared logic | Kotlin Multiplatform (KMP) module — domain models, matching engine, embedding utilities |
| ASR (primary) | Gemini Nano via Android AICore (on-device) |
| ASR (fallback) | `whisper.cpp` via JNI for devices without AICore |
| Embeddings | ONNX Runtime Mobile + a small sentence embedding model (e5-small / bge-small quantized) |
| Vector store | sqlite-vec extension on Room, or in-memory L2-normalized arrays (reminder counts stay in the hundreds) |
| Phone↔Watch IPC | Wearable Data Layer API (DataClient + MessageClient) |
| Background execution | Foreground service (matching loop) + WorkManager (periodic maintenance) + GeofencingClient (passive geofences) + NotificationListenerService (app context) |
| Crash/analytics | Self-hosted (no Firebase). Sentry self-hosted is acceptable. Telemetry is opt-in. |

## Project structure

```
junnz/
├── app-phone/              # Android phone application
│   ├── src/main/kotlin/com/junnz/phone/
│   │   ├── ui/             # Compose UI
│   │   ├── service/        # Foreground service, NotificationListener, Tile providers
│   │   ├── ml/             # ASR, embeddings, AICore wrappers
│   │   ├── geofence/       # Geofence registration & callbacks
│   │   ├── trigger/        # Trigger evaluators (Time, Geofence, Semantic, App)
│   │   ├── sync/           # Wearable Data Layer client
│   │   └── di/             # Hilt modules
│   └── src/main/AndroidManifest.xml
│
├── app-wear/               # Wear OS application
│   ├── src/main/kotlin/com/junnz/wear/
│   │   ├── ui/             # Compose for Wear UI
│   │   ├── tile/           # Tile providers
│   │   ├── complication/   # Watchface complications
│   │   ├── capture/        # Audio capture + chunked transfer to phone
│   │   └── sync/           # Wearable Data Layer client
│   └── src/main/AndroidManifest.xml
│
├── shared/                 # Kotlin Multiplatform module
│   └── src/commonMain/kotlin/com/junnz/shared/
│       ├── domain/         # Reminder, Trigger, ContextEvent, FireEvent — pure data
│       ├── matching/       # Matching engine (deterministic, testable)
│       ├── embedding/      # Embedding interfaces (impls injected per platform)
│       └── time/           # Time utilities (kotlinx-datetime)
│
└── docs/                   # This directory
```

## Phased roadmap

| Phase | Scope | Time |
|---|---|---|
| v0 | Phone-only MVP. Long-press FAB → talk → reminder. Time, geofence, and semantic triggers all working. **Validates that semantic matching feels magical.** | ~3 weeks |
| v1 | Watch companion. Long-press watch button → talk. Reminders fire on watch with haptic. Phone↔watch sync. | +2 weeks |
| v2 | App-context auto-firing. NotificationListenerService + foreground app detection → fire matching reminders without user voicing context. | +2 weeks |
| v3 | Mascot, streaks, weekly report (the share/retention engine). Optional custom wake word for power users. | +3 weeks |

v0 is the gate. If semantic matching doesn't feel magical when Junaid uses it on himself for two weeks, the rest doesn't matter — kill the project, don't build the watch.

## Getting started

Prerequisites: Android Studio Ladybug+, JDK 17, Android SDK 34, Wear OS emulator paired with a phone emulator.

```bash
./gradlew :app-phone:installDebug
./gradlew :app-wear:installDebug
```

For ASR development without AICore, the `whisper.cpp` JNI module ships a small (`tiny.en`, 39MB) model bundled in `app-phone/src/main/assets/`.

## Document index

- **ARCHITECTURE.md** — system design, modules, data flow, IPC contracts.
- **SECURITY_AND_PRIVACY.md** — privacy-first design, DPDP/GDPR posture, threat model, permissions strategy.
- **BATTERY_CONSTRAINTS.md** — watch battery budget, Doze handling, foreground service rules, what we *won't* do.
- **DATA_MODEL.md** — Room schema, KMP domain types, vector storage, sync protocol.
- **MATCHING_ENGINE.md** — the differentiator. Embedding pipeline, scoring, threshold tuning, false-positive control.
- **END_TO_END.md** — phased build plan with milestones, success criteria, and the exact sequence Claude Code should build in.
