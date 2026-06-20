# Dependencies & CI/CD

Versions are managed centrally in `gradle/libs.versions.toml`. Toolchain: Kotlin 2.1.20, AGP 8.5.2, JVM target 17, `compileSdk` 36 / `minSdk` 24.

## Runtime client (`translationtools-client-kmp`)

| Package | Purpose |
|---------|---------|
| `kotlinx-coroutines-core` | Coroutines / `Flow` (exposed as `api`) |
| `kotlinx-datetime` | Timestamps (exposed as `api`) |
| `okio` | Filesystem / snapshot persistence |
| `ktor-client-core` | HTTP client for the remote API |
| `kotlinx-serialization-json` | JSON (de)serialization |

## Compose helpers (`translationtools-client-compose`)

| Package | Purpose |
|---------|---------|
| `project(":")` | The root runtime client (exposed as `api`) |
| `compose.runtime` | Composition locals + `stringResource` helpers |

Targets: Android, JVM, `iosX64`, `iosArm64`, `iosSimulatorArm64`.

## Gradle plugin (`gradle/translationtools-plugin`)

| Package | Purpose |
|---------|---------|
| `kotlin-gradle-plugin` | Wiring generation into Kotlin compilation |
| `snakeyaml-engine` | Parsing `translationtools.yaml` |
| `ktor-client-*` | Push/pull HTTP against TranslationTools |

## Tests

| Package | Purpose |
|---------|---------|
| `kotlin("test")` | Test framework |
| `kotlinx-coroutines-test` | `runTest` for suspend code |
| `ktor-client-mock` | `MockEngine` for HTTP tests |
| `okio-fakefilesystem` | In-memory filesystem for store tests |
| Gradle TestKit (`GradleRunner`) | Plugin functional tests |

## CI/CD

- **Pipeline:** `.github/workflows/publish.translationtools-client-maven-central.yml`
- **Triggers:** push to `master` (any path), or manual `workflow_dispatch`.
- **Steps:** set up JDK 17 + Android SDK (`platforms;android-36`), build & test (`compileDebugKotlinAndroid jvmTest`), then `publishAndReleaseToMavenCentral`.
- **Secrets:** `MAVEN_CENTRAL_USERNAME` / `_PASSWORD`, `MAVEN_SIGNING_KEY` / `_PASSPHRASE`.
- License: **Proprietary** (set in the `mavenPublishing` POM blocks).
