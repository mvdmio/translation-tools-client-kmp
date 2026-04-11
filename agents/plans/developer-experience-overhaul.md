# KMP developer experience overhaul plan

## Goal

- Make TranslationTools feel like normal Android `string.xml` usage.
- Let developers keep editing checked-in `string.xml` files instead of treating `snapshot.json` as the main build input.
- Provide an explicit runtime-backed facade at `Res.string.*`.
- Generate shared-code origin-aware accessors and bundled fallback data from local XML.
- Use TranslationTools as sync and runtime infrastructure, not as the day-to-day authoring format.

## Relationship to the earlier tooling plan

- This plan supersedes the snapshot-first parts of `agents/plans/kmp-translations-tooling.md`.
- The earlier plan is still useful for plugin structure, tests, docs, and publishing, but the source-of-truth decision changes here.

## Current gap

- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/GenerateTranslationResourcesTask.kt` generates from root `snapshot.json`.
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/PullTranslationsTask.kt` writes `snapshot.json`, not local XML.
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/MigrateTranslationsTask.kt` is a one-time import path, not a normal edit-sync loop.
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/AndroidStringResourceParser.kt` only discovers `strings.xml` and only supports a narrow qualifier set.
- `translationtools.yaml` currently carries generation settings that should be conventions in the common case.
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationStringResource.kt`, `TranslationToolsApi.kt`, `TranslationToolsHttpApi.kt`, and `TranslationToolsClient.kt` are still key-only even though the API requires `origin`.
- The current workflow feels like "generate from remote snapshot" instead of "work with `string.xml` like usual".

## Target developer experience

1. Developers keep editing `src/androidMain/res/values*/**/*.xml` like they do in a normal Android project.
2. Developers migrate runtime-backed call sites to `Res.string.foo`.
3. The migration from Android resource references should be mostly mechanical, for example `R.string.*` to `Res.string.*`.
4. Local XML values act as the bundled fallback on every target.
5. `pullTranslations` updates local XML files.
6. `pushTranslations` uploads local XML changes.
7. Regular builds do not require a committed `snapshot.json`.
8. `translationtools.yaml` is only needed for sync tasks and overrides, not for basic code generation.

## Guiding decisions

### Source of truth

- Recommended: local Android resource XML is the authored source for build-time generation and fallback values.
- TranslationTools remains the remote sync source and runtime refresh source.
- `snapshot.json` should stop being the normal developer-facing source artifact.

### Key format

- Recommended: default TranslationTools key equals the Android resource name exactly.
- Example: `action_save` stays `action_save`.
- Keep key overrides only as a legacy escape hatch for projects that already shipped dot-based keys.

### Translation identity

- Translation identity should be `origin + key`, not key-only.
- `origin` is required for KMP requests sent to the API.
- `origin` should be `Gradle project path + normalized base XML file path`.
- Example origins:
- `:app:/strings.xml`
- `:feature:checkout:/checkout.xml`
- Localized variants share the same base-file origin.
- Example:
- `values/strings.xml` and `values-nl/strings.xml` both map to `:app:/strings.xml`.
- Developers should almost never type origins manually; generated resources should carry them.

### Generated API shape

- Recommended: keep a unified `Res.string.*` surface for shared code.
- Reason: this is already close to Android and Compose resource conventions, while still allowing generated resources to carry origin.
- Do not mirror the .NET per-file class shape. Android resources are globally named, so a single `string` namespace is the more native fit.
- Use a separate `TranslationRef` identity type for `origin + key`.
- Keep resource types separate from raw identity, so generated string resources carry a `TranslationRef` plus fallback metadata.

### Build integration

- Recommended: keep Gradle task based generation.
- Reason: the input is XML files, not Kotlin symbols. The current plugin already owns source registration and task wiring.
- Do not introduce KSP unless later profiling shows Gradle-task generation is a real bottleneck.

### Android integration

- Keep the normal Android resource pipeline for authoring and packaging.
- Do not try to replace or suppress Android's generated `R` class.
- The TranslationTools runtime-backed facade is `Res.string.*`, and that should be the primary documented usage surface.
- Existing Android `R.string.*` references can be migrated mechanically where runtime-backed TranslationTools behavior is desired.

## Proposed architecture

### 1. Build one XML-first resource model

- Replace the current split between parser, snapshot model, and generator input with one canonical in-memory model built from local XML.
- Parse every `*.xml` file under configured `values*` directories, not only `strings.xml`.
- Compute one logical base-file origin per resource XML file, shared across locale variants.
- Support normal Android locale directory shapes:
- `values`
- `values-nl`
- `values-pt-rBR`
- BCP-47 folders such as `values-b+sr+Latn`
- Ignore non-locale qualifiers like `values-night` for TranslationTools sync, but report clear warnings.
- Track at least this metadata per entry:
- origin
- resource name
- remote key
- default-locale value
- per-locale values
- owning base-file path
- flags such as `translatable=false`
- Detect duplicates across files, not just inside a single XML file.

### 2. Generate common code from local XML

- Rewrite `generateTranslationResources` to read the XML-first model instead of `TranslationSnapshotFile`.
- Keep generating `Res.kt`, but derive it from local XML.
- Generate `ResBundledSnapshot.kt` from local XML as well.
- That gives the runtime client a bundled snapshot without needing committed `snapshot.json`.
- Keep generated names equal to Android resource names whenever possible.
- Keep collision hashing only as a fallback for legacy override scenarios.
- Introduce `TranslationRef` for public `origin + key` identity.
- Update `TranslationStringResource` so generated entries carry a `TranslationRef` plus fallback metadata.

Recommended generated shape:

```kotlin
public object Res {
   public object string {
      public val action_save: TranslationStringResource =
         TranslationStringResource(
            ref = TranslationRef(
               origin = ":app:/strings.xml",
               key = "action_save",
            ),
            fallback = "Save",
         )
   }
}
```

Expected usage:

```kotlin
val title = client.getCached(Res.string.action_save)
val title = stringResource(Res.string.action_save)
```

### 3. Make `pullTranslations` write local XML

- Rewrite `pullTranslations` so its primary output is local `string.xml` content, not `snapshot.json`.
- Use `origin` as the logical remote file identity and map it back to local XML files.
- Preserve existing file splits when possible.
- For remote origins that do not map cleanly to existing local files, ignore those entries and emit warnings.
- Keep stable ordering and formatting so pull diffs stay reviewable.
- Normalize output after the first managed write. Do not try to preserve exact existing comments or whitespace.
- Any future pull-side prune behavior should be explicit and off by default.

Recommended pull algorithm:

1. Parse current local default-locale XML files.
2. Build an origin-to-owning-file map.
3. Fetch remote metadata and origin-aware locale values.
4. Ignore remote entries whose origins have no local mapping and warn.
5. Merge remote values into per-file models by `origin + key`.
6. Add new keys to a mapped file when the origin is known but the key is new.
7. Write default and localized XML files.
8. Regenerate common Kotlin sources.

### 4. Add `pushTranslations` as the normal upload path

- Add a new `pushTranslations` task that reads local XML and uploads the full project state.
- This becomes the day-to-day upload command after developers edit local XML.
- Deprecate `migrateTranslations` or turn it into a thin compatibility alias for the first release of the new flow.
- Upload `origin + key + locale -> value` state.
- Because origin includes file identity, moving a string between XML files changes its remote identity. `pushTranslations` must treat that as a move or as create-plus-delete when prune is enabled.
- `pushTranslations` should not automatically run `pullTranslations`.
- Remote deletions should only happen in explicit prune mode.

Recommended push algorithm:

1. Parse local XML into the canonical model.
2. Convert the model to `origin + key -> locale -> value` payloads.
3. Upload to `/api/v1/translations/project/import`.
4. Report created and updated counts.
5. Detect origin changes caused by file moves and surface clear guidance when stale remote refs need pruning.
6. Stop after upload. Do not auto-pull.

### 5. Keep runtime and Compose integration minimal

- Replace key-only runtime identity with origin-aware resources and translation items.
- Remove raw key-only public APIs such as `get(key)`, `getCached(key)`, and `observe(key)`.
- `TranslationToolsClient` public APIs should require origin-aware resources, `TranslationRef`, or explicit `origin + key` inputs.
- Generated resources are the main facade, so most callers should not need to manually pass `origin`.
- `translationtools-client-compose` can keep `stringResource(resource)` as the main shared-code convenience because generated resources carry origin internally.
- Low-level/manual access can still expose `client.get(origin, key)` or `client.get(ref)` style APIs for advanced scenarios.
- The main runtime changes are that bundled fallback data now comes from local XML instead of `snapshot.json`, and cached/fetched items are keyed by `origin + key`.
- Avoid adding a global singleton client just to mimic the .NET API surface. Android developers already expect scoped resource access rather than a static global resource class.
- Phase 1 should support `translatable=false` entries as local-only generated resources that keep local fallbacks and avoid remote sync or fetch paths.
- This is a breaking public API change and should ship as a major version bump.

### 6. Reduce required configuration

- `generateTranslationResources` should work without `translationtools.yaml` if the project has resource XML files.
- Keep default resource discovery rooted at `src/androidMain/res`.
- Keep `translationtools.yaml` as the primary long-term sync config surface.
- Derive the origin module segment from `project.path`.
- Derive the generated package from Gradle conventions when possible.
- Default generated package: `android.namespace + ".translations"`.
- Keep explicit override support for projects that need a custom package.
- Fix the generated object name to `Res` by default.
- Treat custom `objectName` as a backward-compatibility option, not the happy path.
- Keep API key lookup in this priority order for network tasks:
- `-Ptranslationtools.apiKey=...`
- `TRANSLATIONTOOLS_API_KEY`
- `translationtools.yaml`

### 7. Add stronger diagnostics

- Duplicate resource name across XML files for the same logical locale.
- Warning diagnostics for unsupported XML constructs in phase 1.
- Warning diagnostics for unsupported locale qualifiers.
- Pull conflict when multiple local files claim the same remote origin.
- Pull conflict when multiple local entries would map to the same `origin + key`.
- Generated property collision after sanitization.
- Override map conflicts.
- Origin changes caused by moving a key between XML files.
- Missing default-locale ownership for a pulled origin.
- Warn when localized-only keys exist and fallback behavior will be weak.

## Scope boundaries

### In scope

- XML-first code generation.
- Origin-aware runtime and sync model.
- Local XML as bundled fallback source.
- `pullTranslations` writing XML.
- `pushTranslations` reading XML.
- Conventions-first setup.
- Android and shared-code developer experience improvements.

### Out of scope for the first delivery

- Replacing Android `R` generation itself.
- WebSocket or live-update protocol changes.
- A brand-new non-Android authoring format.
- Perfect parity for every Android resource feature on day one.

## Feature support plan

### Phase 1 support

- `<string>` entries.
- escaped text and CDATA preservation as plain string values.
- formatted strings are treated as raw strings with no typed helper generation yet.
- normal locale directories.
- `translatable=false` entries as local-only generated resources.
- deterministic generation and sync.
- `<plurals>` and `<string-array>` are skipped with warning diagnostics.

### Later support

- formatted string argument metadata and helpers.
- `<plurals>`.
- `<string-array>`.
- richer qualifier support if real projects need it.

## API and backend implications

- Runtime fetch requests must include `origin`.
- Locale and project pull responses must include `origin` per translation item.
- Project import payloads must include `origin` per item.
- Because origin is required, this overhaul likely needs coordinated KMP client, Gradle plugin, and backend contract changes.
- No checked-in ownership manifest is required; ownership comes from origin and local XML layout.

Potential backend follow-up, only if needed later:

- Dedicated move semantics could make file-origin changes cheaper than create-plus-prune behavior.

## Likely implementation surface

- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationRef.kt`
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationStringResource.kt`
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationModels.kt`
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationToolsApi.kt`
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationToolsHttpApi.kt`
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationToolsClient.kt`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/AndroidStringResourceParser.kt`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/TranslationResourceGenerator.kt`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/GenerateTranslationResourcesTask.kt`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/PullTranslationsTask.kt`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/MigrateTranslationsTask.kt`
- new `PushTranslationsTask.kt`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/TranslationToolsPlugin.kt`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/TranslationToolsConfig.kt`
- `src/commonTest/kotlin/io/mvdm/translationtools/client/TranslationToolsClientTests.kt`
- `translationtools-client-compose/src/commonMain/kotlin/io/mvdm/translationtools/client/compose/TranslationStringResources.kt`
- `README.md`
- plugin functional and unit tests under `gradle/translationtools-plugin/src/test/...`

## Phases

## Phase 0 - design lock

### Work

- Lock the XML-first source-of-truth decision.
- Lock the default key format to the Android resource name.
- Lock translation identity to `origin + key`.
- Lock origin format to `Gradle project path + base XML file path`.
- Lock localized variants to share the same base-file origin.
- Lock the shared generated surface to `Res.string.*`.
- Lock the decision that basic generation works without `translationtools.yaml`.
- Lock the decision that public runtime APIs become origin-aware.

### Acceptance

- This plan is accepted as the new direction.
- The older snapshot-first plan is treated as historical context, not the active recommendation.
- The implementation is understood to require a major version bump because the public runtime API shape changes.

## Phase 1 - XML-first code generation

### Work

- Expand resource discovery to all `values*/**/*.xml` files.
- Build the canonical XML resource model.
- Introduce `TranslationRef` plus origin-aware resource and snapshot models.
- Generate `Res.kt` from local XML.
- Generate `ResBundledSnapshot.kt` from local XML.
- Update runtime client and API contracts to use origin-aware translations.
- Remove the build-time requirement for `snapshot.json`.

### Acceptance

- A project with local `string.xml` files builds successfully without `snapshot.json`.
- Shared code can use generated `Res.string.*` entries whose origins and fallbacks come from local XML.
- Android and shared code can use generated `Res.string.*` as the runtime-backed facade.
- The generated API surface uses `TranslationRef` under the hood for identity.

## Phase 2 - XML pull

### Work

- Introduce XML writing and merge logic.
- Rewrite `pullTranslations` to update local XML files.
- Preserve file ownership through origin mapping and stable formatting.
- Regenerate Kotlin sources after pull.

### Acceptance

- Pull updates local resource XML and generated sources.
- Developers can review translation changes as normal XML diffs.
- Remote values map back to the expected local files through origin.
- Remote entries with unknown origins are ignored with warnings.
- New remote keys for known origins are added to the mapped local file.
- No committed `snapshot.json` is required for the happy path.

## Phase 3 - XML push and migration cleanup

### Work

- Add `pushTranslations`.
- Deprecate `migrateTranslations` or turn it into a compatibility alias.
- Add explicit prune behavior.
- Update docs to describe the new normal workflow.

### Acceptance

- Developers can edit local XML and push those changes to TranslationTools.
- The recommended workflow is symmetrical: edit, push, pull.
- Origin changes caused by file moves are explicit and documented.
- Push does not delete remote items unless explicit prune mode is used.
- Push uploads origin-aware payloads derived from `TranslationRef` identities.
- The old migrate-first flow is no longer the main documented path.

## Phase 4 - parity improvements

### Work

- Add support for `translatable=false` local-only resources.
- Design formatted-string helpers.
- Add plurals and arrays if the runtime API shape is clear.
- Expand qualifier handling based on real project samples.

### Acceptance

- Common Android resource patterns work with minimal caveats.
- Unsupported XML features remain visible through warnings instead of silently disappearing.
- Remaining limitations are explicit, tested, and documented.

## Test plan

### Parser tests

- all XML files under `values*` are discovered
- locale qualifier normalization is correct
- origin computation is correct
- duplicate detection works across files
- unsupported qualifiers are ignored with warnings
- unsupported node types are reported with warnings

### Generator tests

- generated properties match Android resource names
- generated resources include origin
- generated resources include `TranslationRef`
- default-locale values become fallbacks
- bundled snapshot generation comes from local XML
- generation is deterministic

### Pull tests

- existing file ownership is preserved via origin
- new keys for known origins are added to the mapped file
- unknown origins are ignored with warnings
- localized files are written correctly
- stable formatting avoids noisy diffs

### Push tests

- XML is converted into correct origin-aware import payloads
- locale coverage is preserved
- file moves are surfaced as origin changes
- push does not auto-pull
- prune mode only removes when explicitly enabled

### Functional tests

- sample KMP project builds without `snapshot.json`
- shared code compiles with generated `Res.string.*`
- Android code can migrate mechanically to generated `Res.string.*`
- origin-aware runtime calls resolve expected values
- pull then build then test is green

## Docs and migration

- Rewrite `README.md` around XML-first authoring.
- Show `Res.string.*` as the primary runtime-backed facade for both Android and shared code.
- Document the origin-aware runtime model and the fact that generated resources hide origins from most callers.
- Document `TranslationRef` as the low-level identity model behind generated resources.
- Add `pushTranslations` to the documented workflow.
- Explain that `translationtools.yaml` is optional for build-only generation.
- Document that unknown pulled origins are ignored with warnings.
- Document that moving a string between XML files changes its origin.
- Document the recommended migration from `R.string.*` references to `Res.string.*`.
- Document that `pushTranslations` does not auto-run `pullTranslations`.
- Document that deletions require explicit prune mode.
- Document that formatted strings are plain strings in phase 1.
- Document that `<plurals>` and `<string-array>` are warned and skipped in phase 1.
- Provide a migration guide from the current snapshot-first flow.

## Decisions locked

- `pullTranslations` should normalize XML output after the first managed write instead of preserving exact comments and formatting.
- `translationtools.yaml` stays the primary sync config surface.
- `translatable=false` support is required in phase 1 as local-only generated resources.
- KMP translation identity is `origin + key`, not key-only.
- `TranslationRef` is the low-level public identity type.
- `origin` is required for API calls.
- `origin` is `Gradle project path + normalized base XML file path`.
- The module part of origin is always `project.path`.
- Localized variants share the same base-file origin.
- `Res.string.*` is the explicit primary runtime-backed facade.
- Migration from `R.string.*` to `Res.string.*` is expected and should be mostly mechanical.
- Public runtime APIs become origin-aware.
- The low-level public API uses `TranslationRef` plus resource types, not one combined identity-and-fallback type.
- Raw key-only public APIs are removed.
- `pushTranslations` does not auto-run `pullTranslations`.
- Remote deletions require explicit prune mode.
- Phase 1 formatted strings stay raw; typed helpers come later.
- Phase 1 warns and skips `<plurals>` and `<string-array>`.
- Unknown pulled origins are ignored with warnings.
- New pulled keys for known origins are added to the mapped local file.
- No checked-in ownership manifest will be added. Pull behavior must rely on origin and local XML ownership.

## Done when

- Developers can work primarily in local `string.xml` files.
- Shared code gets generated origin-aware common accessors from those same XML files.
- Android and shared code can use the explicit runtime-backed `Res.string.*` facade.
- Pull and push operate on local XML rather than a committed `snapshot.json`.
- The documented happy path feels much closer to using Android string resources without the library.
