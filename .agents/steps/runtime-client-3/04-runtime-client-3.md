# 04 — Refresh from TranslationTools over Ktor 3

Status: done

## What to build

A KMP app already on Ktor 3 can put this runtime client on `commonMain` without Gradle mixing Ktor 2 bytecode into a Ktor 3 runtime. Runtime refresh still fetches project metadata, locales, single keys, heartbeats, and global placeholder names from TranslationTools.

The Gradle plugin stays on Ktor 2 for this release. That copy talks to TranslationTools only at build time and never sits on the app classpath. Do not add a Darwin or OkHttp engine to the library; consumers still supply the engine.

HTTP tests stay on Ktor `MockEngine` and must compile and pass against Ktor 3.5.2. Existing client tests that drive refresh through the fake API must still pass.

## Footprint

Projects: `translationtools-client-kmp`, `:translationtools-client-compose`, `gradle/translationtools-plugin`

- `gradle/libs.versions.toml` — runtime Ktor version (`3.5.2`); plugin Ktor version remains 2.x
- `build.gradle.kts` — `ktor-client-core` (commonMain), `ktor-client-mock` (jvmTest)
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationToolsHttpApi.kt` — `TranslationToolsHttpApi`, Ktor request/response types
- `src/commonMain/kotlin/io/mvdm/translationtools/client/TranslationToolsFactory.kt` — `TranslationTools.createClient` `HttpClient` parameter
- `src/jvmTest/kotlin/io/mvdm/translationtools/client/TranslationToolsHttpApiTests.kt` — `MockEngine` path/header/body cases
- `src/jvmTest/kotlin/io/mvdm/translationtools/client/TranslationToolsFactoryTests.kt` — factory HTTP wiring
- `src/commonTest/kotlin/io/mvdm/translationtools/client/TranslationToolsClientTests.kt` — refresh / initialize behaviour via `FakeTranslationToolsApi`
- `gradle/translationtools-plugin/build.gradle.kts` — plugin still on the Ktor 2 aliases
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/ProjectTranslationPushClient.kt` — build-time CIO client
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/PushTranslationsTask.kt` — push HTTP
- `gradle/translationtools-plugin/src/main/kotlin/io/mvdm/translationtools/gradle/PullTranslationsTask.kt` — pull HTTP
- `README.md` — consumer note that 3.0 needs Ktor 3 (Ktor 2 apps stay on 2.3.0)

## Acceptance criteria

- [x] Catalog runtime Ktor is 3.5.2; plugin Ktor stays on 2.x
- [x] `TranslationToolsHttpApi` compiles and talks to TranslationTools through Ktor 3 `HttpClient`
- [x] Existing `MockEngine` HTTP tests compile and pass against Ktor 3.5.2
- [x] Existing client refresh / cache / observe tests still pass
- [x] Plugin push/pull tests still pass on Ktor 2
- [x] The library does not add a Darwin, OkHttp, or CIO engine
- [x] `./gradlew.bat build` then `./gradlew.bat allTests`, then `./gradle/translationtools-plugin/gradlew.bat test`, all sequential

## Outcome

Catalog `ktor` (runtime) moved to **3.5.2**; `ktor-plugin` stays **2.3.12**. Runtime still depends only on `ktor-client-core` / `ktor-client-mock` — no Darwin, OkHttp, or CIO engines added. `TranslationToolsHttpApi` and factory wiring needed no source changes for Ktor 3; existing `MockEngine` HTTP tests and fake-API client refresh tests pass as-is. Plugin still uses `ktor-plugin-client-*` aliases and its push/pull tests pass on Ktor 2. README: consumer note that 3.0 needs Ktor 3 / Kotlin 2.3.21 (Ktor 2 apps stay on 2.3.0). Docs: `.agents/ref/dependencies.md` notes runtime Ktor 3.5.2 vs plugin Ktor 2.3.12.

Footprint drift: none material — HttpApi / factory / HTTP tests / plugin HTTP sources untouched beyond catalog + docs.

Verified: `./gradlew.bat build`, `./gradlew.bat allTests`, `./gradlew.bat jvmTest --rerun-tasks`, `./gradle/translationtools-plugin/gradlew.bat test --rerun-tasks`.
