# translationtools-client-kmp

Kotlin Multiplatform TranslationTools runtime client.

## Install

Maven Central:

```text
https://repo1.maven.org/maven2/io/mvdm/translationtools/translationtools-client-kmp/0.1.0/
```

Repository:

```kotlin
repositories {
    mavenCentral()
}
```

Dependency:

```kotlin
dependencies {
    implementation("io.mvdm.translationtools:translationtools-client-kmp:0.1.0")
}
```

Version catalog:

```toml
[libraries]
translationtools-client-kmp = { module = "io.mvdm.translationtools:translationtools-client-kmp", version = "0.1.0" }
```

```kotlin
dependencies {
    implementation(libs.translationtools.client.kmp)
}
```

## What it does

- bootstrap translations from `https://translations.mvdm.io`
- keep reads local after bootstrap
- persist snapshots locally for next app launch
- refresh on startup and app foreground only

Runtime model:

1. create `TranslationToolsClient`
2. call `initialize()` during startup
3. restore persisted snapshots when configured
4. refresh project metadata + selected locale snapshots
5. read translations from in-memory cache
6. call `refreshIfStale()` when returning to foreground

Default refresh behavior:

- refresh on `initialize()`
- refresh on foreground only when older than 1 hour
- no background timer
- no WebSocket/live sync

## Targets

- `android`
- `jvm`
- `iosX64`
- `iosArm64`
- `iosSimulatorArm64`

## Recommended setup

Use the factory:

```kotlin
import io.ktor.client.HttpClient
import io.mvdm.translationtools.client.JvmTranslationSnapshotStores
import io.mvdm.translationtools.client.TranslationTools
import io.mvdm.translationtools.client.TranslationToolsClientOptions

val httpClient = HttpClient()

val client = TranslationTools.createClient(
    httpClient = httpClient,
    options = TranslationToolsClientOptions(
        apiKey = "your-project-api-key",
        currentLocaleProvider = { "en" },
        snapshotStore = JvmTranslationSnapshotStores.default(),
    ),
)
```

You provide:

- project API key
- Ktor `HttpClient`
- optional persisted snapshot store
- optional current locale provider

## Startup and refresh

Call `initialize()` once during startup:

```kotlin
client.initialize()
```

Call `refreshIfStale()` when the app returns to foreground:

```kotlin
client.refreshIfStale()
```

Guidance:

- prefer one shared client instance per app/runtime
- initialize before first real UI read if possible
- do not call `refresh()` on every screen

## Persisted cache

JVM:

```kotlin
val snapshotStore = JvmTranslationSnapshotStores.default()
```

Android:

```kotlin
val snapshotStore = AndroidTranslationSnapshotStores.fromContext(context)
```

Custom path:

```kotlin
import okio.FileSystem
import io.mvdm.translationtools.client.TranslationSnapshotStores

val snapshotStore = TranslationSnapshotStores.file(
    "/some/path/translations.json",
    FileSystem.SYSTEM,
)
```

Persistence behavior:

- store contains project metadata, locale snapshots, refresh timestamp
- corrupt persisted JSON is treated as cache miss and deleted
- `NoOpTranslationSnapshotStore` keeps runtime fully in-memory

## Reading translations

Cache-only read:

```kotlin
val title = client.getCached("home.title") ?: "Home"
```

Fetch on miss:

```kotlin
val title = client.get("home.title", defaultValue = "Home")
```

Observe updates:

```kotlin
client.observe("home.title")
```

Read behavior:

- `getCached(...)` = cache only
- `get(...)` = cache first, then single-item fetch on miss

## Locale selection

Selection order:

1. explicit locale argument
2. `currentLocaleProvider`
3. project default locale
4. fallback `en`

If `preferredLocales` is set, snapshot bootstrap uses that list.

## Android example

```kotlin
class App : Application(), DefaultLifecycleObserver {
    lateinit var translations: TranslationToolsClient

    override fun onCreate() {
        super<Application>.onCreate()

        val httpClient = HttpClient()

        translations = TranslationTools.createClient(
            httpClient = httpClient,
            options = TranslationToolsClientOptions(
                apiKey = BuildConfig.TRANSLATIONTOOLS_API_KEY,
                currentLocaleProvider = {
                    resources.configuration.locales[0].toLanguageTag()
                },
                snapshotStore = AndroidTranslationSnapshotStores.fromContext(this),
            ),
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    suspend fun initializeTranslations() {
        translations.initialize()
    }

    override fun onStart(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            translations.refreshIfStale()
        }
    }
}
```

## Error model

Typed exceptions:

- `TranslationToolsException`
- `TranslationToolsValidationException`
- `TranslationToolsHttpException`
- `TranslationToolsSerializationException`
- `TranslationToolsNetworkException`

Example:

```kotlin
try {
    client.initialize()
}
catch (exception: TranslationToolsHttpException) {
    // auth / permission / remote error
}
catch (exception: TranslationToolsNetworkException) {
    // offline / dns / timeout / transport issue
}
```

## Transport notes

- base URL is fixed to `https://translations.mvdm.io`
- auth uses raw `Authorization` header
- locale requests send `Accept-Encoding: gzip`
- avoid hardcoding production API keys directly in source when possible

## OpenCode

This repo includes `opencode.jsonc` for project-local OpenCode defaults.

- loads repo instructions from `AGENTS.md`
- ignores noisy generated directories during file watching
- adds project shortcuts for build, test, and verify workflows

Keep `opencode.jsonc` and `AGENTS.md` in sync when updating contributor guidance.
