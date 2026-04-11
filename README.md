# translationtools-client-kmp

Kotlin Multiplatform TranslationTools client with XML-first resource generation.

## Install

Maven Central:

```text
https://repo1.maven.org/maven2/io/mvdm/translationtools/translationtools-client-kmp/1.0.0/
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
    implementation("io.mvdm.translationtools:translationtools-client-kmp:1.0.0")
}
```

Version catalog:

```toml
[libraries]
translationtools-client-kmp = { module = "io.mvdm.translationtools:translationtools-client-kmp", version = "1.0.0" }
```

```kotlin
dependencies {
    implementation(libs.translationtools.client.kmp)
}
```

Compose artifact:

```kotlin
dependencies {
    implementation("io.mvdm.translationtools:translationtools-client-compose:1.0.0")
}
```

## What changed in 1.0.0

- local Android XML is now the build-time source of truth
- generated resources are origin-aware and backed by `TranslationRef`
- runtime-backed usage goes through `Res.string.*`
- raw key-only public APIs were removed
- the library no longer uses committed `snapshot.json` as the normal generation input

## Targets

- `android`
- `jvm`
- `iosX64`
- `iosArm64`
- `iosSimulatorArm64`

## Developer workflow

1. Edit Android XML under `src/androidMain/res/values*/**/*.xml`
2. Use generated `Res.string.*` in Android and shared code
3. Run `pushTranslations` to upload local XML to TranslationTools
4. Run `pullTranslations` to merge remote updates into local XML

## XML-first setup

Keep your checked-in XML files under Android resource folders, for example:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="home_title">Home</string>
    <string name="checkout_title">Checkout</string>
</resources>
```

Add `translationtools.yaml` at the repo root:

```yaml
apiKey: your-project-api-key
defaultLocale: en
locales:
  - en
generated:
  packageName: io.mvdm.translationtools.client.resources
  objectName: Res
androidResources:
  resourceDirectories:
    - src/androidMain/res
  keyOverrides: {}
```

Optional API key sources, highest priority first:

1. `-Ptranslationtools.apiKey=...`
2. `TRANSLATIONTOOLS_API_KEY`
3. `apiKey` in `translationtools.yaml`

Notes:

- `translationtools.yaml` is primarily for sync config and overrides
- build-only generation can rely on defaults if your XML lives under `src/androidMain/res`
- generated package defaults to `android.namespace + ".translations"` when not configured explicitly

## Gradle tasks

Init starter config:

```bash
./gradlew.bat initTranslationTools
```

Generate Kotlin sources from local XML:

```bash
./gradlew.bat generateTranslationResources
```

Pull remote translations into local XML:

```bash
./gradlew.bat pullTranslations
```

Push local XML to TranslationTools:

```bash
./gradlew.bat pushTranslations
```

Workflow notes:

- `generateTranslationResources` reads local XML, not `snapshot.json`
- `pullTranslations` updates local XML files and then regenerates Kotlin sources
- unknown remote origins are ignored with warnings
- new remote keys for known origins are added to the mapped local XML file
- `pushTranslations` uploads local XML and does not auto-run `pullTranslations`
- remote deletions require explicit prune mode
- do not commit `build/generated/...`

## Generated API

The generated facade is `Res.string.*`.

Example generated shape:

```kotlin
public object Res {
    public object string {
        public val home_title: TranslationStringResource =
            TranslationStringResource(
                ref = TranslationRef(
                    origin = ":app:/strings.xml",
                    key = "home_title",
                ),
                fallback = "Home",
            )
    }
}
```

Generated resources hide `origin` for normal usage, but the low-level identity is still `TranslationRef(origin, key)`.

## Runtime model

1. Create `TranslationToolsClient`
2. Call `initialize()` during startup
3. Restore persisted snapshots when configured
4. Otherwise restore bundled generated snapshot when provided
5. If nothing restored, do blocking refresh
6. If restored, do background refresh by default
7. Read translations through generated resources
8. Call `refreshIfStale()` when returning to foreground

Default refresh behavior:

- blocking refresh on `initialize()` only when no persisted or bundled snapshot exists
- background refresh after successful persisted or bundled restore by default
- refresh on foreground only when older than 1 hour
- no background timer
- no WebSocket/live sync

## Recommended setup

Use the factory:

```kotlin
import io.ktor.client.HttpClient
import io.mvdm.translationtools.client.JvmTranslationSnapshotStores
import io.mvdm.translationtools.client.TranslationTools
import io.mvdm.translationtools.client.TranslationToolsClientOptions
import io.mvdm.translationtools.client.resources.ResBundledSnapshot

val httpClient = HttpClient()

val client = TranslationTools.createClient(
    httpClient = httpClient,
    options = TranslationToolsClientOptions(
        apiKey = "your-project-api-key",
        currentLocaleProvider = { "en" },
        snapshotStore = JvmTranslationSnapshotStores.default(),
        bundledSnapshot = ResBundledSnapshot.value,
    ),
)
```

## Reading translations

Use generated resources:

```kotlin
import io.mvdm.translationtools.client.resources.Res

val cachedTitle = client.getCached(Res.string.home_title)
val title = client.get(Res.string.home_title)
val updates = client.observe(Res.string.home_title)
```

Low-level access is still available for advanced scenarios:

```kotlin
import io.mvdm.translationtools.client.TranslationRef

val ref = TranslationRef(origin = ":app:/strings.xml", key = "home_title")
val title = client.get(ref)
```

Resource behavior:

- `getCached(resource)` = cached value, else resource fallback, else resource key
- `get(resource)` = cache first, then single-item fetch on miss when the resource is remotely managed
- `observe(resource)` = reactive read with the same fallback chain
- `managedRemotely = false` resources stay local-only and never trigger remote fetches

## Compose usage

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import io.mvdm.translationtools.client.compose.LocalTranslationToolsClient
import io.mvdm.translationtools.client.compose.LocalTranslationToolsLocale
import io.mvdm.translationtools.client.compose.stringResource
import io.mvdm.translationtools.client.resources.Res

CompositionLocalProvider(
    LocalTranslationToolsClient provides client,
    LocalTranslationToolsLocale provides "en",
) {
    val title = stringResource(Res.string.home_title)
}
```

Compose behavior:

- `stringResource(...)` reads from the typed runtime API
- explicit locale wins over `LocalTranslationToolsLocale`
- if neither is set, locale resolution falls back to the client runtime rules

## Migration from older versions

Main migration:

- move from key-only calls to generated resources or `TranslationRef`

Typical migration:

```kotlin
// old
client.getCached("home.title")

// new
client.getCached(Res.string.home_title)
```

Android migration is usually mechanical:

- replace runtime-backed `R.string.*` references with `Res.string.*`

Other important changes:

- `TranslationStringResource` now carries `TranslationRef`
- `TranslationItem` now carries `TranslationRef`
- XML files are the normal source for code generation
- `snapshot.json` is no longer the normal developer-facing artifact

## Locale selection

Selection order:

1. explicit locale argument
2. `currentLocaleProvider`
3. project default locale
4. fallback `en`

If `preferredLocales` is set, startup refresh uses that list.

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
                bundledSnapshot = ResBundledSnapshot.value,
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

## Phase 1 limits

- formatted strings are treated as raw strings
- `<plurals>` are warned and skipped
- `<string-array>` is warned and skipped
- unknown remote origins are ignored with warnings during pull

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
- single-item fetches identify translations by `origin + key`
- avoid hardcoding production API keys directly in source when possible

## OpenCode

This repo includes `opencode.jsonc` for project-local OpenCode defaults.

- loads repo instructions from `AGENTS.md`
- ignores noisy generated directories during file watching
- adds project shortcuts for build, test, and verify workflows

Keep `opencode.jsonc` and `AGENTS.md` in sync when updating contributor guidance.
