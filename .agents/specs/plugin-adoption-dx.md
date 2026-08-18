# Plugin adoption without codegen or configuration-cache workarounds

Status: ready-for-agent

## Problem Statement

A consumer that only wants Gradle push/pull against TranslationTools still has to work around the plugin.

Push and pull resolve files and the project path through `Project` while the task runs. That fails when configuration cache is on. jewel-app has configuration cache on, so they run those tasks with a flag that turns it off.

When the plugin is applied to a Kotlin Multiplatform module, it always adds generated `Translations.*` sources and makes every Kotlin compile wait on that generate task. The generated files import runtime types, so the module then needs the runtime client — including iOS klibs and a Ktor 3-compatible build. A team that only wants sync, and that still uses Compose `Res.string.*`, has to park the plugin on a separate non-Kotlin module so the generated code is never compiled.

Vendoring the plugin with `includeBuild` also puts the plugin's Kotlin Gradle plugin dependency on the consumer's build classpath. That dependency is an implementation dependency today, so two Kotlin Gradle plugin versions can sit side by side.

## Solution

Make the existing plugin safe to apply on the module that owns the Android XML (and optional Apple `.strings`) without those workarounds.

Push and pull take their directories and project path as normal task inputs, resolved before the task runs. They do not touch `Project` at execution time. Consumers can run them with configuration cache on.

A YAML flag `generated.enabled` turns off the KMP codegen wiring. Default stays on. When it is false, push, pull, and init still exist; Kotlin compile does not depend on generate; the generated source directory is not added. The generate task remains if someone runs it by hand.

The plugin depends on the Kotlin Gradle plugin only at compile time (`compileOnly`). An `includeBuild` consumer no longer inherits that dependency on the build classpath.

This spec does not publish the plugin to the Plugin Portal. That still needs an account and secrets.

## User Stories

1. As a Gradle user with configuration cache on, I want `pushTranslations` to succeed, so that I do not pass `--no-configuration-cache`.
2. As a Gradle user with configuration cache on, I want `pullTranslations` to succeed, so that I do not pass `--no-configuration-cache`.
3. As a Gradle user, I want a second push or pull with configuration cache on to reuse the stored configuration when inputs did not change, so that the cache is actually working, not merely not crashing.
4. As a Gradle user, I want push and pull to keep writing and reading the same Android XML as today, so that a cache-safe task does not change TranslationTools content.
5. As a Gradle user, I want push and pull to keep handling Apple `.strings` when `appleResources` is set, so that the cache-safe path does not drop iOS sync.
6. As a KMP developer who only wants sync, I want `generated.enabled: false` in `translationtools.yaml`, so that I can apply the plugin on my shared module without compiling `Translations.*`.
7. As a KMP developer with `generated.enabled: false`, I want Kotlin compile to run without waiting on `generateTranslationResources`, so that a missing or unused generate output cannot fail my build.
8. As a KMP developer with `generated.enabled: false`, I want the generated source directory left off `commonMain`, so that `StoredTranslations` is not pulled onto my compile classpath.
9. As a KMP developer with `generated.enabled: false`, I want `pushTranslations` and `pullTranslations` to still be registered, so that I can sync without codegen.
10. As a KMP developer with `generated.enabled: false`, I want `pullTranslations` not to run generate afterwards, so that a pull does not write Kotlin I did not ask for.
11. As a KMP developer with `generated.enabled` omitted, I want today's generate-on-compile behavior, so that existing apps do not change.
12. As a KMP developer with `generated.enabled: true`, I want the same generate wiring as today, so that the flag is an explicit opt-in to the current default.
13. As a KMP developer, I want `generateTranslationResources` still registered when codegen is disabled, so that I can run it by hand if I later want the typed API.
14. As a developer who applies the plugin on a module that is not Kotlin Multiplatform, I want push and pull to keep working with no extra flag, so that the existing non-KMP workaround remains valid.
15. As a developer with a bad `generated.enabled` value, I want a clear configuration error, so that I know the key must be a boolean.
16. As a developer reviewing `translationtools.yaml`, I want `generated.packageName` to keep working next to `generated.enabled`, so that I do not lose the package setting.
17. As a developer who vendors the plugin with `includeBuild`, I want the plugin not to put the Kotlin Gradle plugin on my build classpath, so that I do not mix two Kotlin Gradle plugin versions.
18. As a plugin test author, I want functional tests to keep applying the Kotlin Multiplatform plugin themselves, so that `compileOnly` does not break TestKit.
19. As a maintainer, I want README to document `generated.enabled: false` and configuration-cache-safe push/pull, so that jewel-app can copy the supported setup instead of the workarounds.
20. As a maintainer, I want README to stop implying the plugin is only usable by vendoring plus those workarounds, so that the documented path matches the plugin.
21. As a KMP developer who still wants runtime refresh, I want the default generate path unchanged, so that this spec does not take codegen away from the 3.0 client.
22. As a developer running `initTranslationTools`, I want the starter YAML to mention `generated.enabled` only if we document it as optional, so that new projects keep generating by default.

## Implementation Decisions

- Resolve Android and Apple resource directories to file collections at configuration time. Push and pull read those collections. They do not call `project.file` while the task runs.
- Pass the Gradle project path into push and pull as an input string, the same way generate already receives it. Parsers that need a path for origins use that input. They do not read `project.path` while the task runs.
- Do not capture `Project` inside providers that run at execution time. Read `translationtools.yaml` in a way configuration cache allows (layout, file providers, mapped values). The generate task's existing input style is the pattern to copy. Declare every value those tasks need as a normal input or file collection so a second run with unchanged inputs can reuse the stored configuration, not only avoid a crash.
- `generated.enabled` is an optional boolean on the existing `generated` YAML map. Missing key or missing `generated` block means true. `true` keeps today's KMP wiring. `false` skips adding the generated source directory, skips making Kotlin compile and sources-jar tasks depend on generate, and skips making pull finalize by generate.
- Push, pull, init, and generate stay registered when `generated.enabled` is false. Generate still works if someone invokes it. It is just not wired into compilation or pull.
- A non-boolean `generated.enabled` is a configuration error with a message that names the key and the expected type. Same style as the existing YAML shape errors.
- Default starter YAML from init does not set `generated.enabled`. New projects keep generating.
- The plugin's Kotlin Gradle plugin dependency becomes `compileOnly`. Test dependencies still have whatever they need to compile and run TestKit. Consumer projects that apply Kotlin Multiplatform continue to supply that plugin themselves.
- Do not introduce a second plugin id for sync-only.
- Do not change TranslationTools HTTP contracts, origin format, or Apple `.strings` merge rules.
- README documents configuration-cache-safe tasks, `generated.enabled: false` for sync-only on a KMP module, and that `includeBuild` no longer brings the Kotlin Gradle plugin onto the consumer classpath. Composite-build install stays until the plugin is published (out of scope here).
- Version: if this ships in the same release as runtime client 3.0.0, do not bump again. If it ships later, increment MINOR.

## Testing Decisions

Good tests assert what a developer sees from Gradle: task outcome, whether generate ran, whether `Translations.*` appeared, whether compile needed the runtime types, and whether configuration cache accepted the run. Do not assert on annotation names or internal property types.

- Config parsing: existing config tests gain cases for `generated.enabled` true, false, omitted (treat as true), and a non-boolean value that errors. Prior art: the existing YAML parse tests.
- Configuration cache: existing push and pull TestKit tests (or a focused pair next to them) run with `--configuration-cache` and must succeed. A second invocation with the same inputs should report that configuration cache was reused. Prior art: push/pull task tests with `MockEngine` and temp projects; generate already avoids `Project` at execution.
- Apple paths stay covered: at least one configuration-cache push or pull with `appleResources` still uploads or writes `.strings`. Prior art: existing Apple push/pull tests.
- Sync-only flag, highest seam: a TestKit KMP project with `generated.enabled: false` can run `pushTranslations` / `pullTranslations` (HTTP mocked) and can compile Kotlin without a generated `Translations` file and without a runtime-client dependency. A project with the flag omitted still generates and still makes compile depend on generate. Prior art: plugin functional tests and task registration tests.
- Pull with the flag off must not run generate as a finalized task. Assert generate is not in the task graph (or does not run) for that pull.
- `compileOnly`: existing plugin unit and functional tests must still pass. Do not add a test that inspects the plugin's own dependency configuration unless there is no other way to lock the behavior.
- Run plugin tests through the plugin wrapper, not the root wrapper, and not in parallel with a root Gradle invocation.

## Out of Scope

- Publishing the plugin to the Gradle Plugin Portal or Maven Central. That still needs an account, secrets, and a publish workflow.
- Runtime client Ktor 3, Instant, or iOS klib work (separate spec).
- Android `%1$s` substitution and `<plurals>`.
- A second plugin id, a `syncOnly` top-level key, or disabling generate by detecting a missing runtime dependency.
- Changing default generate-on behavior for existing KMP consumers.
- Editing `project.pbxproj` or other Apple project files.
- Moving the plugin to Ktor 3.

## Further Notes

- jewel-app's workarounds (vendor + `--no-configuration-cache` + a non-Kotlin module) become unnecessary once this ships. They can apply the plugin on the shared module with `generated.enabled: false` until they are ready for the 3.0 runtime client.
- The generate task already passes project path and files as inputs. Push and pull should look like that, not like a new task API.
- ADR 0001 is about Apple `.strings` being sync-only. This flag is different: it turns off Android/KMP codegen. Do not fold them into one concept.
- Issue 03 (Plugin Portal publish) stays open. Only the `compileOnly` part of that issue is in this spec.
