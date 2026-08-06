# Provender — Claude Code project guide

Provender is a Kotlin / Jetpack Compose Android app that turns photos of your pantry into a
living food inventory and feeds you matching + generated recipes. **All AI inference is
on-device** (LiteRT-LM + Gemma 3n, ML Kit). No AI API keys, no cloud AI, ever (a cloud
fallback may arrive later behind the `LlmEngine` interface, off by default). Read `SPEC.md`
for the full product & technical spec before making changes.

## Phase plan (from SPEC.md §6)

- [x] **Phase 0 — Scaffold**: Gradle project, Hilt, Room, Compose navigation, theme, Settings shell, CI-friendly build
- [x] **Phase 1 — Inventory core**: entities/DAOs, manual CRUD, locations, search (FTS), staples, change log
- [x] **Phase 2 — Model runtime**: LiteRT-LM integration, model download manager + progress UI, device capability gating, hidden debug prompt screen (tap the Settings model row 7×)
- [x] **Phase 3 — Vision capture**: CameraX flow, ML Kit OCR, extraction prompt + parsing, Confirm screen (seed mode), barcode mode + Open Food Facts cache
- [x] **Phase 4 — Diffing**: fuzzy matching, three-part diff UI, change-log integration
- [ ] **Phase 5 — Paprika + matching**: `.paprikarecipes` import, ingredient parser, matching engine, Matches tab
- [ ] **Phase 6 — Web recipes**: TheMealDB provider behind `RecipeSource`
- [ ] **Phase 7 — Roulette**: deck, tuning, shortlist
- [ ] **Phase 8 — Generation**: flexible-recipe prompts, background pre-generation cache, validation, save
- [ ] **Phase 9 — Polish**: stale review flow, snapshot photo pruning, DB export/backup

Each phase must end with a running, installable app and passing tests. Update this checklist
when a phase completes.

## Build / test / install

```bash
./gradlew assembleDebug          # build debug APK
./gradlew test                   # unit tests (JVM + Robolectric; includes DAO tests)
./gradlew installDebug           # install on a connected device/emulator
```

CI (`.github/workflows/build.yml`) runs `test` + `assembleDebug` (uploading the debug APK) and
`lintDebug` on every push to `main`, every PR, and on manual dispatch.

Requires JDK 17+ (JDK 21 works) and the Android SDK (compileSdk 36). Gradle wrapper is 8.14.3,
AGP 8.13.0, Kotlin 2.2.20 — see `gradle/libs.versions.toml` for everything; all versions live
in the catalog, never inline in build files.

## Package layout (single `app` module, package root `com.provender`)

```
app/src/main/java/com/provender/
  data/        # Room entities, DAOs, database, repositories; settings/ (SharedPreferences)
  network/     # ModelDownloadWorker, Open Food Facts client; TheMealDB client (Phase 6)
  ai/          # LlmEngine + FakeLlmEngine + LitertLmEngine, prompts, lenient JSON parsing,
               #   ModelVariant/ModelRepository/ModelState, capability gating
  mlkit/       # BarcodeAnalyzer, OcrClient, PhotoDownscaler
  paprika/     # .paprikarecipes import (Phase 5)
  matching/    # normalization, synonym map, scoring (normalizer exists; rest Phase 5)
  roulette/    # deck building, filters (Phase 7)
  generate/    # flexible-recipe prompt building + validation + cache (Phase 8)
  ui/          # Compose screens: theme/, navigation/, components/, inventory/, capture/,
               #   confirm/, recipes/, roulette/, settings/, debug/ (hidden LLM console)
  di/          # Hilt modules
```

Packages listed for future phases that have no code yet are intentionally absent from the
tree — create them when their phase starts.

## Code conventions

- **Architecture**: MVVM + repositories, unidirectional data flow. ViewModels expose a single
  `StateFlow<UiState>` (`combine(...)` + `stateIn`) and public event functions. No logic in
  composables beyond rendering and event forwarding.
- **DI**: Hilt everywhere. Repositories are interfaces bound to `Default*` implementations in
  `di/` so ViewModel tests use in-memory fakes.
- **All model access goes through `ai/LlmEngine`**. Feature code and tests must never depend
  on the real model; `FakeLlmEngine` is the Hilt binding until Phase 2.
- **Database**: Room with KSP; schemas exported to `app/schemas/` (committed). FTS4 table
  (`inventory_items_fts`) mirrors `inventory_items` for search. Every inventory mutation —
  manual or snapshot-driven — writes an `InventoryChange` row in the same transaction.
- **Async**: Coroutines + Flow only; WorkManager for background jobs (later phases). Room DAOs
  return `Flow` for reads, `suspend` for writes.
- **Serialization**: kotlinx.serialization. Networking is Retrofit + OkHttp and is used only
  for non-AI needs (recipe data, barcode lookups, one-time model download).
- **Style**: official Kotlin style; conventional commits (`feat:`, `fix:`, `test:`, `chore:`,
  `docs:`); unit tests must run via `./gradlew test` (Robolectric, not instrumented, wherever
  possible).
- **Never** add an API-key UI or cloud-AI dependency. Model weights (`*.litertlm`) and
  app-private photo dirs are gitignored — keep it that way.

## Testing

- DAO/repository tests: Robolectric (`@Config(sdk = [34])`) with in-memory Room, `runTest`.
- ViewModel tests: JVM-only with Turbine + `FakeInventoryRepository` + `MainDispatcherRule`.
- Pure logic (normalizer, parsers): plain JUnit.

## Decisions

- **AGP 8.13.0, not 9.x** (latest stable is 9.2.0): AGP 9 mandates the built-in-Kotlin
  migration and Gradle ≥ 9.4.1. This scaffold was authored in a sandbox whose egress policy
  blocks all Maven repos (dl.google.com, maven.google.com, repo.maven.apache.org), so no build
  could be verified; the well-understood 8.x + Kotlin 2.2.20 + KSP combination was chosen to
  maximize first-build success. Upgrading to AGP 9 later = bump `agp` + wrapper to Gradle 9.4+,
  drop `kotlin-android` plugin per the migration guide.
- **LiteRT-LM coordinate** recorded in the catalog as
  `com.google.ai.edge.litertlm:litertlm-android:0.13.1` (Google Maven; verified July 2026).
  The deprecated MediaPipe LLM Inference API (`com.google.mediapipe:tasks-genai`) must not be
  added. ML Kit: `barcode-scanning:17.3.0`, `text-recognition:16.0.1`. None are dependencies
  yet — Phase 2–3.
- **`InventoryChange.itemName` denormalized** (extra column beyond SPEC §4) so the change log
  stays readable after an item row is deleted; no FK on `itemId` because the log is
  append-only history and must survive item deletion.
- **Default locations seeded from `ProvenderApp.onCreate`** via
  `InventoryRepository.seedDefaultLocationsIfEmpty()` (idempotent, checks count == 0) rather
  than a Room `Callback` — testable and works with in-memory DBs.
- **Quantity model**: `quantity: Double?` + free-form `unit: String?` with a suggested-units
  list (`count, g, kg, oz, lb, ml, l, some, low, plenty`); the "some/low/plenty" fuzzy amounts
  are units with null quantity.
- **Staple toggle writes a `MANUAL_EDIT` change row with delta 0** — "every manual mutation
  writes an InventoryChange row" is interpreted to include non-quantity edits.
- **String routes** for Compose navigation (not type-safe routes) — trivial to migrate later,
  fewer moving parts now.
- **material-icons-extended pinned at 1.7.8** — the artifact left the Compose BOM; 1.7.8 is
  its final version and is forward-compatible.
- **`app/schemas/` is committed but empty until the first local build** — Room writes
  `1.json` there during KSP; commit it when it appears so future migrations diff cleanly.
- **LiteRT-LM API usage verified against upstream source** (July 2026,
  `google-ai-edge/LiteRT-LM@main`, `kotlin/java/com/google/ai/edge/litertlm/`): blocking
  `Engine(EngineConfig).initialize()`, `createConversation(ConversationConfig)`,
  `sendMessage(text|Contents): Message`, `Contents.of(Content.ImageFile|Text)`,
  `SamplerConfig(topK, topP, temperature)`, `Backend.CPU()/GPU()`. All calls run on
  Dispatchers.IO behind one Mutex; `Message.toString()` yields the reply text.
- **Gemma weights come from license-gated Hugging Face repos** — unauthenticated download
  returns 401/403 until the user accepts Google's license. No token UI (the no-API-key rule
  is interpreted strictly); instead the worker's error explains the situation and Settings
  offers a document-picker **Import** of a browser-downloaded `.litertlm` file. `adb push`
  into `filesDir/models/` also works.
- **Model downloads run as plain (non-foreground) WorkManager jobs** with UNMETERED +
  storage-not-low constraints and `.part` Range-resume. A dataSync foreground service with
  notification is deliberate future polish, not Phase 2.
- **CPU/GPU is a manual Settings toggle for now** (CPU default). The SPEC's per-device
  benchmark-and-persist idea is deferred; the `LlmBackend` pref is where it would land.
- **`LlmEngine` grew `warmUp()` and debug-only `rawPrompt()`** beyond SPEC §4's two methods:
  warmUp implements the eager-init requirement, rawPrompt feeds the hidden debug console
  (reached by tapping Settings → "On-device model" seven times). Feature code must never
  call rawPrompt.
- **Capability gate**: arm64-v8a required; total RAM ≥ ~5.5 GiB for E2B, ≥ 7 GiB for E4B.
  Below that the model UI is disabled with an explanation (barcode/manual entry unaffected).
- **Snapshot analysis is an app-scoped coroutine, not a WorkManager job with notification**
  (SPEC §2 asks for a notification): the job survives navigation and the Confirm screen says
  it keeps running in the background, but a real OS notification needs POST_NOTIFICATIONS
  plumbing — deferred to Phase 9 polish. Status/result/error persist on the `snapshots` row,
  so process death degrades to a FAILED/stuck-ANALYZING row, never data loss.
- **DB schema still version 1** — Snapshot/BarcodeCache were added pre-release before any
  build exported `app/schemas/1.json`. First shipped schema locks the version; migrations
  start after that.
- **Barcode scans add straight to inventory** (name + brand-in-notes + barcode column,
  MANUAL_ADD change row) rather than joining the photo session's confirm list — precise
  entries don't need the VLM review path. Capture FAB is the SPEC §5.1 entry point; manual
  add moved to the + button beside the inventory search field.
- **`ExtractedItem.category` maps to the `Category` enum by name/label, else OTHER**; the
  Confirm screen is the accuracy backstop per SPEC §4.
- **Snapshot photos live under `filesDir/snapshots/session-*/`** with `.small.jpg`
  downscaled (≤1024 px long edge) siblings fed to the model; 30-day pruning is Phase 9.
- **Diff matching = max(Jaro-Winkler, token Jaccard, 0.90 token-subset score) ≥ 0.85** over
  normalized names, greedy one-to-one assignment. The subset rule makes brand-prefixed names
  ("trader joe black bean" ~ "black bean") match; it also means a bare "oil" matches "olive
  oil" — accepted, the Confirm screen is the backstop and Phase 5 synonyms will refine.
- **Diff semantics**: a null extracted quantity is a *confirmation*, not a change (the model
  simply gave no amount); a null inventory quantity firming up to a number *is* a change.
  "Consumed" deletes the item row (+SNAPSHOT_CONSUMED, delta = -old); "Moved" rewrites
  locationId (+SNAPSHOT_MOVED, delta 0); "Still there", unchanged matches, and declined
  quantity changes bump lastSeen/lastConfirmedAt with no change row (SPEC §3.2). Units are
  ignored when deciding "changed" — only quantities count (v1).
- **A MOVED decision without a chosen target degrades to "still there"** rather than
  blocking the commit.
- **First build in this repo has not been machine-verified** (sandbox network restriction
  above). If a version in `libs.versions.toml` fails to resolve, bump only the patch digit
  first — every group/artifact coordinate was verified against Maven listings in July 2026.
