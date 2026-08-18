# Runtime client 3.0 for current KMP apps

Status: ready-for-agent

## Problem Statement

A KMP app that already targets iOS and already uses Ktor 3 cannot adopt this library's runtime client. The published 2.3.0 module lists iOS variants that are not on Maven Central, so resolution fails as soon as the dependency sits on `commonMain`. The same module is compiled against Ktor 2 and `kotlinx-datetime` 0.6. The consumer's newer versions win, then the library bytecode calls symbols that no longer exist.

jewel-app is the consumer that surfaced this. It has iOS targets, Ktor 3.5.1, kotlinx-datetime 0.8.0, and Kotlin 2.3.21. Sync-only through the Gradle plugin can be worked around. Runtime refresh cannot.

## Solution

Ship **3.0.0** of the runtime client and the Compose helpers as one breaking release that jewel-app can put on `commonMain`.

Compile that line with Kotlin 2.3.21. Keep the existing Ubuntu publish job. Kotlin 2.2.20 and later can produce iOS klibs from Linux, so the advertised iOS variants must actually be published. Depend on Ktor 3.5.2. Move public `Instant` and `Clock` types to `kotlin.time`. Stop exposing `kotlinx-datetime` on the public API.

Do not keep a parallel 2.x line. Apps that still need Ktor 2 stay on 2.3.0.

## User Stories

1. As a KMP app developer, I want the Maven Central iOS variants to exist, so that adding this client to `commonMain` resolves for `iosArm64`, `iosX64`, and `iosSimulatorArm64`.
2. As a KMP app developer, I want the Compose helpers' iOS variants to exist too, so that I can use composition locals from shared iOS code.
3. As a KMP app developer on Ktor 3, I want this client compiled against Ktor 3, so that Gradle does not mix Ktor 2 bytecode with my Ktor 3 runtime.
4. As a KMP app developer on kotlinx-datetime 0.8, I want this client to stop requiring `kotlinx.datetime.Instant`, so that my datetime version does not break the client.
5. As a KMP app developer on Kotlin 2.3.21, I want this library compiled with Kotlin 2.3.21, so that I am not mixing a 2.1 library klib with my 2.3 compiler.
6. As a KMP app developer, I want `StoredTranslations.lastSuccessfulRefreshAt` to be a `kotlin.time.Instant`, so that the public snapshot type matches the standard library.
7. As a KMP app developer, I want `TranslationRefreshState.lastSuccessfulRefreshAt` to be a `kotlin.time.Instant`, so that refresh status uses the same clock type as the rest of my app.
8. As a KMP app developer, I want an existing on-disk snapshot from 2.x to still load, so that an upgrade to 3.0 does not throw away the last successful refresh.
9. As a KMP app developer, I want snapshot files written by 3.0 to keep using an ISO-8601 timestamp string, so that the cache format does not become a second break.
10. As a KMP app developer, I want `initialize`, `get`, `getCached`, and `observe` to keep the same behavior, so that call sites do not change besides the Instant type.
11. As a KMP app developer, I want the Compose helpers to compile against this 3.0 client, so that `stringResource` and the composition locals still work on Android, JVM, and iOS.
12. As a maintainer, I want the version catalog to declare Ktor 3.5.2 and Kotlin 2.3.21, so that the published module metadata matches what we compile.
13. As a maintainer, I want the Compose Multiplatform plugin bumped to a release that supports Kotlin 2.3.21, so that the Compose module still builds.
14. As a maintainer, I want the publish job to stay on Ubuntu, so that we do not pay for a macOS runner just to produce klibs.
15. As a maintainer, I want CI to compile the iOS klib targets before publish, so that a missing iOS artifact fails the job instead of shipping another root module that points at 404s.
16. As a maintainer, I want a local Maven publish to include the iOS modules, so that we can prove the variants exist without waiting for Maven Central.
17. As a maintainer, I want README install snippets and the compatibility note to say 3.0.0 and Kotlin 2.3.21, so that consumers copy a version that actually works for them.
18. As a maintainer, I want the README to state that 3.0 needs Ktor 3 and Kotlin 2.3.21, so that a Ktor 2 app knows to stay on 2.3.0.
19. As a Ktor 2 app developer, I want 2.3.0 to remain the last 2.x line, so that I am not forced onto Ktor 3.
20. As a jewel-app developer, I want one 3.0.0 pair of artifacts (client + Compose), so that I do not juggle a compiler bump and a Ktor bump as two releases.
21. As a maintainer, I want the Gradle plugin to stay on Ktor 2 for this release, so that the plugin HTTP client is not dragged into the runtime break.
22. As a maintainer, I want existing client tests to keep passing after the Instant and Ktor moves, so that runtime refresh behavior does not regress.
23. As a maintainer, I want the published root module to stop advertising iOS variants we did not build, so that consumers never see a 404 for a listed target.
24. As a CI operator, I want a documented fallback to a macOS publish job if Linux still cannot build a klib because of C interop, so that we have a next step if the first publish fails.

## Implementation Decisions

- This is a **MAJOR** release. The published version becomes 3.0.0. README version snippets move with it.
- Compile the runtime client, the Compose module, and the included Gradle plugin with Kotlin **2.3.21**. jewel-app is the only current consumer and already uses that compiler. Do not go to 2.4.
- Keep Android Gradle Plugin 8.5.2 and Gradle 8.14.3. Both sit inside the 2.3.21 compatibility range.
- Bump the JetBrains Compose Multiplatform plugin to a release that supports Kotlin 2.3.21. The current 1.7.3 plugin will not build. The Compose module only needs the runtime artifact. Pick the current compatible Compose Multiplatform release at implement time. Do not rewrite Compose helpers.
- Depend on Ktor **3.5.2** for the runtime client (core client only, same as today). Do not add a Darwin or OkHttp engine to the library. Consumers still supply the engine.
- Leave the Gradle plugin on Ktor 2. The plugin talks to TranslationTools only at build time. That Ktor copy never sits on the app classpath.
- Public `Instant` and `Clock` types move to `kotlin.time`. Stop declaring `kotlinx-datetime` as an API dependency. Production code only uses those two types. Tests follow the same types.
- Do not take kotlinx-datetime 0.8.0 just because Kotlin 2.3.21 allows it. The library no longer needs that package on the public API.
- Bump declared kotlinx-coroutines and kotlinx-serialization in the version catalog to releases that work with Kotlin 2.3.21 and Ktor 3.5.2. Do not leave the catalog on 1.9 / 1.7 while we compile with 2.3.
- Snapshot JSON keeps an ISO-8601 string for `lastSuccessfulRefreshAt`. A 2.x cache file must load in 3.0. A 3.0 cache file must use the same string shape. If `kotlin.time.Instant` needs an explicit serializer to do that, add one. Do not change the field name.
- Keep the Ubuntu publish job. After Kotlin 2.2.20, klib cross-compilation is on by default. Do not add the old enable flag unless a 2.3.21 build still requires it.
- CI must compile the iOS klib targets (device and both simulators) in the job that currently only compiles Android and runs JVM tests, then publish. A missing iOS output fails the job.
- The published root modules for the client and the Compose helpers must include working iOS variant modules, not only metadata pointers.
- If Linux still cannot produce a klib because a dependency uses C interop, switch only the publish job to a macOS runner. Do not do that unless Linux fails.
- Do not keep a 2.x development line. 2.3.0 stays on Maven Central as the last Ktor 2 release. New work lands on 3.x.
- Call sites for `get`, `getCached`, `observe`, `initialize`, and Compose `stringResource` stay the same besides the Instant type change.
- Do not change placeholder substitution, plurals handling, or Apple `.strings` sync in this spec.

## Testing Decisions

Good tests here assert what a consumer or CI job can observe: the client still refreshes and reads translations, a snapshot file round-trips, an old snapshot JSON still loads, and a local publish actually writes iOS modules. Do not test that a particular import line changed.

- Keep driving the client with the existing fake API and `runTest`. After the Instant move, the same refresh, cache, and observe cases must still pass. Prior art: the existing client tests and the mutable test clock.
- Keep HTTP tests on Ktor `MockEngine`. They must compile and pass against Ktor 3.5.2. Prior art: the existing HTTP API tests.
- Snapshot store tests must still round-trip `StoredTranslations`. Add one case that loads a fixture JSON whose `lastSuccessfulRefreshAt` is the ISO-8601 string a 2.x client would have written, and assert the timestamp comes back. Prior art: the existing file snapshot store tests with a fake filesystem.
- Compose helper tests follow the Instant type change and must still pass. Prior art: the existing Compose `stringResource` tests.
- Prove iOS artifacts at the highest seam we can run in this repo: publish the client and the Compose module to a local Maven repository on Linux (or the CI Ubuntu job) and assert the iOS variant modules are present on disk. Do not require a live Maven Central check. If the environment cannot compile those targets, fail clearly rather than skip and publish anyway.
- Existing `./gradlew.bat build` and `./gradlew.bat allTests` must pass after the bump. Run them sequentially. Plugin tests stay on the plugin wrapper and must still pass with the plugin compiled as 2.3.21.

## Out of Scope

- Publishing the Gradle plugin to the Plugin Portal or Maven Central.
- Configuration-cache fixes for push and pull.
- A `generated.enabled` flag or any other way to skip codegen.
- Changing the Kotlin Gradle plugin dependency to `compileOnly`.
- Android `%1$s` / `%1$d` substitution and `<plurals>` support.
- A macOS publish job, unless Linux klib publish fails.
- Kotlin 2.4 or a later compiler than 2.3.21.
- Adding HTTP engines (Darwin, OkHttp, CIO) to the runtime client.
- Moving the Gradle plugin to Ktor 3.
- Keeping a maintained 2.x branch.
- Runtime refresh of Apple `.strings` (ADR 0001 still holds).

## Further Notes

- jewel-app is the named consumer. The 3.0 floor (Kotlin 2.3.21, Ktor 3) is chosen for that app. Other 2.1 / Ktor 2 apps stay on published 2.3.0.
- Domain terms: runtime refresh, bundled fallback, TranslationTools. This spec does not change origins, keys, or locales.
- Wave 2 (configuration cache, optional codegen, `compileOnly` Kotlin Gradle plugin) is a separate spec. If that work ships in the same 3.0.0 release, do not bump again. If it ships later, that is a MINOR.
- The colleague research that started this work is the jewel-app adoption review: missing iOS klibs, Ktor 2 vs 3, and datetime Instant on the public API.
