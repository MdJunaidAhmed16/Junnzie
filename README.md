<div align="center">

# 🌿 Junnz

### Context-Aware Smart Reminders for Phone + Wear OS

*Capture on your wrist. Get reminded at the right moment — not just the right time.*

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Wear OS](https://img.shields.io/badge/Wear%20OS-1A8C5A?style=for-the-badge&logo=wearos&logoColor=white)
![Android](https://img.shields.io/badge/Android-34A853?style=for-the-badge&logo=android&logoColor=white)
![Hilt](https://img.shields.io/badge/Hilt%20DI-2C4F7C?style=for-the-badge)
![Room](https://img.shields.io/badge/Room-FF7043?style=for-the-badge)

</div>

---

## ✨ Overview

**Junnz** rethinks reminders around *context* instead of just the clock. The **watch** is a frictionless quick-capture device — speak a reminder in one tap — while the **phone** is the intelligent dashboard that decides *when* it actually matters.

A spoken sentence like *“remind me to buy cucumber when I open Blinkit”* is transcribed, parsed on-device into a structured reminder, and later fired the moment you actually open that shopping app — turning a passive to-do into a timely nudge.

> Built end-to-end as a solo project: shared Kotlin domain logic, a Wear OS capture app, and an Android intelligence app with four distinct context-trigger engines.

---

## 📱 Phone  the intelligence dashboard

<img
  width="1672"
  height="941"
  alt="Phone screens"
  src="https://github.com/user-attachments/assets/a9d00cd6-a6e5-47e0-bb51-f5b080e96d6f"
/>

<div align="center">
  <sub><b>Home / Smart Reminders</b> · <b>Create Reminder</b> · <b>Context Reminder Overlay</b> · <b>Nearby Suggestion / Explore</b></sub>
</div>

## ⌚ Wear OS  the quick-capture companion

<img
  width="1672"
  height="941"
  alt="Wear OS screens"
  src="https://github.com/user-attachments/assets/de342081-14e2-4716-92b5-aae39f074f9b"
/>

<div align="center">
  <sub><b>Watch Home</b> · <b>Voice Capture</b> · <b>Smart Parsing</b> · <b>Triggered Reminder</b> · <b>Nearby Suggestion</b></sub>
</div>

---

## 🧠 How it works

```
🎙️  Voice capture        →   on-device speech-to-text
🧩  Intent parsing        →   transcript → { task + structured trigger }
🗄️  Persist + schedule    →   Room + alarms / geofences / context watchers
⚡  Context matching      →   shared MatchingEngine evaluates live signals
🔔  Fire                  →   the right reminder, at the right moment
```

The natural-language parser is pure Kotlin in a **shared module**, so the exact same logic runs on the phone and the watch — and is fully unit-tested with a fixed clock.

---

## 🎯 Four ways a reminder fires

| Trigger | Example phrase | Under the hood |
|---|---|---|
| ⏰ **Time** | *“…tomorrow at 9:30 AM”* | `AlarmManager` exact alarms with Done/Snooze actions + reboot rescheduling |
| 📍 **Location** | *“…when I reach the supermarket”* | Geocoding (label → coordinates) + Play Services **Geofencing API** |
| 📲 **App context** | *“…when I open Blinkit or Zepto”* | `UsageStatsManager` foreground detection + `NotificationListenerService` |
| 🧬 **Semantic** | *“…when I’m talking about travel”* | Text **embeddings** + cosine similarity through a shared `MatchingEngine` |

All four are routed through a single matching brain with per-reminder cooldowns and tunable thresholds.

---

## 🏗️ Architecture

Multi-module, clean separation of concerns, dependency-injected throughout.

```
┌───────────────┐     ┌───────────────────────────┐     ┌───────────────┐
│   app-wear    │     │          shared           │     │   app-phone   │
│  (Wear OS)    │────▶│  domain models · IPC      │◀────│  (Android)    │
│  capture UI   │     │  ReminderParser           │     │  dashboard    │
│  audio → PCM  │     │  MatchingEngine           │     │  triggers     │
└───────────────┘     └───────────────────────────┘     └───────────────┘
        │                Data Layer over Wearable                │
        └──────────────────── MessageClient ─────────────────────┘
```

- **Pattern:** MVVM with unidirectional state (`StateFlow` → Compose)
- **DI:** Hilt across ViewModels, repositories, services, and broadcast receivers
- **Persistence:** Room (reminders) + DataStore (settings)
- **Async:** Kotlin Coroutines & Flow end-to-end
- **UI:** 100% Jetpack Compose + Wear Compose, custom design system, **theme-aware light/dark mode** via `CompositionLocal`

---

## 🛠️ Tech stack

**Language & UI:** Kotlin · Jetpack Compose · Wear Compose · Material 3
**Architecture:** MVVM · Hilt (Dagger) · Multi-module · Coroutines · Flow
**Data:** Room · DataStore Preferences · Kotlinx Serialization
**Platform APIs:** AlarmManager · Geofencing API · UsageStatsManager · NotificationListenerService · Wearable Data Layer
**Intelligence:** On-device NLP parser · Text embeddings (local + Google `text-embedding-004`) · cosine-similarity matching
**Quality:** JUnit unit tests · Gradle (Kotlin DSL, version catalog)

---

## 🔬 Engineering highlights

- **Offline-first by design** — the NLP parser and lexical embeddings run fully on-device with **no network or model files**; cloud Speech-to-Text and embeddings are a drop-in upgrade gated by an API key.
- **One shared brain** — app-context (by package) and semantic (by embedding) matching are unified behind a single `MatchingEngine`, eliminating duplicate firing paths and sharing one cooldown.
- **Robust scheduling** — exact alarms degrade gracefully when the OS withholds the exact-alarm capability, and all reminders are re-armed after device reboot.
- **Test-backed core** — the transcript-to-trigger parser is verified with deterministic unit tests covering time, location, app, and semantic cases.
- **Production-grade UX** — floating dock navigation, ambient gradients, animated capture visualizers, persisted theme + preferences, and a full dark theme.

---

## 🚀 Running locally

```bash
# 1. Clone, then copy the local config template
cp local.properties.sample local.properties

# 2. Set your Android SDK path in local.properties
#    (API keys are optional — the app runs fully offline without them)

# 3. Open in Android Studio and run the `app-phone` and `app-wear` configurations
```

> Optional: add `GOOGLE_SPEECH_API_KEY` (cloud ASR) and `GOOGLE_EMBEDDING_API_KEY`
> (semantic embeddings) to `local.properties` to enable the cloud-backed paths.

---

## 📌 Status

Personal project. The full pipeline is functional and verified by build + unit tests: voice/text capture → on-device parsing → all four trigger types → alarms, geofences, app-context detection, and semantic matching, with persisted settings and live dark mode.

<div align="center"><sub>Designed & engineered by <b>MD Junaid Ahmed</b> · Kotlin · Jetpack Compose · Wear OS</sub></div>
