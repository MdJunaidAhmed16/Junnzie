# Security and Privacy

Privacy is part of the product, not an afterthought. The differentiator (semantic context matching using personal reminders + app activity + location) is exactly the kind of feature that gets users worried — and rightly so. The design philosophy is: **process locally, store locally, encrypt at rest, transmit nothing by default**.

This document covers principles, controls, threat model, compliance posture (DPDP Act 2023 for India, GDPR-readiness for EU users), and concrete engineering rules.

---

## Principles

1. **On-device by default.** ASR, embeddings, intent classification, and matching all run on-device. Cloud is opt-in, scoped, and never default for any user-content path.
2. **Data minimization.** We do not log notification content, audio, or transcripts longer than necessary to act on them. We retain only the structured `Reminder` and minimal `ContextEvent` history.
3. **No cloud sync of reminders in v0–v2.** Multi-device sync, if added, is end-to-end encrypted with a user-held key.
4. **No third-party analytics SDKs.** No Firebase, no Mixpanel, no Amplitude. If we do crash reporting, it's self-hosted Sentry with PII scrubbing.
5. **Transparent permissions.** Each sensitive permission is preceded by an in-app explanation screen that says, in plain English, what we do with it and what we don't.
6. **User controls everything.** Export all data, delete all data, revoke any permission, opt out of any feature — all reachable from one Settings screen.

---

## Data classification

| Data | Sensitivity | Where | Retention |
|---|---|---|---|
| Reminder text (user-spoken) | High | Room (encrypted) | Until user deletes or reminder is fired + 30 days |
| Reminder embeddings | High (re-identifying) | Room (encrypted) | Same as reminder |
| Audio captures (raw PCM) | Highest | RAM only | Discarded after transcription. Never written to disk. |
| Transcripts (interim) | High | RAM only | Discarded after intent processing. |
| ContextEvents (app foregrounded, notification posted) | Medium | Room (encrypted) | 7 days rolling, for matching debug + cooldown |
| Notification *content* | Highest | RAM only | Discarded after a single matching pass. **Never persisted.** |
| Geofence locations | High | Room (encrypted) | Until reminder deleted |
| Foreground-app history | Medium | Room (encrypted) | 7 days rolling |
| Fire events (history of what fired when) | Medium | Room (encrypted) | 30 days, for the user's "weekly report" feature |
| User identifier | None | Local install ID (UUID), never sent | Forever, until uninstall |
| Crash reports (opt-in) | Low (after scrubbing) | Self-hosted Sentry | 90 days |

---

## On-device processing guarantees

The following operations **never touch a network** in v0–v2:

- Audio capture and transcription
- Intent classification
- Slot extraction (time, place, item)
- Embedding generation
- Vector similarity search
- Geofence registration (GeofencingClient is an on-device API; Google does the location intersection without our service touching it)
- Notification listener processing
- Reminder firing and snooze

Network access in v0 is limited to:
- Application updates (Play Store)
- Telemetry (opt-in, off by default)
- Crash reports (opt-in, off by default)

We declare this as an explicit guarantee in the privacy policy. We back it up by **using `network_security_config` to restrict allowed domains** in release builds.

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <!-- Only telemetry/crash domains are reachable -->
    <domain-config>
        <domain includeSubdomains="true">telemetry.junnz.app</domain>
    </domain-config>
</network-security-config>
```

In debug builds we leave this open for development.

---

## Encryption at rest

### Database

Room SQLite database is encrypted with **SQLCipher** (`net.zetetic:android-database-sqlcipher`). The database key is generated on first launch and stored in **Android Keystore** (`MasterKey` from AndroidX Security).

```kotlin
// Pseudocode
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val dbKey = encryptedPrefs.getString("db_key", null) ?: generateAndStoreNewKey()

Room.databaseBuilder(context, JunnzDatabase::class.java, "junnz.db")
    .openHelperFactory(SupportFactory(SQLiteDatabase.getBytes(dbKey.toCharArray())))
    .build()
```

### Preferences

Sensitive preferences (e.g. user's chosen wake word in v3) are stored in `EncryptedSharedPreferences`. Non-sensitive preferences (UI theme, etc.) are in plain DataStore.

### Audio buffers

Audio is captured into in-memory byte buffers and never hits disk. If a session is interrupted (e.g. phone goes to sleep mid-transcription), the buffer is discarded; the user has to re-speak. We do not implement "resume" because doing so would require persisting raw audio.

---

## Encryption in transit

- **Phone ↔ Watch**: Wearable Data Layer is encrypted at the Bluetooth pairing layer. We do not add a second encryption layer in v0; the Bluetooth-layer encryption is sufficient for the threat model (someone within Bluetooth range with sniffing capability).
- **Phone ↔ telemetry endpoint** (opt-in only): TLS 1.3, certificate pinned via `network_security_config`.

---

## Permissions strategy

Each permission is requested **just before first use**, with an explanation screen. We never bulk-request on first launch.

| Permission | Why we ask | What we do | What we don't do |
|---|---|---|---|
| `RECORD_AUDIO` | To hear what you say to Junnz. | Capture audio, transcribe locally, discard. | Continuous listening. Recording when you don't tap-to-talk. Sending audio to any server. |
| `POST_NOTIFICATIONS` | To show you reminders. | Show notifications when reminders fire. | Send marketing notifications. |
| `ACCESS_FINE_LOCATION` | For location-triggered reminders. | Register geofences with Android. | Track your location continuously. Send your location anywhere. |
| `ACCESS_BACKGROUND_LOCATION` | For location reminders to fire when the app is closed. | Same as above, but extended to background. | Anything else with the location. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | To detect when you open Blinkit, Zomato, etc., so we can flash relevant reminders. | Read app+title metadata of notifications from a fixed allowlist of apps. | Read content of notifications from any other app. Persist notification content. Send any of it anywhere. |
| `PACKAGE_USAGE_STATS` | To know which app is currently in the foreground (better context detection). | Sample at 30s intervals while the matching service is active. | Build a profile of your app usage. Persist it beyond 7 days. |
| `SCHEDULE_EXACT_ALARM` | For reminders to fire at the exact time you set. | Set exact alarms for time-triggered reminders. | Anything else. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | For the matching service to run in the background. | Run a foreground service while there are active semantic-triggered reminders. | Run when not needed. |
| `WAKE_LOCK` | Brief wake during transcription. | Hold a partial wake lock during ASR (~1s typical). | Hold wake locks for longer than ASR runtime. |
| `RECEIVE_BOOT_COMPLETED` | Re-register geofences and time alarms after reboot. | What it says. | Run anything else on boot. |

The permissions explainer screens are part of the onboarding flow. Each is a single screen with three lines: what we ask, why we ask, what we promise *not* to do.

### Notification listener — extra care

`BIND_NOTIFICATION_LISTENER_SERVICE` is the most sensitive permission Junnz requests. The Play Store's "Restricted Permissions" policy applies. We must:

1. Show a clear explainer before sending the user to the system NL settings.
2. Use the listener **only** for the documented core feature (context detection for reminder firing).
3. Maintain an allowlist of packages we'll process, hardcoded in code — not user-configurable in v0 to avoid policy surface area. The list is published in the privacy policy.
4. Document the data we read in the privacy policy.

The implementation:

```kotlin
class JunnzNotificationListener : NotificationListenerService() {
    private val allowlist = setOf(
        "com.bigbasket.mobileapp", "in.swiggy.android", "com.zomato.cart",
        "com.grofers.customerapp" /* Blinkit */, "in.amazon.mShop.android.shopping",
        "com.olacabs.customer", "com.ubercab", "com.flipkart.android",
        "com.whatsapp",  // for "Mom called" type contexts; content is NEVER persisted
        // ... see MATCHING_ENGINE.md for full taxonomy
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in allowlist) return
        // Build a context phrase from package metadata, NOT from notification content
        val phrase = ContextPhraseDeriver.fromPackage(sbn.packageName)
        // e.g. "shopping for groceries" for blinkit, never "your order #1234 is on the way"
        contextEventEmitter.emit(ContextEvent.AppNotification(sbn.packageName, phrase))
    }
}
```

Note: we deliberately **do not** read the notification's title or text. We derive a context phrase from the package alone. This is a stronger privacy posture than reading the notification body, even though reading the body would give us more matching signal. We're choosing the privacy story.

If, in v2+, we want to read notification body for stronger matching, we'll do it as an opt-in feature gated behind a separate explainer screen, with a clear "what this enables / what it costs" disclosure.

---

## DPDP Act 2023 (India) compliance

Junaid and the initial user base are in India. The Digital Personal Data Protection Act 2023 applies. Key requirements and how we meet them:

| Requirement | Implementation |
|---|---|
| Notice (§5) — clear, plain-language notice of data processing | In-app onboarding + privacy policy linked from Settings |
| Consent (§6) — free, specific, informed, unconditional, unambiguous | Per-permission explainer screens; granular opt-ins for telemetry, crash reporting, future cloud features |
| Purpose limitation (§4) — use data only for the specified purpose | We use data only for reminder matching. No marketing. No profiling for other purposes. |
| Data minimization (§4(b)) — collect only what's necessary | We do not collect user account info. There is no account in v0. Local-only. |
| Right to erasure (§13(1)(c)) | Settings → Delete all data. One tap, fully wipes Room + EncryptedPrefs + Keystore key. |
| Right to access (§13(1)(a)) | Settings → Export my data. Generates a JSON file with all reminders, fire history, and ContextEvents. |
| Children's data (§9) — no behavioral monitoring or targeted advertising for those under 18 | Self-declared age in onboarding (kept local). If under 18, telemetry permanently disabled. No advertising in product regardless. |
| Security safeguards (§8(5)) | Encryption at rest (SQLCipher + Keystore), TLS 1.3 in transit, foreground-service-only execution model, threat model documented (this doc) |
| Breach notification (§8(6)) | If a v3+ cloud sync feature is added and breached, we notify users in-app and via the Data Protection Board within 72 hours. v0–v2 has no cloud component, so this is not yet applicable. |

We will publish a Data Protection Officer contact email on the privacy policy page (`privacy@junnz.app`).

## GDPR posture (EU users)

We design for GDPR even before EU users exist, because retrofitting is expensive. The DPDP measures above also satisfy most of GDPR. Specific GDPR additions:

- **Lawful basis**: consent (Art 6(1)(a)) for all processing. We do not rely on legitimate interest in v0–v2.
- **Right to data portability** (Art 20): the JSON export is in a structured, machine-readable format.
- **Right to object** (Art 21): any user can disable any feature in Settings; uninstall fully terminates all processing.
- **Privacy by design** (Art 25): documented in this file.
- **No DPO required at v0 scale** — we will appoint one if we cross 250 employees or process special-category data, neither of which applies.

## Children

Junnz is rated 13+. Voice + reminder + location features are not designed for or marketed to children. Onboarding asks for self-declared age. If under 13, we refuse account creation (in v0 there are no accounts; we refuse to proceed past onboarding). If 13–17, we automatically disable telemetry and crash reporting.

---

## Threat model

| Threat | Risk | Mitigation |
|---|---|---|
| Lost/stolen device, attacker has physical access | High | Database is SQLCipher-encrypted with key in Keystore. Keystore unlock requires device unlock. App requires device-credential auth on first launch after boot. |
| Malicious app on the same device reads our data | Medium | Internal storage scoped to app. SQLCipher even if internal storage is compromised. We don't write to external storage. |
| Network attacker (MitM on telemetry) | Low | TLS 1.3, certificate pinning. Telemetry is anonymized and opt-in. Compromise impact is limited to anonymous app metrics. |
| Bluetooth attacker between watch and phone | Low | Wearable Data Layer uses Android Bluetooth Bonding (encrypted at link layer). Attacker would need physical proximity and the device pairing key. |
| Malicious notification listener competitor reads our notifications | Low | Our notifications contain only the reminder text the user spoke. We don't expose internal state in notifications. |
| Memory dump / root access | High | Out of scope for v0–v2. A rooted device is assumed compromised. |
| Side channel (timing, power) | Low | Out of scope. |
| Cloud breach | N/A | No cloud in v0–v2. |
| Compromised dependency in our build | Medium | Pin all dependencies in version catalog. Dependabot enabled. Reproducible builds via Gradle's locking. |
| Subpoena / legal compulsion | N/A | We hold no user data on servers. There is nothing to subpoena. This is an architectural defense, not a policy one. |

---

## Audit logging

We maintain an internal audit log (in Room, encrypted, 30-day retention) of:

- Reminder creates / edits / deletes
- Fire events (which reminder, what triggered it, was it acted on)
- Permission grants / revokes
- Critical errors

This log is **for the user**, not for us. It powers the "weekly report" feature in v3 and gives the user a way to ask "wait, did I really say that?" The log is included in the data export.

We do not have a server-side audit log because we have no server.

---

## Specific engineering rules (for Claude Code)

These are non-negotiable. Treat as compile-time invariants where possible:

1. **No `String` containing audio data.** Audio is `ByteArray` or `ShortArray`. Lint rule: forbid `Base64.encode` of audio buffers.
2. **Transcripts are not logged.** `Log.d("transcript: $text")` is forbidden. Lint rule: detect and fail.
3. **Notification content is not logged.** `Log.d("notification: ${sbn.notification.extras}")` is forbidden.
4. **No third-party SDK that sends data off-device** without explicit review and addition to the privacy policy. The build will fail if any new dependency is added that's not in `libs.versions.toml`'s reviewed list.
5. **Cleartext traffic is forbidden in release.** Network security config enforces this.
6. **All Room entities containing user content extend a marker interface `EncryptedEntity`.** Lint rule: any `@Entity` not marked `EncryptedEntity` triggers a review.
7. **Test that the database is unreadable without the key.** Instrumented test that opens the SQLite file directly without SQLCipher and asserts it fails.
8. **Test that a fresh install creates a new database key.** Instrumented test asserting key randomness.
9. **The `Delete all data` button is always reachable** within 2 taps from the main screen. UI test.
10. **Privacy policy is bundled with the APK.** Not just a URL. So users can read it offline at any time.

---

## What goes in the in-app privacy policy

A short version. Linked to a long version. Must cover, in order:

1. What Junnz does
2. What data it collects (reminder text, location for geofences, app foreground events, notification metadata from allowlisted apps)
3. Where it stores it (locally on your phone, encrypted)
4. What leaves the device (nothing by default; opt-in telemetry is anonymized)
5. Your rights (access, deletion, opt-out — and how to exercise them)
6. The data retention table (above)
7. Contact: `privacy@junnz.app`
8. Effective date and changelog

A first-pass draft of the privacy policy lives at `app-phone/src/main/assets/privacy_policy.md`.

---

## Future considerations (v3+)

These are not in v0–v2 but should be designed for now to avoid retrofit pain:

- **Multi-device sync**: must be end-to-end encrypted. User holds the key. Server sees only ciphertext. Likely Signal-protocol-style key exchange between devices.
- **Family/shared reminder pools**: per-pool encryption keys, shared via QR code in person.
- **Cloud LLM fallback** (e.g. for very long captures the on-device ASR can't handle): explicitly opt-in per-capture, with a clear UI signal that "this one is going to the cloud." Never automatic.
- **Mascot / gamification telemetry**: aggregate-only, on-device computation, periodic rollup published to telemetry endpoint with k-anonymity threshold.

---

## Compliance checklist for first release

- [ ] Privacy policy drafted, reviewed, bundled
- [ ] All permission explainer screens implemented
- [ ] SQLCipher wired with Keystore-backed key
- [ ] Network security config restricts release domains
- [ ] Notification listener allowlist hardcoded and documented
- [ ] Data export feature implemented and tested
- [ ] Data deletion feature implemented and tested
- [ ] Telemetry opt-in (off by default) implemented
- [ ] Crash reporting opt-in (off by default) implemented
- [ ] Play Store data safety form completed and matches the policy
- [ ] DPO contact email functional
