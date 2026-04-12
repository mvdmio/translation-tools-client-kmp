# translationtools-client-kmp

`translationtools-client-kmp` is a Kotlin Multiplatform client for TranslationTools.

It keeps Android `strings.xml` as the source of truth, generates typed `Res.string.*` accessors for shared code, bundles fallback translations into your app, and refreshes translations from TranslationTools at runtime.

Use it when you want all of this at once:

- keep editing normal Android XML string resources
- use the same typed translation API from shared KMP code
- ship local fallback strings with the app
- fetch updated translations from TranslationTools without changing call sites

Current scope:

- supports Android XML `<string>` resources
- generates `Res.string.*` and `ResBundledSnapshot`
- supports Android, JVM, and iOS consumers through the KMP client
- skips `<plurals>` and `<string-array>`

## Install

Repository:

```kotlin
repositories {
    mavenCentral()
}
```

Runtime client:

```kotlin
dependencies {
    implementation("io.mvdm.translationtools:translationtools-client-kmp:1.0.0")
}
```

Optional Compose helpers:

```kotlin
dependencies {
    implementation("io.mvdm.translationtools:translationtools-client-compose:1.0.0")
}
```

Version catalog:

```toml
[libraries]
translationtools-client-kmp = { module = "io.mvdm.translationtools:translationtools-client-kmp", version = "1.0.0" }
translationtools-client-compose = { module = "io.mvdm.translationtools:translationtools-client-compose", version = "1.0.0" }
```

```kotlin
dependencies {
    implementation(libs.translationtools.client.kmp)
}
```

To generate `Res.string.*`, your module also needs the `io.mvdm.translationtools.plugin` Gradle plugin. That plugin reads Android XML and generates the typed resource API used by this client.

## How It Works

1. You keep strings in `src/androidMain/res/values*/**/*.xml`.
2. The Gradle plugin generates Kotlin resources such as `Res.string.home_title`.
3. The build also generates `ResBundledSnapshot`, a bundled fallback snapshot from your local XML.
4. Your app creates `TranslationToolsClient` and calls `initialize()`.
5. The client restores cached or bundled translations, then refreshes from TranslationTools.
6. Your code reads translations through `Res.string.*`.

## Quick Start

### 1. Apply the plugin in the KMP module that owns your Android resources

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("io.mvdm.translationtools.plugin")
}
```

The runtime dependency alone does not generate resources.

### 2. Keep your strings in Android XML

Example `src/androidMain/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="home_title">Home</string>
    <string name="checkout_title">Checkout</string>
</resources>
```

Example `src/androidMain/res/values-nl/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="home_title">Start</string>
    <string name="checkout_title">Afrekenen</string>
</resources>
```

### 3. Add `translationtools.yaml` at the project root

```yaml
apiKey: your-project-api-key
defaultLocale: en
locales:
  - en
  - nl
generated:
  packageName: com.example.translations
  objectName: Res
androidResources:
  resourceDirectories:
    - src/androidMain/res
  keyOverrides: {}
```

API key lookup order:

1. Gradle property `-Ptranslationtools.apiKey=...`
2. Environment variable `TRANSLATIONTOOLS_API_KEY`
3. `apiKey` in `translationtools.yaml`

### 4. Generate the typed resources

```bash
./gradlew.bat generateTranslationResources
```

This generates:

- `Res.string.*` for typed string access
- `ResBundledSnapshot` for bundled fallback data

The plugin also wires generation into Kotlin compilation, so normal builds regenerate resources automatically.

### 5. Create the client

Common shape:

```kotlin
import io.ktor.client.HttpClient
import io.mvdm.translationtools.client.TranslationTools
import io.mvdm.translationtools.client.TranslationToolsClientOptions
```

JVM example:

```kotlin
import com.example.translations.ResBundledSnapshot
import io.ktor.client.HttpClient
import io.mvdm.translationtools.client.JvmTranslationSnapshotStores
import io.mvdm.translationtools.client.TranslationTools
import io.mvdm.translationtools.client.TranslationToolsClientOptions

val client = TranslationTools.createClient(
    httpClient = HttpClient(),
    options = TranslationToolsClientOptions(
        apiKey = "your-project-api-key",
        currentLocaleProvider = { "en" },
        snapshotStore = JvmTranslationSnapshotStores.default(),
        bundledSnapshot = ResBundledSnapshot.value,
    ),
)
```

Android example:

```kotlin
import android.content.Context
import com.example.translations.ResBundledSnapshot
import io.ktor.client.HttpClient
import io.mvdm.translationtools.client.AndroidTranslationSnapshotStores
import io.mvdm.translationtools.client.TranslationTools
import io.mvdm.translationtools.client.TranslationToolsClientOptions

fun createTranslationsClient(context: Context) = TranslationTools.createClient(
    httpClient = HttpClient(),
    options = TranslationToolsClientOptions(
        apiKey = "your-project-api-key",
        currentLocaleProvider = {
            context.resources.configuration.locales[0].toLanguageTag()
        },
        snapshotStore = AndroidTranslationSnapshotStores.fromContext(context),
        bundledSnapshot = ResBundledSnapshot.value,
    ),
)
```

### 6. Initialize once during app startup

```kotlin
suspend fun startTranslations() {
    client.initialize()
}
```

`initialize()` does this:

- restore persisted snapshot when available
- otherwise restore bundled snapshot from generated XML data
- otherwise do a blocking remote refresh
- after restore, optionally do a background refresh

### 7. Read translations

```kotlin
import com.example.translations.Res

val cachedTitle = client.getCached(Res.string.home_title)
val title = client.get(Res.string.home_title)
val titleUpdates = client.observe(Res.string.home_title)
```

Behavior:

- `getCached(...)` returns cached value, otherwise XML fallback, otherwise the key
- `get(...)` returns cached value first and fetches from TranslationTools on cache miss
- `observe(...)` exposes a `Flow` that updates when translations change

Locale resolution order:

1. explicit `locale` argument
2. `currentLocaleProvider`
3. project default locale
4. `en`

## Compose

If you use Compose, add `translationtools-client-compose` and provide the client through composition locals.

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import com.example.translations.Res
import io.mvdm.translationtools.client.compose.LocalTranslationToolsClient
import io.mvdm.translationtools.client.compose.LocalTranslationToolsLocale
import io.mvdm.translationtools.client.compose.stringResource

CompositionLocalProvider(
    LocalTranslationToolsClient provides client,
    LocalTranslationToolsLocale provides "en",
) {
    val title = stringResource(Res.string.home_title)
}
```

## Sync Your XML With TranslationTools

Available Gradle tasks:

- `./gradlew.bat initTranslationTools`
  Creates a starter `translationtools.yaml`.
- `./gradlew.bat generateTranslationResources`
  Generates `Res.string.*` and `ResBundledSnapshot` from local XML.
- `./gradlew.bat pushTranslations`
  Uploads local XML to TranslationTools.
- `./gradlew.bat pullTranslations`
  Downloads translations from TranslationTools, updates local XML, then regenerates Kotlin resources.

Normal workflow:

1. Edit `src/androidMain/res/values*/**/*.xml`.
2. Build or run `generateTranslationResources`.
3. Use `Res.string.*` in shared or platform code.
4. Run `pushTranslations` when local XML should become the remote state.
5. Run `pullTranslations` when remote changes should be merged back into XML.

## Migrate From Regular `strings.xml`

If your app already uses Android `strings.xml`, migration is mostly mechanical.

### What you keep

- your existing `src/androidMain/res/values*/strings.xml` files
- your existing string names such as `home_title`
- Android XML as the editable source of truth

### What changes

- generated resources become `Res.string.*`
- runtime reads go through `TranslationToolsClient`
- TranslationTools becomes the remote source for updates and sync

### Migration steps

1. Keep your existing `strings.xml` files under `src/androidMain/res/values*/`.
2. Apply `io.mvdm.translationtools.plugin` to the module that owns those files.
3. Add `translationtools.yaml`.
4. Run `./gradlew.bat generateTranslationResources`.
5. Create and initialize `TranslationToolsClient`.
6. Replace runtime-backed string reads with `Res.string.*`.
7. Run `./gradlew.bat pushTranslations` once to upload your current XML to TranslationTools.

Typical code migration:

```kotlin
// before
context.getString(R.string.home_title)

// after
client.get(Res.string.home_title)
```

Compose migration:

```kotlin
// before
androidx.compose.ui.res.stringResource(R.string.home_title)

// after
io.mvdm.translationtools.client.compose.stringResource(Res.string.home_title)
```

Shared code migration:

```kotlin
// before
"Home"

// after
client.get(Res.string.home_title)
```

### Migration notes

- only XML `<string>` entries are generated
- `<plurals>` and `<string-array>` are skipped and need separate handling
- generated files live under `build/generated/...`; do not edit them by hand
- moving a string to a different XML file changes its translation origin in TranslationTools

## What You Need In Production

For a complete production setup, make sure all of these are true:

1. `translationtools-client-kmp` is in your dependencies.
2. `io.mvdm.translationtools.plugin` is applied to the module with Android XML resources.
3. `translationtools.yaml` exists in the project root.
4. Your default locale XML exists in `src/androidMain/res/values/`.
5. Your app creates one `TranslationToolsClient` and calls `initialize()` at startup.
6. Your app reads translations through `Res.string.*`.
7. You use `pushTranslations` and `pullTranslations` to sync local XML with TranslationTools.
