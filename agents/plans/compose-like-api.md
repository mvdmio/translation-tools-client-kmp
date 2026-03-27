# Compose-like API plan

## Status

Implemented for the first shipped slice.

This now ships:

- typed string resources in the runtime
- generated `Res.string.*` accessors
- explicit Gradle pull workflow
- offline builds from a checked-in snapshot
- separate Compose integration in `translationtools-client-compose`

Deferred items remain deferred.

## Goals

- make Kotlin call sites feel closer to Compose resources
- keep `TranslationToolsClient` as the runtime engine
- let UI code read a string synchronously from cache or fallback
- generate typed accessors from pulled translation data
- keep the first implementation small enough to ship in steps

Target usage:

```kotlin
val title = client.getCached(Res.string.home_title)
```

Manual resources must also work:

```kotlin
val title = client.getCached(
    TranslationStringResource(key = "home.title", fallback = "Home")
)
```

## Out of scope for v1

- formatting args
- plurals
- push workflows
- published standalone Gradle plugin
- operator helpers like `client[Res.string.home_title]`

## Locked decisions

- add `TranslationStringResource` as a `data class` in `commonMain`
- keep existing string-key APIs unchanged
- add fallback-aware typed overloads that return `String` and `Flow<String>`
- do not change `TranslationToolsApi` for this slice
- build-time sync uses a checked-in `translationtools/snapshot.json`
- generated Kotlin lives under `build/generated/...` only
- `pullTranslations` is the only public sync task in v1
- normal `build`, `test`, and IDE sync stay offline
- Gradle build logic starts as an included build in this repo
- generated fallback text comes from the default locale when present
- only `commonMain` gets generated resources in v1

## Runtime API

### Resource type

Add a new file in `src/commonMain`:

```kotlin
public data class TranslationStringResource(
    val key: String,
    val fallback: String? = null,
)
```

Why this shape:

- easy to construct by hand
- trivial for codegen
- no interface hierarchy yet
- easy to extend later with separate plural types

### Client overloads

Add overloads to `TranslationToolsClient`:

```kotlin
public fun getCached(
    resource: TranslationStringResource,
    locale: String? = null,
): String

public suspend fun get(
    resource: TranslationStringResource,
    locale: String? = null,
): String

public fun observe(
    resource: TranslationStringResource,
    locale: String? = null,
): Flow<String>
```

Semantics:

- `getCached(resource)` returns cached value, else `resource.fallback`, else `resource.key`
- `get(resource)` delegates to existing `get(key, locale, defaultValue)` with `resource.fallback`
- `observe(resource)` maps the existing nullable flow to fallback-aware `Flow<String>`
- key validation, locale resolution, refresh, and persistence stay in the current runtime

Explicitly not in scope for these overloads:

- vararg format args
- new API or backend calls
- new persistence format

### Compatibility

- existing `String` overloads stay as-is
- current consumers do not need to change
- implementation bumped the published version to `0.3.0`

## Generated API

### Output shape

Generator emits one Kotlin file named `<objectName>.kt`. Default object name is `Res`.

Example:

```kotlin
package com.example.translations

import io.mvdm.translationtools.client.TranslationStringResource

public object Res {
    public object string {
        public val home_title: TranslationStringResource =
            TranslationStringResource(
                key = "home.title",
                fallback = "Home",
            )
    }
}
```

Locked decisions:

- top-level object name is configurable; default `Res`
- nested container name is fixed: `string`
- generated members are `val`
- generated member type is explicit
- output is sorted by raw key
- generator embeds the default-locale value as `fallback` when that value is non-null

### Naming algorithm

Raw key -> Kotlin property name.

Rules:

- replace `.`, `-`, `/`, whitespace, and repeated separators with `_`
- lowercase the result
- trim leading and trailing `_`
- if the result is empty, use `key`
- if the result starts with a digit, prefix `key_`
- if the result is a Kotlin keyword, suffix `_`
- if multiple raw keys map to the same property name, append `__` plus the first 8 hex chars of `sha256(rawKey)` to every key in that collision group

Examples:

- `home.title` -> `home_title`
- `checkout/primary-cta` -> `checkout_primary_cta`
- `404.title` -> `key_404_title`
- `object` -> `object_`

This keeps the normal case readable and the collision case deterministic.

### Fallback behavior

Fallback source is the default locale in the snapshot.

Rules:

- if default-locale value exists, emit it as `fallback`
- if default-locale value is `null` or missing, emit `fallback = null`
- runtime typed overloads still return the key if both cache and fallback are missing

## Snapshot contract

Build-time sync owns a dedicated snapshot format. Do not reuse the runtime persistence JSON as the checked-in source of truth.

Default path:

- `translationtools/snapshot.json`

Rules:

- checked in to source control
- schema-versioned
- no timestamps or other volatile fields
- locales sorted
- keys sorted within each locale
- file rewrite skipped when content is unchanged
- generator reads this file only; it never hits the network

Schema v1:

```json
{
  "schemaVersion": 1,
  "project": {
    "defaultLocale": "en",
    "locales": ["en", "nl"]
  },
  "translations": {
    "en": {
      "home.title": "Home"
    },
    "nl": {
      "home.title": "Start"
    }
  }
}
```

Notes:

- translation values are `string | null`
- snapshot must always include the remote default locale
- configured locales are additive on top of the default locale

## Gradle build logic

### Location

Start with an included build so the plugin code is isolated and testable.

Suggested layout:

```text
gradle/translationtools-plugin/
```

Why this path:

- cleaner than ad-hoc root tasks
- easier to test than `buildSrc`
- easy to extract later if the plugin becomes reusable

`settings.gradle.kts` should add:

```kotlin
includeBuild("gradle/translationtools-plugin")
```

### Public surface

Extension:

```kotlin
translationTools {
    configFile.set(layout.projectDirectory.file("translationtools.yaml"))
}
```

Public task:

- `pullTranslations`

Internal task:

- `generateTranslationResources`

Rules:

- `pullTranslations` does network sync and then triggers generation
- `generateTranslationResources` is local-only and safe in offline builds
- `compileKotlin*` depends on `generateTranslationResources`
- `build` must not depend on `pullTranslations`

### `pullTranslations`

Command:

```bash
./gradlew.bat pullTranslations
```

Behavior:

1. load config
2. resolve API key
3. fetch project metadata
4. fetch default locale plus configured locales
5. write stable snapshot JSON
6. run `generateTranslationResources`

Failure cases:

- config file missing
- API key missing
- auth failed
- network failure
- invalid response payload
- deterministic codegen collision bug

Example output:

```text
> Task :pullTranslations
Loaded config from translationtools.yaml
Pulled locales: en, nl
Updated translationtools/snapshot.json
Updated build/generated/source/translationtools/commonMain/kotlin/.../Res.kt
```

No-change case:

```text
> Task :pullTranslations
Loaded config from translationtools.yaml
Remote snapshot unchanged
Generated sources unchanged
```

### `generateTranslationResources`

Inputs:

- snapshot file
- generated package and object config

Outputs:

- `build/generated/source/translationtools/commonMain/kotlin/<package path>/<objectName>.kt`

Behavior:

- no network access
- deterministic file content
- skip rewrite if unchanged
- wire output dir into `commonMain`
- if snapshot is missing, fail with a message telling the user to run `./gradlew.bat pullTranslations`

## Configuration

Default config file:

- `translationtools.yaml`

V1 schema:

```yaml
apiKey: your-project-api-key
locales:
  - nl
snapshotFile: translationtools/snapshot.json
generated:
  packageName: com.example.translations
  objectName: Res
```

Optional field:

- none; `apiKey` is required in the checked-in config shape

Config rules:

- `generated.packageName` is required
- `generated.objectName` defaults to `Res`
- `locales` defaults to an empty list, which means pull only the remote default locale
- base URL is fixed to `https://translations.mvdm.io`
- no source-set or output-dir knobs in v1; generation always targets `commonMain`

Supported overrides:

1. `-Ptranslationtools.config=...` for config file path
2. `-Ptranslationtools.apiKey=...`
3. `TRANSLATIONTOOLS_API_KEY`
4. `apiKey` in YAML

If API key is still unresolved, fail clearly.

## Developer workflow

1. add `translationtools.yaml`
2. run `./gradlew.bat pullTranslations`
3. commit `translationtools/snapshot.json`
4. build and test offline

Expected repo state:

- `translationtools.yaml` checked in
- `translationtools/snapshot.json` checked in
- `build/generated/...` ignored

This gives reproducible builds without surprise network traffic during compile.

## Implementation slices

### Slice 1. Typed runtime layer

Files:

- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationStringResource.kt`
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationToolsClient.kt`
- `src/commonTest/kotlin/io/mvdm/translationtools/client/TranslationToolsClientTests.kt`

Done when:

- `client.getCached(TranslationStringResource("home.title", "Home"))` works without generator support
- existing string-key tests still pass

### Slice 2. Snapshot model and generator

Files live under the included build.

Deliver:

- snapshot schema v1 model
- pure naming and rendering functions
- generator that emits `<objectName>.kt`
- JVM tests for naming, collisions, fallback embedding, and stable output

Done when:

- the same snapshot always generates byte-for-byte identical output
- collision behavior is deterministic and covered by tests

### Slice 3. Gradle plugin wiring

Repo touch points:

- `settings.gradle.kts`
- included-build plugin files under `gradle/translationtools-plugin/`

Deliver:

- `translationTools` extension
- public `pullTranslations`
- internal `generateTranslationResources`
- generated source wiring into `commonMain`

Done when:

- `./gradlew.bat pullTranslations` refreshes snapshot and generated code
- `./gradlew.bat build` succeeds with no network after snapshot exists

### Slice 4. Compose module

Files:

- `settings.gradle.kts`
- `translationtools-client-compose/build.gradle.kts`
- `translationtools-client-compose/src/commonMain/kotlin/io/mvdm/translationtools/client/compose/TranslationToolsCompositionLocals.kt`
- `translationtools-client-compose/src/commonMain/kotlin/io/mvdm/translationtools/client/compose/TranslationStringResources.kt`

Deliver:

- separate `translationtools-client-compose` artifact
- `LocalTranslationToolsClient`
- `LocalTranslationToolsLocale`
- `stringResource(resource, locale)`

Done when:

- Compose code can read `TranslationStringResource` values from the shared runtime client
- explicit locale wins over `LocalTranslationToolsLocale`

### Slice 5. Docs and release pass

Files:

- `README.md`
- `build.gradle.kts`

Deliver:

- README usage for typed resources, sync workflow, and Compose module
- version bump to `0.3.0`

## Test plan

### Runtime tests

Add or update `src/commonTest` coverage for:

- `getCached(resource)` returns cached value, then fallback, then key
- `get(resource)` passes `resource.fallback` through existing fetch path
- `observe(resource)` emits fallback-aware values
- invalid keys still fail through existing validation

### Generator tests

Add JVM tests for:

- key sanitization
- keyword handling
- collision hashing
- stable sort order
- fallback embedding from default locale
- unchanged-output short circuit

### Gradle integration tests

Add plugin or TestKit coverage for:

- config loading
- API key precedence
- snapshot write
- generated source wiring
- offline build from existing snapshot

Use mocked HTTP or fixture snapshots. Do not depend on the live service in tests.

## Deferred follow-ups

These stay out of v1 on purpose:

- formatting args and placeholder strategy
- plural resource model
- editable local source format for push workflows
- published reusable Gradle plugin

None of these block typed resources plus `Res.string.*`.

## Final target state

After slices 1 through 5, a consumer project can:

1. run `./gradlew.bat pullTranslations`
2. commit `translationtools/snapshot.json`
3. build offline
4. read strings through `Res.string.*`
5. use `stringResource(...)` from `translationtools-client-compose`
6. keep using the existing runtime client under the hood

That first slice is now in place. Remaining work is follow-up scope, not a blocker for use.
