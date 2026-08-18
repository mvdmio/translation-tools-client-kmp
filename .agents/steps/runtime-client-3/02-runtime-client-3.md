# 02 — Compile the runtime client with Kotlin 2.3.21

Status: done

## What to build

A KMP app already on Kotlin 2.3.21 can depend on this library without mixing a 2.1 klib with a 2.3 compiler. The runtime client, the Compose helpers, and the included Gradle plugin all compile with Kotlin 2.3.21. Compose `stringResource` and the composition locals still work on Android, JVM, and iOS source sets. The plugin still generates `Translations.*` / `TranslationsBundledSnapshot` and still pushes and pulls.

Keep Android Gradle Plugin 8.5.2 and Gradle 8.14.3. Do not go to Kotlin 2.4. Bump the JetBrains Compose Multiplatform plugin to a release that supports Kotlin 2.3.21 (1.7.3 will not build; pick the current compatible Compose Multiplatform release at implement time). Bump declared kotlinx-coroutines and kotlinx-serialization to releases that work with Kotlin 2.3.21 (do not leave the catalog on 1.9 / 1.7). This step must leave the tree compiling: Instant stays `kotlinx.datetime`, and both Ktor lines stay on 2.x.

This is the first published-artifact change of the 3.0.0 release. Set the library version to 3.0.0 here and do not bump again in later steps.

## Footprint

Projects: `translationtools-client-kmp`, `:translationtools-client-compose`, `gradle/translationtools-plugin`

- `gradle/libs.versions.toml` — `kotlin`, `compose`, `kotlinx-coroutines`, `kotlinx-serialization`
- `build.gradle.kts` — `version`, Kotlin Multiplatform plugin, iOS targets already declared
- `translationtools-client-compose/build.gradle.kts` — Compose Multiplatform plugin + Kotlin compose plugin
- `gradle/translationtools-plugin/build.gradle.kts` — `kotlin("jvm")` / serialization plugin from the catalog Kotlin version
- `README.md` — install version snippets and the plugin "compiled with Kotlin …" compatibility note
- `AGENTS.md`, `CLAUDE.md`, `.agents/ref/dependencies.md` — documented toolchain versions
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/TranslationToolsPluginFunctionalTests.kt` — `generateTranslationResources_should_work_with_kotlin_2`
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/FunctionalTestFixtures.kt` — consumer Kotlin default used by TestKit

## Acceptance criteria

- [x] Catalog Kotlin is 2.3.21; Compose Multiplatform plugin is a release that builds against it
- [x] Catalog kotlinx-coroutines and kotlinx-serialization are releases that work with Kotlin 2.3.21, not 1.9 / 1.7
- [x] Published version is 3.0.0; README version snippets match
- [x] Runtime client, Compose helpers, and the included plugin compile with Kotlin 2.3.21
- [x] Existing client, Compose, and plugin tests still pass (Ktor 2, `kotlinx.datetime.Instant` unchanged)
- [x] AGP stays 8.5.2 and Gradle stays 8.14.3
- [x] `./gradlew.bat build` then `./gradlew.bat allTests`, then `./gradle/translationtools-plugin/gradlew.bat test`, all sequential

## Outcome

Catalog: Kotlin 2.3.21, Compose Multiplatform 1.11.1, kotlinx-coroutines 1.11.0, kotlinx-serialization 1.11.0. Published version set to 3.0.0 (README install snippets + plugin compatibility note updated). AGP 8.5.2 and Gradle 8.14.3 unchanged. Ktor lines still 2.3.12; Instant still `kotlinx.datetime`. Plugin functional test `generateTranslationResources_should_work_with_kotlin_2` now uses consumer Kotlin 2.3.21. Docs: `CLAUDE.md`, `.agents/ref/dependencies.md` toolchain versions updated. AGENTS.md had no hardcoded Kotlin version to change.

Footprint drift: Compose Multiplatform 1.11 removed Apple x86_64, so `:translationtools-client-compose` dropped `iosX64()` (kept `iosArm64` / `iosSimulatorArm64`). Root runtime client still declares `iosX64`. Later iOS-publish steps should expect Compose without an `iosX64` variant module.

Verified: `./gradlew.bat build`, `./gradlew.bat allTests`, `./gradle/translationtools-plugin/gradlew.bat test`.
