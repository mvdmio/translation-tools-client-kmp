# Common Tasks

## Add a platform snapshot store

1. Implement `TranslationSnapshotStore` in the relevant source set (`androidMain`, `jvmMain`).
2. Expose a small factory object mirroring `AndroidTranslationSnapshotStores` / `JvmTranslationSnapshotStores`.
3. Add tests (JVM: use Okio `FakeFileSystem`).
4. Document it in `README.md` if it changes the public setup.

## Add a Gradle plugin task

1. Add the task class under `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/`.
2. Register and wire it in `TranslationToolsPlugin.kt`.
3. Add functional tests with Gradle TestKit; reuse `FunctionalTestFixtures`.
4. Document the task in `README.md` (the "Sync Your XML" task list).

## Change generated output (`Translations.*` / bundled snapshot)

1. Edit `TranslationResourceGenerator.kt` and/or `BundledSnapshotGenerator.kt`.
2. Update `TranslationResourceGeneratorTests` and the functional tests.
3. Generated names are `Translations` and `TranslationsBundledSnapshot` — keep them stable; renaming is a breaking (MAJOR) change.

## Add a KMP target

1. Declare the target in both `build.gradle.kts` and `translationtools-client-compose/build.gradle.kts`.
2. Provide any platform-specific `actual` implementations (e.g. a snapshot store).
3. Add/extend tests for the target where it has distinct behavior.

## Touch the remote API

1. Change `TranslationToolsApi` and its `TranslationToolsHttpApi` implementation together.
2. Cover it with `MockEngine`-based JVM tests asserting path, headers, and body.

After any change: `./gradlew.bat build` then `./gradlew.bat allTests` (sequentially), bump the version in `build.gradle.kts` if the published artifact is affected, and update `README.md`.
