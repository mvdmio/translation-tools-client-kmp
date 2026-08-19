# Changelog

## 2026-08-19: Plugin apply without workarounds

Version 3.1.0 lets you apply the Gradle plugin on the module that owns your Android XML and run only push and pull. Set `generated.enabled: false` so Kotlin compile does not wait on `Translations.*` files. Push and pull work with Gradle configuration cache on, and `includeBuild` no longer puts the Kotlin Gradle plugin on your build classpath.

## 2026-08-18: Runtime client 3.0.0

Version 3.0.0 of the runtime client and Compose helpers needs Kotlin 2.3.21 and Ktor 3. You can put both artifacts on commonMain and the iOS variants resolve. Runtime refresh timestamps use kotlin.time Instant; apps still on Ktor 2 stay on published 2.3.0.
