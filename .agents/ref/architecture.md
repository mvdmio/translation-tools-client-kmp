# Architecture & Layout

## Project structure

```
translationtools-client-kmp/
├── src/                                    # Root project: the runtime KMP client
│   ├── commonMain/kotlin/io/mvdm/translationtools/client/
│   │   ├── TranslationToolsClient.kt       # Core client: initialize/get/getCached/observe
│   │   ├── TranslationToolsClientOptions.kt
│   │   ├── TranslationToolsFactory.kt      # TranslationTools.createClient(...)
│   │   ├── TranslationToolsApi.kt          # Remote API abstraction
│   │   ├── TranslationToolsHttpApi.kt      # Ktor-based API implementation
│   │   ├── TranslationSnapshotStore.kt     # Persisted snapshot abstraction
│   │   ├── TranslationRef.kt               # (origin, key) identity
│   │   ├── TranslationModels.kt
│   │   └── TranslationToolsExceptions.kt
│   ├── androidMain/kotlin/...              # AndroidTranslationSnapshotStores
│   ├── jvmMain/kotlin/...                  # JvmTranslationSnapshotStores
│   ├── commonTest/ , jvmTest/             # Tests (see testing.md)
├── translationtools-client-compose/        # Subproject :translationtools-client-compose
│   └── src/commonMain/.../compose/         # Composition locals + stringResource helpers
├── gradle/translationtools-plugin/         # Included build: the Gradle plugin (NOT a root path)
│   └── src/main/kotlin/io/mvdm/translationtools/gradle/
│       ├── TranslationToolsPlugin.kt
│       ├── GenerateTranslationResourcesTask.kt
│       ├── PushTranslationsTask.kt / PullTranslationsTask.kt / InitTranslationToolsTask.kt
│       ├── AndroidStringResourceParser.kt  # parses Android <string> XML
│       ├── TranslationResourceGenerator.kt # emits Translations.*
│       └── BundledSnapshotGenerator.kt     # emits TranslationsBundledSnapshot
├── .github/workflows/                      # CI/CD (Maven Central publish)
├── CONTEXT.md , docs/adr/                  # Domain glossary + decisions
└── README.md                              # Usage documentation
```

## Build topology

- **Composite (included) build.** The root project publishes `translationtools-client-kmp`.
- Subproject `:translationtools-client-compose` publishes `translationtools-client-compose` (Android, JVM, iOS).
- `gradle/translationtools-plugin` is an **included build**, not a root project path — do **not** address it as `:translationtools-plugin`. It has its own delegating wrapper.
- Public package namespace: `io.mvdm.translationtools.client`.

## Runtime client lifecycle

`TranslationToolsClient.initialize()` runs, in order:

1. Restore persisted snapshot from the `TranslationSnapshotStore` when available.
2. Otherwise restore the bundled snapshot (`TranslationsBundledSnapshot`, generated from local XML).
3. Otherwise do a blocking remote refresh from TranslationTools.
4. After restore, optionally do a background refresh (`backgroundRefreshEnabled`).

Read paths:

- `getCached(ref)` — cached value, else XML fallback, else the key itself.
- `get(ref)` — cached value first; fetches from TranslationTools on a cache miss.
- `observe(ref)` — a `Flow` that emits when the translation changes.

Locale resolution order: explicit `locale` arg → `currentLocaleProvider` → project default → `en`.

## Gradle plugin flow

The plugin reads Android XML (`AndroidStringResourceParser`) and generates the typed
`Translations.*` API plus `TranslationsBundledSnapshot` (`TranslationResourceGenerator`,
`BundledSnapshotGenerator`). It also wires generation into Kotlin compilation. Sync tasks
push local XML to TranslationTools and pull remote changes back. iOS Apple `.strings` are
**sync-only** — no codegen, no bundled snapshot, no runtime refresh (see `docs/adr/0001`).

## Important files

| File | Purpose |
|------|---------|
| `src/commonMain/.../TranslationToolsClient.kt` | Core client API and lifecycle |
| `src/commonMain/.../TranslationToolsFactory.kt` | `TranslationTools.createClient(...)` entry point |
| `src/commonMain/.../TranslationToolsHttpApi.kt` | Ktor remote API implementation |
| `src/commonMain/.../TranslationSnapshotStore.kt` | Snapshot persistence abstraction |
| `gradle/translationtools-plugin/.../TranslationToolsPlugin.kt` | Plugin entry point and task wiring |
| `gradle/translationtools-plugin/.../TranslationResourceGenerator.kt` | Generates `Translations.*` |
| `build.gradle.kts` | Root artifact coordinates and version |
