# 06 — Document 3.0.0 for jewel-app and Ktor 2 holdouts

Status: done

## What to build

A jewel-app developer can copy one 3.0.0 pair (runtime client + Compose helpers) from the README onto `commonMain` and know the floor: Kotlin 2.3.21 and Ktor 3. A Ktor 2 app developer reading the same page knows to stay on published 2.3.0 — there is no maintained 2.x line. Install snippets, version-catalog examples, and the plugin compatibility note all say 3.0.0 and Kotlin 2.3.21.

This step does not change runtime refresh, bundled fallback, or plugin sync. It makes the already-green 3.0.0 tree match what consumers will copy.

## Footprint

Projects: `translationtools-client-kmp`, `:translationtools-client-compose`, `gradle/translationtools-plugin`

- `build.gradle.kts` — published `version` stays `3.0.0`
- `README.md` — Maven Central path, dependency snippets, version-catalog examples, plugin compatibility note, 3.0 floor (Kotlin 2.3.21 + Ktor 3), stay-on-2.3.0 for Ktor 2 apps
- `AGENTS.md`, `CLAUDE.md`, `.agents/ref/dependencies.md` — toolchain / artifact versions if they still say 2.1.20 or 2.3.0

## Acceptance criteria

- [x] README install snippets and version-catalog examples use `3.0.0` and match `build.gradle.kts`
- [x] README states that 3.0 needs Ktor 3 and Kotlin 2.3.21
- [x] README states that Ktor 2 apps stay on published 2.3.0
- [x] Plugin compatibility note says the plugin is compiled with Kotlin 2.3.21
- [x] Contributor docs that mention the Kotlin or library version match 3.0.0 / 2.3.21
- [x] `./gradlew.bat build` then `./gradlew.bat allTests`, then `./gradle/translationtools-plugin/gradlew.bat test`, all sequential

## Outcome

README Install now shows one **3.0.0** pair (client + optional Compose) on `commonMain`, with matching version-catalog examples. Floor note: Kotlin 2.3.21 + Ktor 3 (compiled vs 3.5.2; consumer supplies engine); no maintained 2.x line — Ktor 2 apps stay on published **2.3.0**; plugin keeps build-time Ktor 2. Plugin compatibility still Kotlin 2.3.21. `CLAUDE.md` / `.agents/ref/dependencies.md` state published 3.0.0 and the consumer floor. Version left at 3.0.0 (no bump). AGENTS.md had no hardcoded versions.

Footprint drift: Compose still publishes without `iosX64` (carry-forward from 02); README iOS wording already reflects that.

Verified: `./gradlew.bat build`, `./gradlew.bat allTests`, `./gradle/translationtools-plugin/gradlew.bat test`.
