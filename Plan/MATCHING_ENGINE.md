# Matching Engine

This is the differentiator. Everything else in Junnz is table stakes; this is what makes it a different product.

The matching engine answers one question: **given the user's current context, which pending reminders should fire right now?**

Context can come from time, location, app activity, or voiced announcements ("I'm shopping on Blinkit now"). The engine normalizes all four into a unified scoring pipeline and produces ranked match candidates above a confidence threshold.

This document is opinionated and detailed because Claude Code will need to implement it carefully. Tone-down brevity is in other docs; this one earns its length.

## Design goals

1. **Low false negative rate on time/location** — these are the user's deliberate, explicit triggers. Missing one is unacceptable.
2. **Acceptable false positive rate on semantic** — semantic matching is fuzzy by nature; a slightly over-eager match is far less bad than no match. Default to firing more, let the user dismiss.
3. **Explainable** — every fire carries a `MatchReason` that the user can see. No black box.
4. **Cheap to evaluate** — context events are frequent. The engine must short-circuit aggressively before doing any vector math.
5. **Stable across model upgrades** — when we swap embedding models, behavior must remain coherent. Anchor texts are persisted; vectors are recomputable.

## Inputs

The engine consumes `MatchRequest`:

```kotlin
data class MatchRequest(
    val context: ContextSnapshot,
    val candidatePool: List<Reminder>,    // pre-filtered to status=PENDING or SNOOZED-and-ripe
)
```

Where:

```kotlin
data class ContextSnapshot(
    val now: Instant,
    val location: GeoPoint?,
    val foregroundApp: AppSignal?,        // package + category
    val recentApps: List<AppSignal>,      // last hour, deduped
    val voicedText: String?,              // most recent context announcement, if any
    val sourceTrigger: ContextTriggerSource,
)

enum class ContextTriggerSource {
    SCHEDULED_TIME,        // time alarm fired
    GEOFENCE_EVENT,        // geofence transition
    APP_OPENED,            // notification listener or foreground inference
    VOICED_ANNOUNCEMENT,   // user said "I'm at the store"
    MANUAL_REFRESH,        // user pulled to refresh
}
```

`sourceTrigger` is a hint to the engine about which trigger types are *most likely* to fire, so it can short-circuit. It does NOT restrict — a `GEOFENCE_EVENT` source can still cause semantic matches if voiced text is also present.

## Output

```kotlin
data class MatchResult(
    val candidates: List<MatchCandidate>,    // ordered by score desc
    val evaluatedAt: Instant,
    val candidatesEvaluated: Int,            // for telemetry
    val candidatesShortCircuited: Int,
)
```

Caller (typically the trigger feature module) decides what to do with the candidates. Default: fire all candidates above the per-trigger-type threshold, deduplicating against recent fires for the same reminder.

## The pipeline

```
ContextSnapshot
      │
      ▼
┌─────────────────┐
│ 1. Pre-filter   │   cheap; status, recurrence, snooze state, recent-fire dedup
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ 2. Per-trigger evaluation (parallel)    │
│   ├── TimeMatcher                        │
│   ├── GeofenceMatcher                    │
│   ├── AppContextMatcher                  │
│   └── SemanticMatcher                    │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────┐
│ 3. Score fusion │   combine scores from multiple matching triggers on same reminder
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 4. Threshold    │   apply per-trigger thresholds; drop candidates below
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 5. Dedup        │   suppress recent-fire repeats unless explicitly re-armable
└────────┬────────┘
         │
         ▼
   MatchResult
```

### Stage 1: pre-filter

Cheap filters that eliminate the majority of pending reminders before any vector math. This is the most important performance optimization.

For each candidate reminder:

- Skip if `status` ∉ {`PENDING`, `SNOOZED` *and* snoozeUntil ≤ now}.
- Skip if `firedAt` contains an entry within the per-trigger cool-down (default: 30 minutes per trigger; configurable).
- Skip if reminder has only `Time` triggers AND `sourceTrigger` ≠ `SCHEDULED_TIME` AND `sourceTrigger` ≠ `MANUAL_REFRESH`.
- Skip if reminder has only `Geofence` triggers AND `sourceTrigger` ∉ {`GEOFENCE_EVENT`, `MANUAL_REFRESH`}.
- (Note: reminders with `AppContext` or `Semantic` triggers are always kept past pre-filter, because those can fire on a wide variety of source events.)

Tag pre-filtering for semantic triggers:
- If `voicedText` or `foregroundApp.category` is present, derive a *coarse tag set* from it (e.g., `foregroundApp.category=GROCERY` → tag set `{grocery, food, household}`).
- Reminders whose tag set has zero overlap with the coarse tag set get a flag — they're not skipped (semantic match can still find them) but they're sorted to the back of the candidate list and given a lower base score.

Pre-filter is implemented as a single SQL query over Room, returning a small set of candidate IDs. Rough target: 1000 reminders → 5–20 candidates after pre-filter for a typical context event.

### Stage 2: per-trigger evaluation

Each trigger type has its own matcher. They run in parallel coroutines on `Dispatchers.Default`.

#### TimeMatcher

For `Trigger.Time`:

- If `recurrence == null`: fires when `|now - fireAt| < ε` where `ε = 60 seconds` to absorb scheduling jitter. Score = `1.0` if exact match, decaying linearly to `0.7` at the edges of the window.
- If recurrence is set: compute the next occurrence ≤ `now + ε`; check if `now` is in that window.
- One-shot triggers fire exactly once; recurring triggers fire on every matched occurrence.
- Time zones are honored via `timeZoneId`. A "weekday 9 AM" reminder created in IST fires at 9 AM IST regardless of where the user travels — unless the user explicitly changed the trigger's tz to "device local".

Score: 1.0 (exact), 0.85 (within 30s of intended), 0.7 (within 60s).

#### GeofenceMatcher

For `Trigger.Geofence`:

The matcher does not call the location API directly — that's the trigger source's job. The matcher receives the `ContextSnapshot` already populated with the geofence transition that fired.

- Match if `sourceTrigger == GEOFENCE_EVENT` and the fired geofence id matches this trigger's id.
- For `transitionType == DWELL`: also require `dwellSeconds` has elapsed (the geofencing client enforces this; we double-check defensively).

Score: 1.0 if direct geofence-id match.

For `MANUAL_REFRESH`: compute haversine distance from `context.location` to trigger center. Match if distance < radius. Score = `1.0 - (distance / radius) * 0.3` (so close to center scores higher).

#### AppContextMatcher

For `Trigger.AppContext`:

- Match if `foregroundApp.packageName ∈ trigger.packageNames`. Score: 1.0.
- Match if `foregroundApp.category ∈ trigger.categoryHints`. Score: 0.8.
- Match if `voicedText` mentions one of the apps in `packageNames` (substring match on display name, case-insensitive). Score: 0.7.
- Match if any of `recentApps` (within last 5 min) ∈ `packageNames`. Score: 0.6 — this catches "I just opened Blinkit, then alt-tabbed to my notes" patterns.

The `categoryHints` mechanism is the safety net. Even if the user's grocery app is `app.zepto` and the reminder was created with `app.blinkit`, the category match still fires.

#### SemanticMatcher

The interesting one. For `Trigger.Semantic`:

1. Build a **query embedding** from available context. The query is constructed dynamically from the snapshot:
   - If `voicedText` is present and informative (>3 tokens), it dominates the query.
   - If `foregroundApp.packageName` is set, append a short phrase like "using Blinkit grocery app".
   - If `recentApps` has entries with categories, append "recent activity in [grocery, food]".
   - If `location.placeName` is known (geocoded), append "at [Bangalore, MG Road area]".
2. Run `EmbeddingEngine.embed(queryText)` → 384-d (MiniLM) or 768-d (Gemini) vector.
3. For each candidate reminder with at least one `Semantic` trigger, compute cosine similarity between the **query embedding** and the trigger's `anchorEmbedding`.
4. If `similarity ≥ trigger.similarityThreshold`, emit a `MatchCandidate` with `score = similarity` and `MatchReason.SemanticMatch(anchorText, querySnippet)`.

Performance note: we do NOT use the vector index here. The vector index is for *reverse* lookups (when a new reminder is created, find similar existing reminders for dedup suggestions). For matching, we have ≤ 20 candidates after pre-filter; iterating them is faster than a vec0 query setup for this small N.

For pools above 50 candidates (rare), we route to vec0 with a kNN query bounded by the trigger threshold.

### Stage 3: score fusion

A reminder may match on multiple triggers simultaneously (e.g., geofence + semantic). When this happens:

- The fused score is `1 - (1-s1)(1-s2)...(1-sn)` (probabilistic OR).
- The `MatchReason` is the highest-scoring trigger's reason, with a flag noting other triggers also matched (for the UI to surface).
- The chosen `Trigger` for the candidate is the highest-scoring one.

### Stage 4: threshold

Per-trigger-type thresholds (tunable in settings, with sensible defaults):

| Trigger type    | Default threshold | Rationale                                         |
|-----------------|-------------------|---------------------------------------------------|
| Time            | 0.7               | Always fire on near-exact time match              |
| Geofence        | 0.7               | Always fire on transition; manual refresh is fuzzy|
| AppContext      | 0.6               | Loose; AppContext triggers are themselves coarse  |
| Semantic        | 0.72              | Empirically: above this MiniLM matches feel right |

Thresholds are starting points. We log every `MatchCandidate` (with PII scrubbing) along with whether it was fired and whether the user acted on it. Over time, this becomes a personal calibration dataset (see "Adaptive thresholds" below).

### Stage 5: dedup

For each candidate that survives threshold:

- Look up the most recent `reminder_fire_history` row for `(reminder_id, trigger_id)`.
- If `now - last_fire < cooldown`, drop the candidate UNLESS the trigger is explicitly `re_armable`.
- Default cooldown by trigger type:
  - Time: 0 (each scheduled occurrence is its own fire)
  - Geofence (ENTER): 1 hour
  - Geofence (DWELL): 6 hours
  - AppContext: 30 minutes
  - Semantic: 30 minutes

## Embedding pipeline

### Models

Two models supported, abstracted behind `EmbeddingEngine`:

1. **Gemini Nano embeddings via AICore** (preferred where available)
   - Dimensionality: 768
   - Latency: ~30ms typical on Pixel 8+
   - Available on Pixel 8+, Galaxy S24+, and select OEMs supporting AICore
2. **`all-MiniLM-L6-v2` quantized ONNX** (fallback, universal)
   - Dimensionality: 384
   - Latency: ~50ms typical on mid-range Snapdragon
   - Bundled with the app (~25 MB)

Selection at runtime:
- Detect AICore availability via `AICore.isAvailable()`.
- If yes and user setting allows, use Gemini Nano.
- Else use ONNX MiniLM.

### Tokenization, normalization, and prep

For both models:
- Lowercase input
- Strip extraneous whitespace
- Remove emoji (they confuse small embedders)
- Replace `@user` and URLs with `[user]` and `[url]` placeholders (privacy + signal denoising)
- Truncate at 256 tokens (more than enough for a reminder phrase)

Output vectors are L2-normalized before storage. This makes cosine similarity equivalent to a dot product, which is faster.

### When embeddings are computed

| Event                                | Embedding computed?               |
|--------------------------------------|-----------------------------------|
| Reminder created                     | Yes, async, via `EmbedReminderWorker` |
| Reminder edited (title or anchor)    | Yes, async                         |
| Trigger added (semantic only)        | Yes, async                         |
| Context match request                | Yes, inline (user is waiting)      |
| Model upgrade                        | Bulk re-embed via `BulkReembedWorker` (charge required) |

The first match request after a reminder is created may race the embed worker. If the embedding isn't ready, we skip semantic matching for that reminder this round. The user can `MANUAL_REFRESH` after a few seconds to retry.

## Anchor text construction at capture time

When a reminder is captured, we generate `Trigger.Semantic.anchorText` automatically (user can edit later). The construction:

1. Take the parsed reminder `title` and `body` (if any).
2. Extract intent verbs and object nouns via the NLP layer (lightweight grammar; see "NLP layer" below).
3. Form a phrase like:
   - "buy fresh cream" → anchor "buying groceries; getting fresh cream"
   - "pick up dry cleaning" → anchor "picking up clothes from cleaner; running errands"
   - "remind me about Sarah's birthday" → anchor "celebrating Sarah's birthday; gift for Sarah"

The anchor is intentionally broader than the title. Semantic matching needs to match on intent, not exact wording, so the anchor is paraphrased and synonym-expanded.

We can build this paraphrase via:
- **Option A** — A small instruction-tuned model run on-device (Gemma 2B quantized, slow but accurate).
- **Option B** — A rule-based expansion using a curated synonym dictionary. Faster, less accurate.
- **Option C** — Cloud LLM call (OpenAI / Claude). Best quality, but violates the on-device default.

Recommendation for v0: **Option B**, hand-curated dictionary covering the top 200 verb-noun patterns common in reminders (groceries, food, errands, reminders-to-people, work tasks). Promote to Option A in v1 when Gemma 2B inference is benchmarked acceptable.

## NLP layer (parsing voice → reminder)

The NLP layer takes raw transcript and produces:
- `title` (short, imperative)
- `body` (optional)
- `triggers` (parsed time/location/context cues)
- `tags` (derived)
- `anchorText` (per above)

Architecture:
1. Heuristic parser (Kotlin, regex + curated patterns) for common patterns:
   - "remind me to X at HH:MM" → Time trigger
   - "remind me to X when I'm at PLACE" → Geofence trigger if PLACE is in the user's place book
   - "remind me to X when I'm shopping/buying/at the store" → Semantic trigger with appropriate anchor
   - "remind me about X" with no temporal/spatial cue → Semantic-only trigger
2. Fallback: if the heuristic parser can't classify, the reminder is created with status `PENDING` and a single `Trigger.Semantic` whose anchor is the transcript itself. The user is prompted to add a time/location if they want one. This is the safety net — a captured reminder is never lost to parser failure.

The heuristic parser is intentionally simple and extensible. It is unit-tested with a corpus of ~200 phrases collected during dogfood. Every parser miss adds a new test case.

## Adaptive thresholds (post-v0)

The static thresholds above are starting points. Over time, we want personalization:

- For each user, log: `(reminder, candidate_score, fired?, acted_on?)` tuples.
- Periodically (weekly, on charge), recompute personal thresholds via simple logistic regression on `acted_on?` as label, `score` and `trigger_kind` as features.
- Personal threshold = score above which `P(acted_on)` exceeds 0.5.
- Bound personal thresholds within `[default - 0.1, default + 0.15]` to prevent runaway calibration on small samples.

Adaptive thresholds are explicitly **opt-in** in settings, default off in v0. Static thresholds first; tune by adoption metrics; consider personalization in v2.

## Telemetry and explainability

Every match decision is logged to `reminder_fire_history` with:
- `reminder_id`, `trigger_id`, `score`, `reason`
- Whether it fired (post-threshold + dedup)
- Whether the user acted on it within 24 hours (updated post-hoc)

A debug screen (long-press app icon → debug menu, dev builds only) shows:
- Last 50 match evaluations with full breakdown
- Per-reminder embedding vectors (truncated)
- Threshold sliders for live tuning

User-facing:
- Each fired reminder's notification includes the `MatchReason` text: "Firing because: you're using Blinkit and you said 'buy cream' last week."
- Users can disagree with a match: dismiss with "wasn't relevant" → that fire is logged as a negative example.

## Failure modes and graceful degradation

| Failure                              | Behavior                                                |
|--------------------------------------|---------------------------------------------------------|
| Embedding engine unavailable          | Semantic matching disabled; time/geofence/app still work |
| ASR fails on capture                  | Save raw audio; user can retry transcription later       |
| Vector store corrupted                | Re-embed all reminders; meanwhile fall back to substring matching on anchor text |
| AICore present but throws             | Fall back to ONNX MiniLM, log error, retry next session |
| Model upgrade in progress             | Match using whatever embeddings are present; reminders with stale embeddings get a lower base score until re-embedded |

## Performance targets

- End-to-end match evaluation (from `ContextSnapshot` to `MatchResult`): **< 200ms p95** for ≤ 1000 pending reminders on Snapdragon 7+ Gen 2 or equivalent.
- Embedding computation per reminder: **< 100ms p95**.
- Vector kNN query (when used): **< 50ms p95** for 1000 vectors.

Benchmarks live in `:shared-matching:benchmark` and run in CI on every PR.

## Test corpus

A reference corpus lives at `shared-matching/src/commonTest/resources/corpus/`:
- `reminders.json` — 200 sample reminders with ground-truth tags
- `contexts.json` — 100 sample contexts
- `expected_matches.json` — for each (context, reminder) pair, the expected match decision

CI asserts that the engine matches the expected decisions on this corpus. When a parser change or threshold tweak changes outcomes, the corpus is updated deliberately — never silently.

## Open questions

- **Hybrid retrieval (BM25 + embeddings)**: small reminder pools may not need it, but it's robust against embedding failures. Worth piloting in v2 if we see false negatives on rare-vocabulary reminders.
- **Cross-encoder reranking**: a small reranker (e.g., MiniLM cross-encoder) on the top-5 candidates could improve precision. Latency budget would allow ~30ms more. Consider for v2.
- **Negative examples in anchor**: today the anchor is positive only. Adding "NOT when I'm at home" as a negation is desirable; needs grammar work.
- **Time-decayed scoring**: a 6-month-old reminder probably matters less than a 6-hour-old one. We could decay scores by age. Risk: users explicitly create long-term reminders. Default to no decay; revisit with data.
