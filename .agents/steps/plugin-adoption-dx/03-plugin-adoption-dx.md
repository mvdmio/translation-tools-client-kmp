# 03 — compileOnly Kotlin Gradle plugin and documented adoption path

Status: done

## What to build

A developer who vendors the plugin with `includeBuild` no longer gets the plugin's Kotlin Gradle plugin on their build classpath. The plugin still compiles against that API; consumer projects that apply Kotlin Multiplatform continue to supply it themselves. Functional tests keep applying the Kotlin Multiplatform plugin themselves so TestKit still has it.

README documents the supported setup jewel-app can copy: configuration-cache-safe push/pull, `generated.enabled: false` for sync-only on a KMP module, and that `includeBuild` no longer brings the Kotlin Gradle plugin onto the consumer classpath. It stops implying the plugin is only usable by vendoring plus `--no-configuration-cache` plus a non-Kotlin module. Composite-build install stays — publishing to the Plugin Portal is out of scope.

Do not bump the published runtime version: this ships in the same 3.0.0 release. Do not add a test that inspects the plugin's own dependency configuration unless there is no other way to lock the behavior.

## Footprint

Projects: `gradle/translationtools-plugin` (included build), root (README)

- `gradle/translationtools-plugin/build.gradle.kts` — `implementation(libs.kotlin.gradle.plugin)` → `compileOnly`
- `README.md` — composite-build setup, config fields, Sync Your XML, Quick Start / production checklist language that currently assumes generate-on-apply
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/TranslationToolsPluginFunctionalTests.kt` — TestKit still applies Kotlin Multiplatform itself
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/FunctionalTestFixtures.kt` — `writeBuildFiles` applies `org.jetbrains.kotlin.multiplatform`
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/PushTranslationsPruneWiringTests.kt` — non-KMP TestKit apply still works

## Acceptance criteria

- [x] Plugin Kotlin Gradle plugin dependency is `compileOnly`; existing plugin unit and functional tests still pass.
- [x] Functional tests still apply the Kotlin Multiplatform plugin themselves.
- [x] README documents configuration-cache-safe push/pull.
- [x] README documents `generated.enabled: false` for sync-only on a KMP module.
- [x] README documents that `includeBuild` no longer puts the Kotlin Gradle plugin on the consumer classpath.
- [x] README no longer implies the plugin is only usable via vendoring plus those workarounds.
- [x] Composite-build install remains the documented install path (publish stays out of scope).
- [x] Published runtime version stays 3.0.0.
- [x] Plugin tests run through the plugin wrapper and stay green.

## Outcome

Plugin Kotlin Gradle plugin dependency is `compileOnly`. KMP codegen wiring moved to `KotlinMultiplatformCodegenWiring` so the plugin class loads without KGP on the classpath (non-KMP / sync-only). TestKit still applies KMP itself; `pluginUnderTestMetadata` gets a resolvable KGP classpath so codegen-on functional tests resolve `KotlinMultiplatformExtension` under `withPluginClasspath` isolation.

README documents configuration-cache-safe push/pull, `generated.enabled: false` sync-only on a KMP module, and that `includeBuild` does not put KGP on the consumer classpath; composite-build install stays; production checklist no longer implies vendor + `--no-configuration-cache` + non-Kotlin module. Runtime version remains 3.0.0.

Footprint drift: added `KotlinMultiplatformCodegenWiring.kt` and a TestKit-only `kotlinGradlePluginTestClasspath` / `pluginUnderTestMetadata` wiring in the plugin `build.gradle.kts`. Plugin tests, root `build`, and `allTests` green.
