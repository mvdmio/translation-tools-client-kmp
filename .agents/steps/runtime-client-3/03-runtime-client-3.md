# 03 — Serve runtime refresh timestamps as kotlin.time Instant

Status: done

## What to build

After a runtime refresh, the public snapshot and refresh-status types use `kotlin.time.Instant` (and the client clock uses `kotlin.time.Clock`). A KMP app on kotlinx-datetime 0.8 no longer has this library requiring `kotlinx.datetime.Instant` on the public API.

An existing on-disk snapshot written by 2.x still loads: the timestamp comes back as the same instant. A snapshot written by 3.0 still stores `lastSuccessfulRefreshAt` as an ISO-8601 string under the same field name. `initialize`, `get`, `getCached`, and `observe` keep the same behaviour besides the Instant type.

If `kotlin.time.Instant` needs an explicit serializer to keep that string shape, add one. Do not take kotlinx-datetime 0.8 just because the compiler allows it. Stop declaring kotlinx-datetime as an API dependency. Tests follow the same types, including the mutable test clock.

## Footprint

Projects: `translationtools-client-kmp`, `:translationtools-client-compose`, `gradle/translationtools-plugin`

- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationModels.kt` — `StoredTranslations.lastSuccessfulRefreshAt`, `TranslationRefreshState.lastSuccessfulRefreshAt`
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationToolsClient.kt` — `now`, `Clock`, `lastSuccessfulRefreshAt`, `isRefreshStale`, `replaceState`, persist/restore
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationSnapshotStore.kt` — `FileTranslationSnapshotStore`, `defaultSnapshotStoreJson`
- `src/commonMain/kotlin/io/mvdm/translationtools/client/` — Instant ISO-8601 serializer if the stdlib type does not keep the 2.x string shape
- `build.gradle.kts` — `api(libs.kotlinx.datetime)`
- `gradle/libs.versions.toml` — `kotlinx-datetime` version and library entry once unused
- `src/commonTest/kotlin/io/mvdm/translationtools/client/TranslationToolsClientTests.kt` — `MutableClock`, `now`, stored-snapshot Instant fixtures
- `src/jvmTest/kotlin/io/mvdm/translationtools/client/FileTranslationSnapshotStoreTests.kt` — round-trip plus a 2.x ISO-8601 fixture load
- `translationtools-client-compose/src/commonTest/kotlin/io/mvdm/translationtools/client/compose/TranslationStringResourcesTests.kt` — test `now` Instant
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/BundledSnapshotGenerator.kt` — generated `lastSuccessfulRefreshAt = null`

## Acceptance criteria

- [x] `StoredTranslations.lastSuccessfulRefreshAt` and `TranslationRefreshState.lastSuccessfulRefreshAt` are `kotlin.time.Instant?`
- [x] The published client no longer exposes kotlinx-datetime as an API dependency
- [x] A fixture JSON whose `lastSuccessfulRefreshAt` is the ISO-8601 string a 2.x client would have written loads and returns that timestamp
- [x] Saving a 3.0 snapshot still writes `lastSuccessfulRefreshAt` as an ISO-8601 string under the same field name
- [x] Existing refresh, cache, and observe cases still pass with the mutable test clock
- [x] Compose helper tests still pass
- [x] `initialize` / `get` / `getCached` / `observe` behaviour is unchanged besides the Instant type
- [x] `./gradlew.bat build` then `./gradlew.bat allTests`, then `./gradle/translationtools-plugin/gradlew.bat test`, all sequential

## Outcome

Public refresh timestamps now use `kotlin.time.Instant?`; client clock uses `kotlin.time.Clock`. Removed `api(libs.kotlinx.datetime)` and the catalog entry. Snapshot JSON keeps ISO-8601 `lastSuccessfulRefreshAt` via kotlinx-serialization's built-in `InstantSerializer` (custom serializer collided on serial name `kotlin.time.Instant`). Added 2.x fixture load + save-shape assertions. Tests/Compose helpers switched to `kotlin.time.Instant`. BundledSnapshotGenerator unchanged (`null`). Docs: README refresh note, `.agents/ref/dependencies.md`.

Footprint drift: no custom Instant serializer file needed. `RepositoryLayoutTests.compose_module_should_declare_ios_targets` now asserts Compose has no `iosX64()` (step 02 Compose 1.11 drop). Plugin code untouched beyond verification.

Verified: `./gradlew.bat build`, `./gradlew.bat allTests`, `./gradle/translationtools-plugin/gradlew.bat test`.
