# Dependencies & CI/CD

Versions are managed centrally in `gradle/libs.versions.toml`. Toolchain: Kotlin 2.3.21, AGP 8.5.2, Compose Multiplatform 1.11.1, JVM target 17, `compileSdk` 36 / `minSdk` 24.

## Runtime client (`translationtools-client-kmp`)

| Package | Purpose |
|---------|---------|
| `kotlinx-coroutines-core` | Coroutines / `Flow` (exposed as `api`) |
| `okio` | Filesystem / snapshot persistence |
| `ktor-client-core` (Ktor 3.5.2) | HTTP client for the remote API (consumer supplies the engine) |
| `kotlinx-serialization-json` | JSON (de)serialization |

Refresh timestamps use `kotlin.time.Instant` / `kotlin.time.Clock` from the stdlib (no `kotlinx-datetime` API dependency).

## Compose helpers (`translationtools-client-compose`)

| Package | Purpose |
|---------|---------|
| `project(":")` | The root runtime client (exposed as `api`) |
| `compose.runtime` | Composition locals + `stringResource` helpers |

Targets: Android, JVM, `iosArm64`, `iosSimulatorArm64` (Compose 1.11+ dropped `iosX64`). Root runtime client still declares `iosX64`.

## Gradle plugin (`gradle/translationtools-plugin`)

| Package | Purpose |
|---------|---------|
| `kotlin-gradle-plugin` | Wiring generation into Kotlin compilation |
| `snakeyaml-engine` | Parsing `translationtools.yaml` |
| `ktor-client-*` (Ktor 2.3.12 via `ktor-plugin` aliases) | Push/pull HTTP against TranslationTools (build-time only) |

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
- **Runner:** `ubuntu-22.04` (klib cross-compilation is default since Kotlin 2.2.20; no `enableKlibCrossCompilation` flag).
- **Steps:** set up JDK 17 + Android SDK (`platforms;android-36`); `compileDebugKotlinAndroid jvmTest` plus iOS klib compiles (`compileKotlinIosArm64` / `IosX64` / `IosSimulatorArm64` on the client, `iosArm64` + `iosSimulatorArm64` on Compose) with `-Pkotlin.native.ignoreDisabledTargets=false`; `verifyIosMavenVariants` (local Maven check under `build/ios-maven-check`); then `publishAndReleaseToMavenCentral` (also depends on `verifyIosMavenVariants`).
- **Fallback:** if Linux cannot produce an iOS klib (C interop), switch only this publish job to macOS — do not ship root modules that point at missing variants.
- **Secrets:** `MAVEN_CENTRAL_USERNAME` / `_PASSWORD`, `MAVEN_SIGNING_KEY` / `_PASSPHRASE`.
- License: **Proprietary** (set in the `mavenPublishing` POM blocks).
