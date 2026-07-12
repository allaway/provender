# Provender — Claude Code project guide

Provender is a Kotlin / Jetpack Compose Android app that turns photos of your pantry into a
living food inventory and feeds you matching + generated recipes. **All AI inference is
on-device** (LiteRT-LM + Gemma 3n, ML Kit). No AI API keys, no cloud AI, ever (a cloud
fallback may arrive later behind the `LlmEngine` interface, off by default). Read `SPEC.md`
for the full product & technical spec before making changes.

## Phase plan (from SPEC.md §6)

- [x] **Phase 0 — Scaffold**: Gradle project, Hilt, Room, Compose navigation, theme, Settings shell, CI-friendly build
- [x] **Phase 1 — Inventory core**: entities/DAOs, manual CRUD, locations, search (FTS), staples, change log
- [ ] **Phase 2 — Model runtime**: LiteRT-LM integration, model download manager + progress UI, device capability gating, hidden debug prompt screen (`LlmEngine` interface + fake already exist from Phase 0)
- [ ] **Phase 3 — Vision capture**: CameraX flow, ML Kit OCR, extraction prompt + parsing, Confirm screen (seed mode), barcode mode + Open Food Facts cache
- [ ] **Phase 4 — Diffing**: fuzzy matching, three-part diff UI, change-log integration
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

Requires JDK 17+ (JDK 21 works) and the Android SDK (compileSdk 36). Gradle wrapper is 8.14.3,
AGP 8.13.0, Kotlin 2.2.20 — see `gradle/libs.versions.toml` for everything; all versions live
in the catalog, never inline in build files.

## Package layout (single `app` module, package root `com.provender`)

```
app/src/main/java/com/provender/
  data/        # Room entities, DAOs, database, repositories
  network/     # TheMealDB client, Open Food Facts client, model downloader (Phase 6/3/2)
  ai/          # LlmEngine interface, FakeLlmEngine; LiteRT-LM impl + prompts arrive in Phase 2
  mlkit/       # barcode + OCR wrappers (Phase 3)
  paprika/     # .paprikarecipes import (Phase 5)
  matching/    # normalization, synonym map, scoring (normalizer exists; rest Phase 5)
  roulette/    # deck building, filters (Phase 7)
  generate/    # flexible-recipe prompt building + validation + cache (Phase 8)
  ui/          # Compose screens: theme/, navigation/, inventory/, recipes/, roulette/, settings/
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
