# End-to-End Plan

This is the build plan from empty repo to shipped v3. It defines phases, milestones, demo gates that block forward progress, dependencies between work, and a risk register.

The cadence is opinionated: **ship a runnable build at the end of every phase**, with the bare minimum to demonstrate the phase's thesis. We do not stockpile features for a big-bang release. Build-in-public posture means every phase produces shareable artifact.

## Phasing overview

| Phase | Name                          | Duration   | Thesis to prove                                              |
|-------|-------------------------------|------------|--------------------------------------------------------------|
| P0    | Repo & foundations            | Week 1     | The skeleton compiles, CI is green, modules are wired right  |
| P1    | Capture & store on phone      | Week 2     | I can talk to my phone, it transcribes, it saves a reminder  |
| P2    | Time + geofence triggers      | Week 3     | Reminders fire correctly on time and at locations            |
| P3    | Matching engine v0            | Weeks 4–5  | Semantic context matching feels magical (the make-or-break)  |
| P4    | App-context auto-firing       | Week 6     | Opening Blinkit fires grocery reminders without me speaking  |
| P5    | Watch companion               | Weeks 7–8  | Press button on watch, talk, reminder appears on phone+watch |
| P6    | Polish, gating, dogfood       | Weeks 9–10 | Battery and privacy targets met; ready for first 10 users    |
| P7    | Mascot & personality (v2)     | TBD        | Junnz becomes a character, not just a tool                   |

Phases P0–P6 are v1. P7+ is v2 and is intentionally not detailed here.

---

## P0 — Repo & foundations

**Thesis**: a developer can clone the repo, run one command, and have a phone APK + watch APK installed on their devices.

### Deliverables

- Repo created with the module layout from `ARCHITECTURE.md`
- Gradle Kotlin DSL, version catalog, convention plugins
- All modules created as empty shells with placeholder code that compiles
- CI configured (GitHub Actions or equivalent): lint, test, build phone APK, build watch bundle
- Basic README, contributing guide, license decision (proprietary closed-source for v1; revisit later)
- Hilt set up on phone; manual DI scaffolding on watch
- Compose previews working in both apps
- Crash reporter decision made and wired (or skipped for v0 per `SECURITY_AND_PRIVACY.md`)
- Detekt + Ktlint configured, baseline established
- A "smoke" UI in each app: tap a button, see a Toast / wear notification

### Out of scope

- No real features
- No data model wired up beyond placeholder entities
- No ML
- No background work

### Demo gate

A 30-second video showing: `./gradlew installPhoneDebug installWatchDebug`, both apps launching, smoke UIs reacting. CI green badge.

### Dependencies

None. This is the start.

---

## P1 — Capture & store on phone

**Thesis**: voice-driven capture works on phone alone. End-to-end: button tap → record → transcribe → parse → save → confirm.

### Deliverables

- `feature-capture` module: capture flow Activity
  - Single-purpose, opens on dedicated intent
  - Compose UI with mic state visualization, level meter, cancel/confirm
- `ml-asr` module: AICore implementation + Whisper.cpp fallback
  - Detect AICore at runtime
  - Whisper.cpp via JNI (use `whisper.cpp` C++ via NDK, single language EN+HI initial)
  - Audio capture via `AudioRecord` at 16kHz mono PCM
  - Recording cap 30s, hard-killed
- `data-room` module: full Room schema per `DATA_MODEL.md`
  - SQLCipher integrated, key in EncryptedSharedPreferences
  - All entities, DAOs, type converters
  - Migration v0→v1 (initial)
- `feature-reminders` module: reminder list + detail screens
  - List (Compose `LazyColumn`)
  - Detail (read-only initially, edit screen comes later in P1)
  - Empty state
- NLP heuristic parser (Kotlin) for capture parsing
  - 50+ regex patterns covering time/location/intent
  - Test corpus of 50 phrases with expected outputs
- `core-ui` design system primitives (colors, typography, basic composables)
- `core-logging` with PII redaction live

### Out of scope

- No matching engine (P3)
- No triggers fire (P2)
- No watch (P5)
- No embedding generation yet — store reminders without embeddings; will backfill in P3
- Voice retention is OFF by default (Tier 1, consistent with `SECURITY_AND_PRIVACY.md`)

### Demo gate

A 60-second video: developer opens phone app, taps capture, says "Remind me to buy fresh cream," confirms, sees reminder in list, taps it, sees parsed details (title="Buy fresh cream", no triggers because matching/triggers not built yet).

Pass criteria:
- Capture-to-saved time < 4 seconds for 5-second utterance
- Crash-free for 50 captures in a row
- Database survives uninstall→reinstall (encryption key recoverable on same device with same install) — actually no: reinstall wipes Keystore key, that's expected. Document: reminders do not persist across uninstall in v1.

### Dependencies

- P0 complete

### Risks

- **AICore availability**: not all dev devices have it. Whisper.cpp fallback must work or P1 stalls. Mitigation: integrate Whisper first, AICore second.
- **NDK build complexity**: Whisper.cpp needs CMake + NDK setup. Allocate a half-day for build system; have a public reference to `whisper.cpp` Android sample.

---

## P2 — Time + geofence triggers

**Thesis**: time and location reminders fire reliably. We are at parity with Google Keep on the table-stakes triggers.

### Deliverables

- `feature-trigger` module
  - `TimeTriggerScheduler`: AlarmManager + WorkManager combo
    - Exact alarms via `setExactAndAllowWhileIdle`
    - Receiver promotes to brief FGS only if it must do extra work
    - Recurrence handled by computing next occurrence on fire
  - `GeofenceTriggerManager`: GeofencingClient
    - Up to 20 active geofences per user
    - DWELL with 60s, ENTER/EXIT support
    - Re-registers on boot via `BOOT_COMPLETED` (with permission)
- Capture flow now writes triggers correctly:
  - "at 9 PM" → Time trigger
  - "when I'm at home" → Geofence trigger (using saved place "home")
- Place book: minimal UI to define named places (Home, Work, etc.)
  - Plus auto-create from natural language ("at home" → use device's most-frequent location, prompt user to confirm)
- Reminder edit screen: user can fix mis-parsed triggers, add/remove triggers
- Notification firing for reminders
  - Channel set up properly with description
  - Custom vibration pattern (foundation for mascot personality later)
  - Actions: "Did it" / "Snooze 1h" / "Dismiss"
- Permissions UX
  - `POST_NOTIFICATIONS` flow
  - `SCHEDULE_EXACT_ALARM` flow with rationale
  - `ACCESS_FINE_LOCATION` flow
  - `ACCESS_BACKGROUND_LOCATION` two-step flow

### Out of scope

- Semantic / app-context triggers (P3, P4)
- Watch integration (P5)
- Adaptive scheduling (post-v1)

### Demo gate

A 90-second video showing:
- Set a reminder for 2 minutes from now → it fires, on time
- Set a reminder "when I leave home" → walk out → fires (or a simulated geofence)
- Mark "Did it" → reminder marked completed
- Snooze → reminder re-fires after the snooze window

Pass criteria:
- Time triggers fire within 60s of intended for 100% of test runs (10 trials)
- Geofence triggers fire within 90s of physical transition for 90% of test runs (Android system limitation; not our problem to fix below 90%)
- Reminder doesn't fire twice for the same trigger event

### Dependencies

- P1 complete

### Risks

- **OEM background restrictions** (Xiaomi, Realme, Vivo, Samsung have aggressive killers): Document required user actions ("disable battery optimization for Junnz"). Provide an in-app deep link to the right system setting. Cannot fully solve in code.
- **Geofence reliability is OS-dependent**: Plan for ~10% miss rate as a fact of life. Don't over-engineer.

---

## P3 — Matching engine v0 (the differentiator)

**Thesis**: semantic context matching feels magical. The whole product hinges on this phase.

### Deliverables

- `shared-matching` module per `MATCHING_ENGINE.md`
  - `Stage 1: pre-filter`
  - `Stage 2: per-trigger matchers` (Time/Geofence done in P2; AppContext placeholder; Semantic full)
  - `Stage 3: score fusion`
  - `Stage 4: thresholding`
  - `Stage 5: dedup`
- `ml-embed` module
  - Gemini Nano embedding implementation (where available)
  - ONNX Runtime + `all-MiniLM-L6-v2` quantized fallback
  - `EmbeddingEngineProvider` selecting at runtime
- `data-vector` module
  - sqlite-vec integrated
  - Vector index lifecycle
  - kNN query API
- `EmbedReminderWorker`: embeds reminders async on creation/edit
- `BulkReembedWorker`: re-embeds all on model upgrade
- Manual context refresh in app: pull to refresh on reminder list runs a match pass over current location + foreground app, fires anything that should fire
- Voiced context announcement
  - New capture intent type: "context announcement" (via long-press capture button or specific voice prefix like "I'm now…")
  - Fed into `ContextSnapshot.voicedText`
  - Triggers an immediate match pass
- Anchor text construction (Option B from `MATCHING_ENGINE.md`): rule-based synonym expansion using a hand-curated dictionary
- Test corpus and CI assertions

### Out of scope

- Auto-fire on app-context (P4)
- Adaptive thresholds (post-v1)
- Cross-encoder reranking (post-v1)

### Demo gate — THIS IS THE GO/NO-GO

A 2-minute video:
- Create reminders over a few days: "buy fresh cream", "ask Sarah about her trip", "send the invoice to client X", "pick up dry cleaning"
- After a couple of days, in the app, hit the context announcement button and say: "I'm shopping at Blinkit"
- Within ~2 seconds, the cream reminder fires with reason "Semantic match: shopping at Blinkit ↔ buying groceries"
- Try another announcement: "I'm catching up with friends" — the Sarah reminder surfaces

Pass criteria — this is a *subjective* gate as much as an objective one:
- Founder dogfood for 14 days. At least 5 distinct semantic-context fires that the founder did not intend in advance but ended up acting on.
- At least one moment of "oh damn, that's exactly what I needed."
- False positive rate (fires marked "wasn't relevant"): < 30%.

**If the demo gate fails**: do not proceed to P4. Either retune (adjust thresholds, improve anchor expansion, improve query construction) or kill the differentiator and reposition the product.

### Dependencies

- P2 complete (need triggers + reminders to match against)

### Risks

- **Subjective magic is unpredictable.** The threshold tuning and anchor construction quality are the difference between "wow" and "meh." Allocate the full two weeks; do not compress.
- **Gemini Nano availability is narrow.** ONNX MiniLM must work on every dev device, period. Cap acceptance criteria on MiniLM-only behavior.
- **First-week dogfood is sparse.** A reminder pool of 5 doesn't exercise the engine. Seed the pool intentionally with diverse reminder types in week 1; let week 2 be organic.

---

## P4 — App-context auto-firing

**Thesis**: Junnz fires the right reminder when the user opens a relevant app, with no voice prompt needed.

### Deliverables

- `NotificationListenerService` integration
  - Allowlist of apps (default: Blinkit, Zepto, BigBasket, Amazon, Flipkart, Swiggy, Zomato, Dunzo)
  - User-editable allowlist in Settings
  - Reads only `packageName`, `postTime`, `category`
  - Stores observations in `context_observations` table with 1-hour rolling window
  - Triggers `MatchOnContextWorker` (debounced 30s)
- `Trigger.AppContext` matcher activated and fully integrated
- App category inference: package → category map (curated, ~50 entries) plus runtime fallback to Android's `Notification.CATEGORY_*` field
- Settings: enable/disable per-app, view recent observations
- Permission flow for notification listener access (system settings deep link, very clear copy)

### Out of scope

- Accessibility service (we deliberately do NOT use this — see `SECURITY_AND_PRIVACY.md`)
- Foreground app detection via UsageStatsManager (deferred — notification listener gives 80% of the signal at 20% of the privacy cost)

### Demo gate

A 60-second video:
- Setup: install Junnz, grant notification access, set 3 grocery reminders
- Open Blinkit, browse for 10 seconds
- Junnz fires all 3 grocery reminders with reason "you opened Blinkit"
- Open Spotify (out of allowlist) — no fires

Pass criteria:
- Auto-fire works for the 6 default apps on at least 2 different OEMs (Samsung, Pixel/stock Android).
- No fires from out-of-allowlist apps in any test.
- < 2% additional daily battery vs. baseline (notification listener is cheap; this should be easy).

### Dependencies

- P3 complete (matching engine must work for AppContext to plug in)

### Risks

- **Play Store review for notification listener**: high scrutiny. Prepare a justification doc and walkthrough video before submission.
- **Some users won't grant notification access**: that's fine. App-context falls back to manual context announcement (which is voiced and works without listener access). Make this fallback discoverable.

---

## P5 — Watch companion

**Thesis**: pressing a button on the Galaxy Watch and talking is faster, more natural, and sufficient on its own. Watch becomes the primary capture surface.

### Deliverables

- `app-watch` module: full Wear OS app
  - Capture Activity: launched by hardware button long-press OR Tile tap
  - Mic permission flow (one-time, sticky)
  - Audio capture on watch (`AudioRecord`, same params as phone)
  - Stream audio to phone via `ChannelClient`
  - Receive parsed reminder confirmation, show on screen
  - Local Room DB for offline capture queue
- `shared-sync` module: full sync protocol with CBOR serialization
  - Channel encryption (per `SECURITY_AND_PRIVACY.md`)
  - Protocol version negotiation
- Phone: handle incoming watch captures
  - Receive audio chunks, run ASR + parse + save (same pipeline as phone capture)
  - Send confirmation back to watch
- Tile (watchface complication)
  - Static "Junnz" tile that, when tapped, opens capture
  - Shows count of pending reminders (optional, low-priority)
- Ongoing Activity API: when a reminder fires while watch is on wrist, surface as a wearable notification with "Did it / Snooze / Dismiss" actions
- Hardware button mapping
  - Long-press home button → open capture (where OS allows configuration)
  - Document fallback: complication tap if button can't be remapped on user's model
- Pairing flow
  - First-launch on watch checks for paired Junnz phone install
  - Phone sends `ChannelKey` over OS-encrypted MessageClient
  - Watch confirms, both sides ready
- Battery profile compliance per `BATTERY_CONSTRAINTS.md`
  - 24h watch idle test: < 3.5%
  - 24h watch active test: < 5%

### Out of scope

- Custom wake word (deferred to v3 if at all)
- Watchface mascot rendering (deferred to mascot phase)
- Independent watch operation (no LTE; phone presence required for capture parsing)

### Demo gate

A 90-second video:
- Wear watch
- Long-press home button (or tap complication where OS doesn't allow remap)
- Watch UI appears with mic indicator
- Speak: "Remind me to call mom tomorrow at 7 PM"
- Within 3 seconds: watch shows "Got it: Call mom tomorrow 7 PM"
- Phone shows the reminder in list
- Tomorrow at 7 PM: both phone and watch fire

Pass criteria:
- End-to-end (button to confirmation on watch) < 4 seconds for 5s utterance
- Works with phone in pocket / on desk (Bluetooth range)
- Recovers gracefully when phone is out of range (capture is queued, drained on reconnect)

### Dependencies

- P3 complete (matching) — capture from watch should benefit from semantic triggers immediately
- P4 complete (app-context) — fires on phone reach the watch as wearable notifications

### Risks

- **Hardware button remap availability is per-model.** Some Galaxy Watch models lock long-press home to recents. Strategy: complication-first, button-as-bonus.
- **Bluetooth pairing edge cases** (multi-phone, watch unpaired, etc.) are a rabbit hole. Define a minimum viable: one phone, one watch, one user. Refuse to operate in stranger configurations and surface a clear error.
- **Wear OS audio recording reliability varies.** Test on 2+ Galaxy Watch models before declaring P5 done.

---

## P6 — Polish, gating, dogfood

**Thesis**: it's ready for 10 real users (not just the founder).

### Deliverables

- All battery gates from `BATTERY_CONSTRAINTS.md` met on dogfood devices
- All privacy and permission flows reviewed and copy-edited
- Privacy policy published (English + Hindi)
- Data export flow shipping
- "Delete all my data" flow shipping
- Onboarding (3 screens max): what Junnz does, permissions you'll need, get started
- Settings polished
- Notification copy reviewed (every notification should be specific, not generic)
- Crash reporting decision finalized and implemented (or explicitly skipped, documented)
- Internal beta on Play Console with 10 manually-invited users
- A feedback collection channel (in-app form OR a Telegram group OR direct email — not analytics)

### Out of scope

- Public launch (post-P6)
- ASO/marketing
- Mascot (P7)
- Pricing / monetization (post-launch decision)

### Demo gate

A walkthrough video of a complete first-time user experience:
- Install
- Onboarding
- Grant permissions
- Create first reminder by voice
- Wait for it to fire
- Mark done
- Open settings, see data export, see delete-everything

Pass criteria:
- 10 invited users complete onboarding without founder hand-holding
- After 1 week, ≥ 6 users have created ≥ 5 reminders each
- After 1 week, ≥ 4 users report at least one "magic moment" of semantic match
- Zero P0 bugs (crashes, data loss, privacy violations)
- Battery budgets met on all 10 user devices

### Dependencies

- P5 complete

### Risks

- **OEM-specific battery saver kills the FGS** for some users. Document workarounds; consider an in-app "is my Junnz running properly?" diagnostic screen.
- **Real-user voice variance is much higher than founder voice.** ASR may fail more often. Have a fallback: text edit always available before save.
- **Real-user reminder text is messier** than test corpus. Anchor expansion may need a v0.2 iteration mid-P6.

---

## P7+ — Future phases (named, not detailed)

- **Mascot & personality**: visual character, mood states, voice/copy treatment, watchface complication art
- **Forgetful tax & weekly report**: the shareable content engine (per the 1+4+5 framing)
- **Streaks & roasts**: gamification loop with calibrated tone
- **iOS port**: KMP shared modules become real cross-platform; SwiftUI + WatchKit shells on top
- **Adaptive thresholds**: per-user calibration via logged outcomes
- **Cross-encoder reranking**: precision improvement on semantic matching
- **Custom wake word**: if user demand justifies battery cost, Porcupine integration
- **Multi-device sync**: same user, multiple phones (low priority — most users are single-phone)
- **Sharing reminders**: send a reminder to another Junnz user (high social value, high privacy cost; thoughtful design needed)

---

## Cross-phase work tracks (run continuously)

### Documentation track

- Public README of the repo (separate from this internal README)
- API doc generation via Dokka, published as static site
- Privacy policy living document, version-controlled
- CHANGELOG starting at first internal release

### Quality track

- Unit test coverage target: 70% on `shared-*`, 50% on `feature-*`
- UI tests for capture and fire flows
- Benchmarks for matching engine in CI
- Lint baselines kept tight; new violations block merge

### Build-in-public track

- Each phase ends with a public post (X + LinkedIn) showing the demo video
- Code is private; videos and commentary are public
- Track engagement; adjust storytelling

### Research track (background)

- Read up on prospective memory literature for the "forgetful tax" numbers
- Track Wear OS API updates (every Google I/O)
- Track AICore expansion to more devices
- Track Indian DPDP Act implementing rules as they're published

---

## Risk register (consolidated)

| Risk                                                       | Likelihood | Impact | Mitigation                                              |
|------------------------------------------------------------|------------|--------|---------------------------------------------------------|
| AICore not on most devices                                 | High       | Med    | ONNX MiniLM fallback works universally                  |
| Whisper.cpp NDK integration burns time                     | Med        | Low    | Reference public sample; allocate buffer in P1          |
| OEM battery savers kill FGS                                | High       | High   | Document workarounds; in-app diagnostic                 |
| P3 magic doesn't land                                      | Med        | Critical | Don't proceed to P4; retune or pivot                  |
| Notification listener Play Store review fails              | Low        | High   | Prepare justification; have voice-only fallback path    |
| Watch button remap unavailable on user's model             | High       | Med    | Complication-first, button-bonus                        |
| Real-user voice variance breaks ASR                        | Med        | Med    | Always allow text edit before save                      |
| Bluetooth pairing edge cases                               | Med        | Med    | Refuse multi-device configs; clear error UX             |
| Founder dogfood is too narrow a sample                     | High       | Med    | Open beta to 10 users at end of P6                      |
| DPDP rules change mid-build                                | Low        | High   | Stay over-compliant (GDPR-equivalent posture)           |
| Embedding model upgrade breaks vector compatibility        | Med        | Med    | `BulkReembedWorker` already in design                    |
| Galaxy Watch Wear OS version fragmentation                 | Med        | Med    | Test on 2+ models; document min OneUI Watch version     |

---

## What "v1 done" looks like

- Phone APK + watch APK installable, paired, working
- 10 internal beta users, 7+ engaged after 14 days
- Battery within budget on majority of test devices
- Privacy policy published, DPDP-compliant
- All seven docs in this set kept current
- Public-facing landing page with demo video
- Decision made on monetization approach for v2 (free? freemium? paid?)

When v1 is done, the question becomes: do we focus on mascot/personality (v2) or on broadening the user base? That decision is a function of v1 metrics, not predetermined.
