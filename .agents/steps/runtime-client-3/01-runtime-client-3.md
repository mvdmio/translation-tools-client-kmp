# 01 — Split runtime and plugin Ktor versions

Status: done

## What to build

A maintainer can change the runtime client's Ktor line without dragging the Gradle plugin's build-time HTTP client with it. Push and pull still talk to TranslationTools the same way. Consumers see no change: runtime refresh, bundled fallback, and plugin codegen behave as they do today.

The catalog currently has one `ktor` version used by both the published client and the included plugin. This step only separates those lines so later steps can put the client on Ktor 3 while the plugin stays on Ktor 2.

## Footprint

Projects: `translationtools-client-kmp`, `:translationtools-client-compose`, `gradle/translationtools-plugin`

- `gradle/libs.versions.toml` — `ktor` version and the `ktor-client-*` / `ktor-serialization-*` library aliases
- `build.gradle.kts` — runtime `ktor-client-core` / `ktor-client-mock` resolution
- `gradle/translationtools-plugin/build.gradle.kts` — plugin `ktor-client-cio`, content negotiation, mock, kotlinx-json
- `gradle/translationtools-plugin/settings.gradle.kts` — shared catalog `libs` from `../libs.versions.toml`

## Acceptance criteria

- [x] The version catalog declares a runtime Ktor line and a separate plugin Ktor line; both still resolve Ktor 2.3.12 in this step
- [x] The included plugin still compiles and its TestKit / MockEngine tests pass
- [x] Runtime refresh HTTP tests still pass against the same client API
- [x] `./gradlew.bat build` then `./gradlew.bat allTests`, then `./gradle/translationtools-plugin/gradlew.bat test`, all sequential
- [x] No published version bump (catalog-only prefactor; no consumer-visible behaviour)

## Outcome

Catalog now has `ktor` (runtime) and `ktor-plugin` (plugin), both pinned at 2.3.12. Runtime keeps `ktor-client-core` / `ktor-client-mock`; plugin uses `ktor-plugin-client-cio`, `ktor-plugin-client-content-negotiation`, `ktor-plugin-client-mock`, `ktor-plugin-serialization-kotlinx-json`. Plugin `build.gradle.kts` updated to those aliases.

Footprint drift: root `build.gradle.kts` needed no change (runtime aliases already pointed at `ktor`). `gradle/translationtools-plugin/settings.gradle.kts` unchanged (still loads shared `../libs.versions.toml`). No published version bump. Verified: `./gradlew.bat build`, `./gradlew.bat allTests`, `./gradle/translationtools-plugin/gradlew.bat test`.
