# Provender — Product & Technical Spec

A personal Android app that turns photos of your pantry, fridge, and freezer into a living inventory, then feeds you: matching recipes from the internet and your Paprika library, a tunable "meal roulette," and on-the-fly flexible recipes (bowls, sandwiches, wraps) built from what you actually have.

**Design principle: on-device first.** All AI inference (photo → inventory extraction, recipe generation) runs locally on the phone. No AI API keys, no per-photo cloud costs, photos never leave the device. Plain HTTP is used only for non-AI needs (fetching public recipe data, downloading model weights once). An optional cloud-AI fallback may be added later behind a settings toggle, off by default.

---

## 1. Core Concepts

**Inventory** — the canonical list of food items on hand, each tied to a storage location (pantry, fridge, freezer, spice rack, etc.).

**Snapshot** — a photo session of one location. The on-device vision model extracts items from the photos; the app diffs the extraction against the current inventory for that location and proposes changes (added, removed, quantity changed) for user confirmation.

**Change Log** — an append-only history of confirmed inventory changes, so consumption patterns are visible over time.

**Recipe Sources** — pluggable providers: Paprika library import, internet recipe APIs, and locally generated flexible meals.

**Matching** — deterministic local scoring of recipes against inventory: "can make now," "missing 1–2 items," etc.

**Roulette** — a swipeable card deck of meal candidates, tunable by time of day, savory↔sweet, and effort.

---

## 2. On-Device AI Architecture

Three tiers, cheapest first — the fancy model is a last resort, not the front door:

**Tier 1 — Deterministic ML Kit (instant, tiny):**
- **Barcode scanning** (ML Kit, fully on-device) for packaged goods: point at a barcode → exact product via a local-first lookup (cache table seeded from Open Food Facts; network lookup only on cache miss, results cached forever). This is the highest-precision entry path and should be offered prominently in the capture flow.
- **Text recognition (OCR)** (ML Kit, on-device) to read labels in shelf photos; recognized text is passed to the VLM as auxiliary context to improve item naming.

**Tier 2 — Local VLM (the workhorse):**
- **Gemma 3n (E2B, with E4B as a quality option)** running via the **LiteRT-LM Android (Kotlin) API** — Google's current recommended stack; note the older MediaPipe LLM Inference API is deprecated in favor of LiteRT-LM. Gemma 3n is multimodal (image + text in, text out) and designed for phones; the `.litertlm` model files are downloaded once from Hugging Face/Google on first run (~2–4 GB, Wi-Fi-only download with progress UI, stored in app storage).
- Used for: photo → structured inventory extraction, and flexible-recipe generation.
- **Constrained decoding / strict JSON**: use LiteRT-LM's constrained decoding where available to force schema-valid JSON output; always retain a lenient parser + retry (1 retry with a "return only JSON" reprimand) + graceful failure path.
- Session is initialized eagerly at app start (Application.onCreate) to hide model-load latency; benchmark CPU vs GPU backends per device and persist the faster choice (field reports show CPU sometimes wins on cold start).
- Photos downscaled (≤768–1024 px long edge) before inference; multiple photos per snapshot processed sequentially with a progress indicator. Extraction is expected to take seconds-to-tens-of-seconds per photo — the UI treats it as a background job with a notification, never a blocking spinner.

**Tier 3 — nothing (deterministic code):**
- Ingredient parsing, name normalization, matching, scoring, roulette deck-building are all plain Kotlin. No model involved.

**Hardware reality check:** requires a reasonably modern phone (≥6 GB RAM recommended for E2B). The app should detect insufficient RAM and degrade gracefully (barcode/OCR/manual entry still fully functional; VLM features disabled with an explanatory message).

---

## 3. Feature Requirements

### 3.1 Photo-Based Inventory Capture
- Capture one or more photos per session via CameraX (or pick from gallery); barcode-scan mode available in the same capture screen for packaged items.
- Each session is tagged with a storage location (user picks from a configurable list; defaults: Pantry, Fridge, Freezer, Spices).
- Photos + OCR text are run through the local Gemma 3n session with a strict-JSON extraction prompt returning per item: `name`, `quantity_estimate`, `unit`, `category`, `confidence`, `notes` (e.g., "half full", "unopened").
- Extraction results shown on a **Confirm screen**: editable list; each row shows name, qty, unit, confidence badge. User can edit, delete, add missed items, then commit.
- First session per location seeds inventory; later sessions trigger **diffing** (see 3.2).

### 3.2 Inventory Diffing & Change Log
- On a new snapshot for a location, fuzzy-match extracted items against current inventory at that location (normalized names: lowercase, singularize, strip brand words; Jaro-Winkler or token-set ratio ≥ threshold). Pure Kotlin, no model.
- Propose a three-part diff: **New items**, **Missing items** (in inventory, not in photo — user chooses: consumed / still there but hidden / moved), **Changed quantities**.
- All confirmations write `InventoryChange` rows (item, delta, reason, timestamp, snapshot id).
- Items marked "hidden but still there" get `last_confirmed_at` updated without a change entry.

### 3.3 Inventory Management (Manual)
- Full CRUD on items independent of photos.
- Fields: name, category (produce, dairy, protein, grain, canned, condiment, spice, snack, baking, beverage, frozen, other), quantity + unit (count, g, kg, oz, lb, ml, l, "some/low/plenty" fuzzy option), location, staple flag, last_seen_at, notes.
- **Staples** (oil, salt, flour…) are assumed available for matching unless explicitly marked out.
- Search + filter by location/category; low-confidence and stale items (not seen in N days) surfaced for review.

### 3.4 Paprika Library Import
- Import a `.paprikarecipes` export file via Android document picker (the file is a ZIP archive; each entry is a **gzipped JSON** recipe). Parse: name, ingredients (raw lines), directions, categories, photo (base64), rating, source URL.
- Parse ingredient lines into structured `(quantity, unit, ingredient)` using a rule-based parser (regex + unit dictionary); keep the raw line as fallback. No model needed.
- Store in local Recipe table with `source = PAPRIKA`. Re-import is idempotent (match on Paprika UID).

### 3.5 Internet Recipes
- `RecipeSource` interface so providers are pluggable.
- v1 provider: **TheMealDB** (free, no key, filter-by-ingredient endpoint) — plain HTTP, no AI.
- Fetched recipes stored with `source = WEB`, always retaining the source URL. Cached locally so matching works offline.

### 3.6 Matching Engine (fully local, deterministic)
- Normalize both recipe ingredients and inventory names to a shared canonical form (small synonym map: "scallion"="green onion", etc., expandable via Settings).
- Score = weighted coverage: required ingredients present / total required, with staples auto-counted and garnish/optional lines down-weighted.
- Output tiers: ✅ Make now (100% or missing only staples) · 🟡 Close (missing ≤2 non-staples, shown as a mini shopping list) · hidden otherwise.
- Re-computed reactively when inventory changes (Flow).

### 3.7 Meal Roulette
- Swipeable card deck (Compose pager / swipe gestures): each card = a recipe or generated meal with photo (if available), title, "you have X of Y ingredients," est. time.
- Tuning controls above the deck:
  - **Meal type**: auto from time of day (breakfast <11am, lunch 11–4, dinner 4–9, snack otherwise) with manual override.
  - **Savory ↔ Sweet** slider.
  - **Effort**: quick (<20 min) / normal / project.
- Deck composition: shuffled blend of matched Paprika recipes, matched web recipes, and generated flexible meals (see 3.8), filtered by the tuning controls. Swipe right = save to "Tonight" shortlist; swipe left = skip (deprioritized this session).
- Deck building is deterministic local code; only the generated-meal cards involve the VLM, and those are pre-generated in the background and cached (see 3.8) so the roulette itself is instant.

### 3.8 Generated Flexible Recipes (local VLM, text-only mode)
- Template archetypes: **bowl, sandwich, wrap, salad, stir-fry, quesadilla, pasta toss, grain skillet, toast, smoothie, parfait**.
- Gemma 3n prompt with current inventory + archetype + tuning prefs → returns 3–5 structured ideas: name, archetype, ingredients used (must be subset of inventory + staples), 3–6 step instructions, est. time, savory/sweet tag.
- **Pre-generation strategy**: because local generation is slow, ideas are generated in the background (WorkManager) whenever inventory changes meaningfully, and cached per (archetype × meal-type × savory/sweet) bucket. Roulette draws from the cache; a "generate fresh ideas" button forces regeneration with progress UI.
- Validated deterministically: any idea referencing an item not in inventory is dropped or flagged.
- Generated ideas can be saved as permanent recipes (`source = GENERATED`).

---

## 4. Tech Stack & Architecture

| Layer | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3, dark/light |
| Architecture | MVVM + Repository, unidirectional data flow |
| DI | Hilt |
| Async | Coroutines + Flow; WorkManager for background generation/model download |
| Local DB | Room (with FTS4 for item/recipe search) |
| Camera | CameraX |
| On-device AI | **LiteRT-LM Android (Kotlin) API + Gemma 3n E2B/E4B (.litertlm)** for vision extraction & generation; **ML Kit** barcode scanning + text recognition |
| Networking (non-AI only) | Retrofit + OkHttp + kotlinx.serialization — TheMealDB, Open Food Facts barcode lookups, one-time model download |
| Images | Coil; snapshot photos stored in app-private storage, pruned after 30 days |
| Min SDK | 26 (VLM features gated on device capability check) · Target SDK: latest stable |
| Testing | JUnit + Turbine for ViewModels; Room DAO tests; parser unit tests (ingredient lines, Paprika import, extraction JSON, normalization); VLM behind an interface with a fake for tests |

### Module / package layout (single Gradle module to start)
```
app/src/main/java/com/provender/
  data/        # Room entities, DAOs, database, repositories
  network/     # TheMealDB client, Open Food Facts client, model downloader
  ai/          # LlmEngine interface, LiteRT-LM impl, prompts, JSON schemas,
               #   constrained-decoding config, fake impl for tests
  mlkit/       # barcode + OCR wrappers
  paprika/     # .paprikarecipes import
  matching/    # normalization, synonym map, scoring
  roulette/    # deck building, filters
  generate/    # flexible-recipe prompt building + validation + cache
  ui/          # Compose screens: inventory, capture, confirm, recipes, roulette, settings
  di/
```

### Key data entities
```
InventoryItem(id, name, nameNormalized, category, quantity, unit, locationId,
              isStaple, lastSeenAt, lastConfirmedAt, notes, barcode?)
StorageLocation(id, name, sortOrder)
Snapshot(id, locationId, createdAt, photoUris, status)
InventoryChange(id, itemId, snapshotId?, delta, reason, createdAt)
Recipe(id, source[PAPRIKA|WEB|GENERATED], title, ingredientsRaw, directions,
       photoUri?, sourceUrl?, paprikaUid?, mealTypes, savorySweet, estMinutes)
RecipeIngredient(id, recipeId, rawLine, qty?, unit?, nameNormalized, isOptional)
BarcodeCache(barcode, name, brand, category, fetchedAt)
GeneratedIdeaCache(id, archetype, mealType, savorySweet, json, inventoryHash, createdAt)
```

### Vision extraction contract (local)
Prompt demands **only** a JSON array, e.g.:
```json
[{"name":"black beans","quantity":3,"unit":"can","category":"canned",
  "confidence":0.9,"notes":"unopened"}]
```
Enforced via constrained decoding where supported; client also strips code fences, parses leniently with kotlinx.serialization, retries once on malformed output, and lands in a recoverable error state (never crashes). Expect the small model to be imperfect — the Confirm screen is the accuracy backstop, and OCR text + barcode hits reduce how much the VLM must carry.

### AI abstraction
All model access goes through a single `LlmEngine` interface (`extractInventory(photos, ocrText): Result<List<ExtractedItem>>`, `generateIdeas(...): Result<List<MealIdea>>`). The LiteRT-LM implementation is the default and only v1 implementation; a cloud implementation can be added later behind the same interface without touching feature code.

---

## 5. Screens (v1)
1. **Home / Inventory** — grouped by location, search bar, stale-item banner, FAB → capture.
2. **Capture** — camera with location chip selector, photo/barcode mode toggle, multi-shot, review thumbnails, "Analyze."
3. **Confirm** — extraction/diff review, edit rows, commit.
4. **Recipes** — tabs: Matches · Paprika · Saved/Generated; import button.
5. **Roulette** — tuning bar + card deck + "Tonight" shortlist sheet.
6. **Settings** — model management (download/delete, E2B↔E4B, backend CPU/GPU), locations editor, staples list, synonym overrides.

---

## 6. Build Phases
- **Phase 0 — Scaffold**: Gradle project, Hilt, Room, Compose navigation, theme, Settings shell, CI-friendly build.
- **Phase 1 — Inventory core**: entities/DAOs, manual CRUD, locations, search, staples, change log.
- **Phase 2 — Model runtime**: LlmEngine interface + fake; LiteRT-LM integration; model download manager with progress UI; device capability gating; a hidden debug screen to prompt the model directly.
- **Phase 3 — Vision capture**: CameraX flow, ML Kit OCR, extraction prompt + parsing, Confirm screen (seed mode). Barcode mode + Open Food Facts cache.
- **Phase 4 — Diffing**: fuzzy matching, three-part diff UI, change-log integration.
- **Phase 5 — Paprika + matching**: import, ingredient parser, matching engine, Matches tab.
- **Phase 6 — Web recipes**: TheMealDB provider behind RecipeSource.
- **Phase 7 — Roulette**: deck, tuning, shortlist.
- **Phase 8 — Generation**: flexible-recipe prompts, background pre-generation cache, validation, save.
- **Phase 9 — Polish**: stale review flow, snapshot photo pruning, DB export/backup.

Each phase should end with a running, installable app and passing tests.

## 7. Non-Goals (v1)
Cloud AI (kept as a possible opt-in later via LlmEngine), multi-user/sync, expiry tracking, nutrition data, shopping list (beyond the "missing items" mini-list), iOS.
