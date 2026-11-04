# Changelog

## 2026-11-04: The app declares its Keys at start

Version 3.2.0 makes the app declare the Keys of its bundled snapshot to TranslationTools when it starts, under your `environment`. The values fill only empty Translation Values, so an edit made in TranslationTools is never overwritten. A start with no network or no bundled snapshot is unaffected, and a failed Declaration is tried again on the next start.

`pushTranslations` now sends only the Keys and values in your local XML (and Apple `.strings`), and fetches nothing from TranslationTools first. A Key you delete locally is no longer removed by a push; TranslationTools drops it once no running build of the app declares it. The `androidResources.prune` setting is gone; a leftover one in `translationtools.yaml` is ignored.

## 2026-08-19: Plugin apply without workarounds

Version 3.1.0 lets you apply the Gradle plugin on the module that owns your Android XML and run only push and pull. Set `generated.enabled: false` so Kotlin compile does not wait on `Translations.*` files. Push and pull work with Gradle configuration cache on, and `includeBuild` no longer puts the Kotlin Gradle plugin on your build classpath.

## 2026-08-18: Runtime client 3.0.0

Version 3.0.0 of the runtime client and Compose helpers needs Kotlin 2.3.21 and Ktor 3. You can put both artifacts on commonMain and the iOS variants resolve. Runtime refresh timestamps use kotlin.time Instant; apps still on Ktor 2 stay on published 2.3.0.
