# Testing

Test stack: **`kotlin.test`** (`@Test`, `assertEquals`, `assertFailsWith`, …) + **`kotlinx-coroutines-test`** (`runTest`). JVM tests add **Ktor `MockEngine`** for HTTP and **Okio `FakeFileSystem`** for storage. The Gradle plugin is tested with **Gradle TestKit** (`GradleRunner`).

## Common tests

- Location: `src/commonTest/`
- Multiplatform behavior of the client. Use `runTest` for suspend code.
- Drive the client with fakes (e.g. `FakeTranslationToolsApi`) rather than real network.

## JVM tests

- Location: `src/jvmTest/`
- HTTP: build an `HttpClient(MockEngine { request -> respond(...) })` and assert on captured path/headers/body.
- Storage / filesystem: use Okio `FakeFileSystem` for `JvmTranslationSnapshotStores` and file-store tests.

## Compose tests

- Location: `translationtools-client-compose/src/commonTest/`
- Cover composition locals and `stringResource` helpers.

## Plugin tests

- Location: `gradle/translationtools-plugin/src/test/`
- Functional tests use `GradleRunner.create().withPluginClasspath().withArguments(...)` against a temp project dir; assert `TaskOutcome` and generated files.
- Reuse `FunctionalTestFixtures` (`writeBuildFiles`, `writeStandardTestFixtures`) rather than rolling your own project scaffolding.

## Running tests

- All targets: `./gradlew.bat allTests`
- JVM only (fastest iteration loop): `./gradlew.bat jvmTest`
- Compose module: `./gradlew.bat :translationtools-client-compose:allTests`
- Plugin (included build, own wrapper):
  `./gradle/translationtools-plugin/gradlew.bat test` (Windows) or `sh ./gradle/translationtools-plugin/gradlew test` (POSIX)
- Single test by name (JVM): `./gradlew.bat jvmTest --tests "*TranslationToolsClientTests*"`
- Never run two Gradle invocations in parallel — keep build/test steps sequential to avoid file locks and daemon contention.
