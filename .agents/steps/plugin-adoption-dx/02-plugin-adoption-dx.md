# 02 — Sync-only via generated.enabled

Status: done

## What to build

A KMP developer who only wants TranslationTools sync can set `generated.enabled: false` in `translationtools.yaml` and apply the plugin on the module that owns the Android XML (and optional Apple `.strings`). They do not need a separate non-Kotlin module to dodge codegen.

`generated.enabled` is an optional boolean on the existing `generated` YAML map:

- missing key or missing `generated` block → true
- `true` → today's KMP wiring (generate on compile, generated sources on `commonMain`, pull finalized by generate)
- `false` → push, pull, init, and generate stay registered; Kotlin compile and sources-jar do not depend on generate; the generated source directory is not added to `commonMain`; pull does not run generate afterwards
- non-boolean → configuration error that names `generated.enabled` and the expected boolean type, in the same style as existing YAML shape errors

Generate still works if someone runs it by hand. `generated.packageName` keeps working next to `generated.enabled`. Default starter YAML from `initTranslationTools` does not set `generated.enabled` — new projects keep generating. A non-Kotlin-Multiplatform module still gets push and pull with no extra flag.

This flag turns off Android/KMP `Translations.*` codegen. It is not Apple `.strings` sync-only (ADR 0001). Do not introduce a second plugin id or a `syncOnly` top-level key.

## Footprint

Projects: `gradle/translationtools-plugin` (included build)

- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/TranslationToolsConfig.kt` — `GeneratedConfig`, `parseConfig`, `renderDefaultConfig`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/TranslationToolsPlugin.kt` — KMP `srcDir` / compile / sources-jar wiring, pull `finalizedBy(generateTask)`, task registration
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/InitTranslationToolsTask.kt` — starter YAML via `renderDefaultConfig`
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/TranslationToolsConfigTests.kt` — YAML parse cases
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/TranslationToolsPluginFunctionalTests.kt` — generate-on-compile / sources-jar / init
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/TranslationToolsTaskRegistrationTests.kt` — task list
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/FunctionalTestFixtures.kt` — TestKit KMP project scaffolding
- `README.md` — document optional `generated.enabled: false` for sync-only on a KMP module

## Acceptance criteria

- [x] Config parse: `generated.enabled` true, false, omitted (treated as true), and a non-boolean value that errors naming the key and expected type.
- [x] A TestKit KMP project with `generated.enabled: false` can run `pushTranslations` / `pullTranslations` (HTTP mocked) and compile Kotlin without a generated `Translations` file and without a runtime-client dependency.
- [x] With the flag omitted, generate still runs and compile still depends on generate.
- [x] With `generated.enabled: true`, wiring matches today's generate-on-compile path.
- [x] Pull with the flag off does not run generate (generate is not in the task graph, or does not run).
- [x] `generateTranslationResources` remains registered and still works when invoked by hand.
- [x] `generated.packageName` still works next to `generated.enabled`.
- [x] Init starter YAML does not set `generated.enabled`.
- [x] A non-KMP module still gets push and pull with no extra flag.
- [x] Plugin tests run through the plugin wrapper and stay green.

## Outcome

`GeneratedConfig.enabled` (default true) is parsed from `generated.enabled`; non-booleans fail naming the key and expected type. When false, the plugin still registers push/pull/init/generate but skips KMP `commonMain` srcDir / compile / sources-jar `dependsOn(generate)` and skips pull `finalizedBy(generate)`. Hand-run generate and `generated.packageName` still work; init starter YAML omits `enabled`.

TestKit coverage is in `GeneratedEnabledFunctionalTests` (reuses `MockTranslationToolsServer` / `-Ptranslationtools.baseUrl`). README documents optional `generated.enabled: false` for sync-only on a KMP module.

Footprint drift: added `GeneratedEnabledFunctionalTests.kt` and `isGeneratedCodegenEnabled` in `TranslationToolsPlugin.kt`. Plugin tests green via `.\gradle\translationtools-plugin\gradlew.bat test`.
