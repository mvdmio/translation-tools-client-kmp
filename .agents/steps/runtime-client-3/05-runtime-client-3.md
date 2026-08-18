# 05 — Publish real iOS klibs from Ubuntu

Status: done

## What to build

A KMP app that already targets iOS can add the runtime client and the Compose helpers to `commonMain` and resolve `iosArm64`, `iosX64`, and `iosSimulatorArm64`. The published root modules include working iOS variant modules, not only metadata pointers.

The publish job stays on Ubuntu. After Kotlin 2.2.20, klib cross-compilation is on by default; do not add the old enable flag unless a 2.3.21 build still requires it. CI must compile the iOS klib targets (device and both simulators) for both published modules in the job that currently only compiles Android and runs JVM tests, then publish. A missing iOS output fails the job.

Prove the variants at the highest seam this repo can run: publish both modules to a local Maven repository and assert the iOS variant modules are on disk. If the environment cannot compile those targets, fail clearly rather than skip and publish anyway. Do not switch the publish job to macOS unless Linux actually cannot produce a klib (the iOS `actual` uses Foundation `NSUUID`; that is the C-interop risk). Document the macOS-runner fallback only — do not add the job in this step unless Linux fails.

## Footprint

Projects: `translationtools-client-kmp`, `:translationtools-client-compose`, `gradle/translationtools-plugin`

- `.github/workflows/publish.translationtools-client-maven-central.yml` — Ubuntu job; iOS klib compile before `publishAndReleaseToMavenCentral`
- `gradle.properties` — `kotlin.native.ignoreDisabledTargets` must not hide a missing iOS output on the publish job
- `build.gradle.kts` — `iosX64()`, `iosArm64()`, `iosSimulatorArm64()`, `mavenPublishing`
- `translationtools-client-compose/build.gradle.kts` — same iOS targets and `mavenPublishing`
- `src/iosMain/kotlin/io/mvdm/translationtools/client/Platform.ios.kt` — `currentPlatform`, `newClientId` (Foundation interop)
- `src/jvmTest/kotlin/io/mvdm/translationtools/client/RepositoryLayoutTests.kt` — Compose iOS target declarations
- `src/jvmTest/kotlin/io/mvdm/translationtools/client/` — local Maven publish assertion that the client and Compose iOS variant modules exist on disk
- `.agents/ref/dependencies.md` — CI steps (iOS compile + publish)
- `README.md` — iOS variants exist for both artifacts; documented macOS publish fallback if Linux klib fails because of C interop

## Acceptance criteria

- [x] CI on `ubuntu-22.04` compiles iOS klibs for `iosArm64`, `iosX64`, and `iosSimulatorArm64` on both published modules before publish
- [x] A missing iOS output fails that job
- [x] A local Maven publish of the client and Compose modules writes the iOS variant modules to disk; the assertion fails clearly if they are absent
- [x] The publish job stays on Ubuntu unless this step proves Linux cannot produce a klib
- [x] The old klib-from-Linux enable flag is added only if 2.3.21 still requires it
- [x] `./gradlew.bat build` then `./gradlew.bat allTests`, then `./gradle/translationtools-plugin/gradlew.bat test`, all sequential

## Outcome

Ubuntu publish job now compiles iOS klibs before release: client `iosArm64` / `iosX64` / `iosSimulatorArm64`, Compose `iosArm64` / `iosSimulatorArm64` (no Compose `iosX64` — step 02). CI forces `-Pkotlin.native.ignoreDisabledTargets=false` so skipped Apple targets cannot hide a missing klib. Added `IosCheck` local Maven repo + root `verifyIosMavenVariants` (asserts `.klib` modules on disk); `publishAndReleaseToMavenCentral` depends on it. `IosMavenVariantPublishTests` covers workflow/build wiring always; optional disk assert via `-DiosMavenCheckRepo` (CI). No `kotlin.native.enableKlibCrossCompilation` flag — default since 2.2.20; Foundation `NSUUID` klib cross-compiles on this host. macOS publish fallback documented only (README + `.agents/ref/dependencies.md`).

Footprint drift: Compose still has no `iosX64` variant (carry-forward from 02). Windows `build`/`allTests` keep `ignoreDisabledTargets=true` and do not require iOS compile.

Verified: `./gradlew.bat build`, `./gradlew.bat allTests`, `./gradlew.bat verifyIosMavenVariants -Pkotlin.native.ignoreDisabledTargets=false`, `./gradle/translationtools-plugin/gradlew.bat test`.
