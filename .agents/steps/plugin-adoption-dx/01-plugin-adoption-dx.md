# 01 — Configuration-cache-safe push and pull

Status: done

## What to build

A Gradle user with configuration cache on can run `pushTranslations` and `pullTranslations` without `--no-configuration-cache`. A second run with the same inputs reuses the stored configuration — the cache works, it does not merely avoid crashing.

Push and pull keep writing and reading the same Android XML as today. When `appleResources` is set they still upload and write Apple `.strings` (origins, merge rules, and missing-`.lproj` create-plus-warn from ADR 0002 stay the same). TranslationTools HTTP contracts and origin format do not change.

Directories and the Gradle project path become normal task inputs, resolved before the task runs — the same style `generateTranslationResources` already uses for files and `projectPathInput`. Push and pull do not touch `Project` while they run. Values those tasks need (API key, locales, prune, key overrides, resource files, project path) are declared as ordinary inputs or file collections so an unchanged second run can reuse the stored configuration.

## Footprint

Projects: `gradle/translationtools-plugin` (included build)

- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/PushTranslationsTask.kt` — `PushTranslationsTask`, `resourceDirectories`, `appleResourceDirectories`, execution-time `project.file` / `project.path`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/PullTranslationsTask.kt` — `PullTranslationsTask`, same directory and project-path inputs, `writeAppleTranslations`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/TranslationToolsPlugin.kt` — push/pull registration, `resolvedConfig` providers
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/GenerateTranslationResourcesTask.kt` — `resourceFiles`, `projectPathInput` (pattern to copy)
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/TranslationToolsConfig.kt` — `resolveConfig`, `resolveConfigFile` (config must be readable without capturing `Project` at execution)
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/AndroidStringResourceParser.kt` — `parse(..., projectPath)`
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/AppleStringResourceParser.kt` — `parse(..., projectPath)`
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/PushTranslationsTaskTests.kt` — Android + Apple push
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/PullTranslationsTaskTests.kt` — Apple pull write-back
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/PushTranslationsPruneWiringTests.kt` — existing TestKit push wiring
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/FunctionalTestFixtures.kt` — shared TestKit project scaffolding
- `gradle/translationtools-plugin/src/test/kotlin/io/mvdm/translationtools/gradle/TaskPropertyAnnotationsTests.kt` — input annotations if properties change
- `README.md` — document that push and pull are configuration-cache safe

## Acceptance criteria

- [x] `pushTranslations` succeeds with `--configuration-cache`.
- [x] `pullTranslations` succeeds with `--configuration-cache`.
- [x] A second push or pull with unchanged inputs reports that configuration cache was reused.
- [x] Push and pull still write and read the same Android XML as today.
- [x] At least one configuration-cache push or pull with `appleResources` still uploads or writes Apple `.strings`.
- [x] Tests assert Gradle-visible outcomes (task success, cache reuse, file contents), not annotation names or internal property types.
- [x] Plugin tests run through the plugin wrapper and stay green.

## Outcome

Push and pull no longer touch `Project` at execution: Android/Apple resource directories are `@InputFiles` `ConfigurableFileCollection`s, and `projectPathInput` / `baseUrl` are ordinary `@Input` properties wired in `TranslationToolsPlugin` the same way generate already passes path and files. Existing Android XML and Apple `.strings` push/pull behavior is unchanged (origins, prune merge, Apple create-plus-warn).

TestKit coverage lives in `PushPullConfigurationCacheTests` with `MockTranslationToolsServer` and `-Ptranslationtools.baseUrl=…` so HTTP stays local. Second identical runs assert `Reusing configuration cache.`; Apple is covered by a CC push that uploads `InfoPlist.strings`. README notes that push/pull are configuration-cache safe.

Footprint drift: added `MockTranslationToolsServer.kt` and `PushPullConfigurationCacheTests.kt`; added `writeSyncOnlyBuildFiles` in `FunctionalTestFixtures.kt`; `baseUrl` task input + `-Ptranslationtools.baseUrl` test seam (not listed on the original footprint). `parseConfig` is now `internal`. Plugin tests green via `.\gradle\translationtools-plugin\gradlew.bat test`.
