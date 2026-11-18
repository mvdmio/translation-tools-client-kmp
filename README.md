# translationtools-client-kmp

`translationtools-client-kmp` is a Kotlin Multiplatform client for TranslationTools.

It keeps Android `strings.xml` as the source of truth, generates typed `Translations.*` accessors for shared code, bundles fallback translations into your app, and refreshes translations from TranslationTools at runtime.

Use it when you want all of this at once:

- keep editing normal Android XML string resources
- use the same typed translation API from shared KMP code
- ship local fallback strings with the app
- fetch updated translations from TranslationTools without changing call sites

Current scope:

- supports Android XML `<string>` and `<plurals>` resources
- generates `Translations.*` and `TranslationsBundledSnapshot`
- supports Android, JVM, and iOS consumers through the KMP client
- skips `<string-array>`
- optionally syncs Apple `.strings` files (`InfoPlist.strings`, `Localizable.strings`), Apple
  `.stringsdict` plurals, and Apple String Catalogs (`.xcstrings`) as a second source of truth — **sync-only**: push/pull plus the bundled `.lproj` files, with
  no generated `Translations.*` accessors and no runtime refresh (see [Apple `.strings` (iOS)](#apple-strings-ios))

## Install

Maven Central:

```text
https://repo1.maven.org/maven2/io/mvdm/translationtools/translationtools-client-kmp/4.0.2/
```

Repository:

```kotlin
repositories {
    mavenCentral()
}
```

Put the **4.0.2** runtime client on `commonMain`. Add the Compose helpers from the same
release when you use composition locals / `stringResource`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.mvdm.translationtools:translationtools-client-kmp:4.0.2")
            // optional
            implementation("io.mvdm.translationtools:translationtools-client-compose:4.0.2")
        }
    }
}
```

Version catalog (same pair):

```toml
[libraries]
translationtools-client-kmp = { module = "io.mvdm.translationtools:translationtools-client-kmp", version = "4.0.2" }
translationtools-client-compose = { module = "io.mvdm.translationtools:translationtools-client-compose", version = "4.0.2" }
```

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.translationtools.client.kmp)
            implementation(libs.translationtools.client.compose)
        }
    }
}
```

The `io.mvdm.translationtools.plugin` Gradle plugin syncs local Android XML (and optional
Apple `.strings`) with TranslationTools. Apply it on the module that owns those resources.
By default it also generates typed `Translations.*` for the runtime client; set
`generated.enabled: false` when you only want push/pull on a KMP module (for example while
you still read Compose `Res.string.*`).

### Gradle plugin setup (composite build)

The plugin is not yet published to a repository. Include it as a composite build from your
consumer project. Copy or clone the `gradle/translationtools-plugin` directory, then add it
to your `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("path/to/translationtools-plugin")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply it in your module:

```kotlin
plugins {
    id("io.mvdm.translationtools.plugin")
}
```

`includeBuild` does **not** put the Kotlin Gradle plugin on your build classpath — the
plugin depends on it only at compile time. Your project supplies Kotlin Multiplatform (or
skips it) itself.

`pushTranslations` and `pullTranslations` are configuration-cache safe. Run them with
configuration cache enabled (Gradle's default in many builds, or
`--configuration-cache`); you do not need `--no-configuration-cache`.

**Compatibility:** the plugin is compiled with Kotlin 2.3.21 and works with Gradle 8.x
and 9.x. Consumer projects can use any Kotlin version from 1.9.25 through current 2.x
releases.

**3.0.0 floor:** the runtime client (and Compose helpers) need **Kotlin 2.3.21** and
**Ktor 3** on the app classpath (compiled against Ktor 3.5.2; you still supply the HTTP
engine). There is no maintained 2.x line — apps still on Ktor 2 stay on published
**2.3.0**. The Gradle plugin keeps its own Ktor 2 client at build time and is not part of
that runtime floor.

## How It Works

1. You keep strings in `src/androidMain/res/values*/**/*.xml`.
2. The Gradle plugin generates Kotlin resources such as `Translations.home_title`.
3. The build also generates `TranslationsBundledSnapshot`, a bundled fallback snapshot from your local XML.
4. Your app creates `TranslationToolsClient` and calls `initialize()`.
5. The client restores cached or bundled translations, then refreshes from TranslationTools.
6. Your code reads translations through `Translations.*`.

## Quick Start

### 1. Apply the plugin in the module that owns your Android resources

```kotlin
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("io.mvdm.translationtools.plugin")
}
```

You can apply the plugin on that shared KMP module even if you only want sync today.
Add `generated.enabled: false` under `generated:` in `translationtools.yaml` to skip
`Translations.*` codegen wiring (Kotlin compile does not wait on generate; pull does not
run generate afterwards). Omit the key or set `true` for the default generate-on-compile
path. The runtime client dependency is only required when you compile against generated
`Translations.*`.

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
androidResources:
  resourceDirectories:
    - src/androidMain/res
  keyOverrides: {}
# Optional — Placeholders the app supplies once through `globalPlaceholders`; they are not function parameters.
# placeholders:
#   globals:
#     - appName
# Optional — only if you also sync Apple .strings, .stringsdict or .xcstrings (String Catalog) files (see "Apple .strings (iOS)").
# appleResources:
#   resourceDirectories:
#     - ../iosApp/iosApp
```

`defaultLocale` and `locales` are shared across both platforms. The `appleResources` block
is optional; omitting it leaves the build Android-only and unchanged.

Config fields:

- `apiKey` — project API key. Can also come from a Gradle property or env var (see lookup order below).
- `defaultLocale` — base locale; defaults to `en`.
- `locales` — the project's locale set.
- `generated.packageName` — package for the generated `Translations` / `TranslationsBundledSnapshot`.
  Defaults to `<android-namespace>.translations` (falling back to `<project.group>.translations`).
- `generated.enabled` — optional; when `false`, the plugin still registers push/pull/init/generate but
  does not wire `Translations.*` codegen into Kotlin compile or pull on a KMP module. Omit or set
  `true` to keep today's generate-on-compile behavior. Use this for sync-only on the module that
  owns the Android XML (this is not Apple `.strings` sync-only — see ADR 0001).
- `androidResources.resourceDirectories` — where your `values*/**.xml` live. Defaults to
  `src/androidMain/res`.
- `androidResources.keyOverrides` — rename an XML `name` to a different translation key, written as
  `xml-name: translation-key`. Applied consistently to generation and push/pull. Defaults to `{}`.
- `placeholders.globals` — optional list of Global placeholder names, such as `appName`. A Global placeholder is
  never a parameter of a generated function, because the app supplies it once through `globalPlaceholders` in
  the client options (see [Placeholders](#placeholders)). Every name listed must also be registered once in
  `globalPlaceholders`; the plugin does not check that. Each name must be a valid Placeholder name (a lowercase
  letter followed by letters and digits); `generateTranslationResources` fails and names an entry that is not.
  Leaving the key out means no Global placeholders.
- `appleResources.resourceDirectories` — optional; enables Apple `.strings`, `.stringsdict` and String Catalog (`.xcstrings`) sync (see
  [Apple `.strings` (iOS)](#apple-strings-ios)).

API key lookup order (Gradle plugin):

1. Gradle property `-Ptranslationtools.apiKey=...`
2. Environment variable `TRANSLATIONTOOLS_API_KEY`
3. `apiKey` in `translationtools.yaml`

The runtime client also needs the API key to refresh translations at app startup. On
Android, a common approach is to expose it via `BuildConfig`:

```kotlin
// androidApp/build.gradle.kts
buildTypes {
    debug {
        buildConfigField("String", "TRANSLATION_TOOLS_API_KEY",
            "\"${localProperties.getProperty("TRANSLATIONTOOLS_API_KEY") ?: System.getenv("TRANSLATIONTOOLS_API_KEY") ?: ""}\"")
    }
}
```

Then set the key in `local.properties` (not committed to version control):

```properties
TRANSLATIONTOOLS_API_KEY=your-project-api-key
```

Pass it when creating the client:

```kotlin
TranslationToolsClientOptions(
    apiKey = BuildConfig.TRANSLATION_TOOLS_API_KEY,
    backgroundRefreshEnabled = BuildConfig.TRANSLATION_TOOLS_API_KEY.isNotBlank(),
    // ...
)
```

Without a valid API key, `backgroundRefreshEnabled` should be `false` and the app will
only use bundled fallback translations.

### 4. Generate the typed resources

```bash
./gradlew.bat generateTranslationResources
```

This generates:

- `Translations.*` for typed string access: a property for a Key with no Placeholders, and a function for a Key
  whose default-locale value needs Placeholders or is a Plural (see [Placeholders](#placeholders) and
  [Plurals](#plurals))
- `TranslationsBundledSnapshot` for bundled fallback data

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
import com.example.translations.TranslationsBundledSnapshot
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
        bundledSnapshot = TranslationsBundledSnapshot.value,
    ),
)
```

Android example:

```kotlin
import android.content.Context
import com.example.translations.TranslationsBundledSnapshot
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
        bundledSnapshot = TranslationsBundledSnapshot.value,
    ),
)
```

iOS example. There is no iOS-specific snapshot-store helper; use the common
`TranslationSnapshotStores.file(...)` with a path inside the app sandbox:

```kotlin
import com.example.translations.TranslationsBundledSnapshot
import io.ktor.client.HttpClient
import io.mvdm.translationtools.client.TranslationSnapshotStores
import io.mvdm.translationtools.client.TranslationTools
import io.mvdm.translationtools.client.TranslationToolsClientOptions
import okio.FileSystem
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

val documentsPath = NSSearchPathForDirectoriesInDomains(
    NSDocumentDirectory, NSUserDomainMask, true,
).first() as String

val client = TranslationTools.createClient(
    httpClient = HttpClient(),
    options = TranslationToolsClientOptions(
        apiKey = "your-project-api-key",
        currentLocaleProvider = { NSLocale.currentLocale.languageCode },
        snapshotStore = TranslationSnapshotStores.file(
            filePath = "$documentsPath/translationtools/translations.json",
            fileSystem = FileSystem.SYSTEM,
        ),
        bundledSnapshot = TranslationsBundledSnapshot.value,
    ),
)
```

`TranslationSnapshotStores.file(filePath, fileSystem)` works on every target;
`JvmTranslationSnapshotStores` and `AndroidTranslationSnapshotStores` are just platform
conveniences over it. If you omit `snapshotStore`, the client uses an in-memory no-op store and
nothing is persisted across launches.

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
import com.example.translations.Translations

val cachedTitle = client.getCached(Translations.home_title)
val title = client.get(Translations.home_title)
val titleUpdates = client.observe(Translations.home_title)
```

Behavior:

- `getCached(...)` returns cached value, otherwise XML fallback, otherwise the key
- `get(...)` returns cached value first and fetches from TranslationTools on cache miss
- `observe(...)` exposes a `Flow` that updates when translations change

`get`, `getCached`, and `observe` also take the value a generated function returns, such as
`Translations.greeting(userName = "Sam")`. They then render the Placeholders and the Plural. With a
plain `Translations.*` property, `getCached` and `observe` still return the raw value.

Locale resolution order:

1. explicit `locale` argument
2. `currentLocaleProvider`
3. project default locale
4. `en`

## Client options

`TranslationToolsClientOptions` controls runtime behavior. Only `apiKey` is required.

| Option | Type | Default | Purpose |
|--------|------|---------|---------|
| `apiKey` | `String` | — (required) | Project API key used to refresh translations and send heartbeats. |
| `currentLocaleProvider` | `() -> String?` | `{ null }` | Resolves the active locale per read (see locale resolution order above). |
| `preferredLocales` | `Set<String>` | `emptySet()` | Limits which locales are downloaded on refresh. When empty, the client fetches `currentLocaleProvider()` + the project default. Set it to pre-fetch a fixed set. |
| `snapshotStore` | `TranslationSnapshotStore` | no-op (no persistence) | Where refreshed translations are cached between launches. |
| `bundledSnapshot` | `StoredTranslations?` | `null` | Generated `TranslationsBundledSnapshot.value`, used as offline fallback before the first refresh. When set, `initialize()` also declares its Keys to TranslationTools (see [Declaring Keys at start](#declaring-keys-at-start)). |
| `backgroundRefreshEnabled` | `Boolean` | `true` | Whether `initialize()` starts a background refresh after restoring the cache. Set `false` when no API key is available. |
| `refreshInterval` | `Duration` | `1.hours` | Staleness window for `refreshIfStale()` and the background refresh. |
| `environment` | `String?` | `null` | Scopes translations, global placeholders, and the start-up Key Declaration to a named server-side environment (e.g. `"production"`, `"staging"`). |
| `heartbeatEnabled` | `Boolean` | `true` | Periodically reports this client (platform, version, environment) so active clients appear in the TranslationTools management UI. |
| `heartbeatInterval` | `Duration` | `1.hours` | How often the heartbeat is sent. |
| `globalPlaceholders` | `Map<String, () -> String?>` | `emptyMap()` | Ambient placeholder resolvers available to every key (see [Placeholders](#placeholders)). |
| `throwOnPlaceholderError` | `Boolean` | `false` | Throw `PlaceholderSubstitutionException` on an unresolved placeholder instead of degrading to the raw token. |

## Declaring Keys at start

When `bundledSnapshot` is set, `initialize()` declares the Keys of that snapshot to TranslationTools in
the background, under `environment`, next to the global placeholder push. This tells TranslationTools
which Keys this build ships. The values travel with it but fill only empty Translation Values, so a
value edited in TranslationTools is never overwritten. The Declaration never delays or fails
`initialize()`: a failed one (for example with no network) is swallowed and sent again on the next
start. With no `bundledSnapshot`, nothing is sent. The new `TranslationToolsApi.declareKeys` method has
a no-op default body, so existing `TranslationToolsApi` implementations keep compiling.

## Refreshing translations

`initialize()` restores the cache and, when `backgroundRefreshEnabled` is set, refreshes once in the
background. You can also drive refresh manually:

```kotlin
client.refresh()        // force an immediate refresh
client.refreshIfStale() // refresh only if older than refreshInterval
```

Observe refresh status to drive UI — an "updating…" indicator, or an offline banner on failure:

```kotlin
import io.mvdm.translationtools.client.TranslationRefreshStatus

client.observeRefreshState().collect { state ->
    when (state.status) {
        TranslationRefreshStatus.Failed -> showOffline(state.lastFailureMessage)
        TranslationRefreshStatus.Ready  -> hideOffline()
        else -> { /* Idle, RestoringCache, Refreshing */ }
    }
    // state.lastSuccessfulRefreshAt (kotlin.time.Instant?) is also available
}
```

## Placeholders

Translation values may contain named **placeholders** as ICU-compatible tokens — `{` + a camelCase
identifier + `}`, e.g. `Hello {userName}`. A literal brace is written with the ICU apostrophe escape
(`'{'`). The value is substituted at read time.

A Key whose default-locale value holds a Placeholder gets a generated **function** instead of a property,
with one `String` parameter per Placeholder, named as the Placeholder and in the order the Placeholders
first appear. Leaving one out, misspelling its name, or passing `null` fails the build:

```kotlin
// <string name="greeting">Hello {userName}!</string>
val greeting = client.get(Translations.greeting(userName = "Sam"), "en") // "Hello Sam!"
```

The required Placeholders come from the default-locale value only; a translation in another Locale never
changes what a call must pass. Text such as `{0}` and `'{'braces'}'` is not a Placeholder. A Placeholder
named like a Kotlin hard keyword, such as `{in}`, keeps its name and is written in backticks by name:
`Translations.demo(`in` = "x")`; a soft keyword such as `{value}` needs none. A Key with no Placeholder, or
only Global placeholders, keeps its property. The function's result also works with `getCached`, `observe`,
and the Compose `stringResource` (see [Compose](#compose)).

For a Key read by name at runtime, the fluent builder takes any value (and overrides a Global placeholder
for one lookup):

```kotlin
val greeting2 = client.withPlaceholders(Translations.greeting(userName = "Sam").resource)
    .setPlaceholder("userName", "Sam")
    .render()
```

**Global placeholders** are available to every key. List their names under `placeholders.globals` in
`translationtools.yaml`, so no generated function asks for them, and register them once in options as
ambient resolvers (evaluated per render); their names are pushed to TranslationTools at startup so they
appear in the management UI:

```kotlin
val client = TranslationTools.createClient(
    httpClient = HttpClient(),
    options = TranslationToolsClientOptions(
        apiKey = "...",
        environment = "production",
        globalPlaceholders = mapOf(
            "appName" to { "My App" },
            "userName" to { currentUser()?.name },
        ),
    ),
)
```

Failure behavior (a token with no binding and no registered global, or a global whose resolver throws
or returns null): the default is **degrade** — render the raw `{token}`. Set
`throwOnPlaceholderError = true` in options to throw a `PlaceholderSubstitutionException` instead. A
per-call binding shadows a global of the same name.

`getCached(...)` and `observe(...)` with a plain `Translations.*` property return the raw value and do
**not** substitute; hand them the value of a generated function, or use `get(...)` / `withPlaceholders(...)`,
to render placeholders.

### Android format specifiers

The runtime client fills only `{named}` placeholders. Android format specifiers such as `%1$s`,
`%2$d` or `%s` are not substituted, so the app would show `Hello %1$s` as-is. Rewrite them as
placeholders:

```xml
<!-- before -->
<string name="greeting">Hello %1$s</string>

<!-- after -->
<string name="greeting">Hello {userName}</string>
```

```kotlin
val greeting = client.get(Translations.greeting(userName = "Sam"), "en") // "Hello Sam"
```

Android's `getString(R.string.greeting, arg)` does **not** fill placeholders. A key still read through
`R.string` must not be rewritten, or must move to the client in the same change. Otherwise that code
shows the raw `{userName}`.

`pushTranslations` warns about every key whose value, in any locale, holds an Android format
specifier. The push still succeeds and sends the values unchanged. A literal percent written as `%%`
does not trigger the warning.

## Plurals

A **Plural** is a translation value with one whole sentence per plural form of its Locale, written as
an ICU `plural` value:

```
{count, plural, one {# item in {cartName}} other {# items in {cartName}}}
```

An Android `<plurals>` resource holds the same thing:

```xml
<plurals name="cart_items">
    <item quantity="one"># item in {cartName}</item>
    <item quantity="other"># items in {cartName}</item>
</plurals>
```

A Plural Key always gets a generated function. Its first parameter is the number, named after the Plural's
argument (`count` for every Key synced from `<plurals>`) and taking a `Long` or an `Int`, then one `String`
parameter per placeholder found in any form (a placeholder written only in `other` is still required).
`{count}` inside a form prints the number and is not a parameter, and the number is a parameter even when
a Global placeholder has the argument's name:

```kotlin
val text = client.get(Translations.cart_items(count = 22, cartName = "Koszyk"), "pl") // "22 produkty w Koszyk"
```

For a Key read by name at runtime, pass the number with `setPluralNumber` next to your placeholders:

```kotlin
val text = client.withPlaceholders(Translations.cart_items(count = 22, cartName = "Koszyk").resource, "pl")
    .setPluralNumber(22)
    .setPlaceholder("cartName", "Koszyk")
    .render() // "22 produkty w Koszyk"
```

How the form is chosen:

- An exact case such as `=0` wins over a category: `{count, plural, =0 {Your cart is empty} other {# items}}`
  shows "Your cart is empty" for 0.
- Otherwise the CLDR plural rules of the lookup's Locale pick the category (in Polish 1 is `one`, 22 is
  `few`, 25 and 12 are `many`). A negative number picks by its size and keeps its minus sign.
- A value with no form for the category uses `other`, and a value with no `other` uses its last form.
- A Locale CLDR does not know uses its language (`pt-br` uses `pt`). An unknown language picks `other`.
- When the client falls back to the `Translations.*` fallback text or to a `defaultValue`, the project's
  default Locale picks the form, so an English fallback in a `ja` lookup still reads `1 item`.

`#` prints the number in the number format of the Locale that picked the form (`1,000` in English,
`1.000` in Dutch; a language CLDR does not know prints plain digits). `{count}`, where `count` is the
Plural's argument, prints it the same way, and `'#'` prints a literal `#`. Other placeholders in the
picked form are filled as usual, global placeholders included; a global placeholder never supplies the
number. Write `#`, not `%d`: as with other placeholders the runtime client does not substitute
Android format specifiers.

A Plural Key's function cannot leave the number out. When a lookup by `TranslationRef` or a
`withPlaceholders(...)` call supplies no number, the client renders the
`other` form, or the last form, with `#` left as `#`, and warns "No number supplied for the Plural in this
value."; with `throwOnPlaceholderError = true` it throws `PlaceholderSubstitutionException` instead. A
number supplied for a value that is not a Plural is ignored. `getCached(...)` and `observe(...)` with a
plain `Translations.*` property or a `TranslationRef` return the raw Plural text; with the value of a
generated function they render it.

Builds of an app on an older client show the raw ICU text of a Plural. Put a Plural on a **new Key**,
and keep the old Key for those builds.

### Syncing `<plurals>` with the Gradle plugin

`pushTranslations` sends each `<plurals>` resource as a Plural under its `name`, with the argument
`count`. The forms go in CLDR order (`zero`, `one`, `two`, `few`, `many`, `other`) whatever their order in
the file, so the `cart_items` resource above is sent as
`{count, plural, one {# item in {cartName}} other {# items in {cartName}}}`. Item text is sent as written.
A `{`, `}` or apostrophe in an item that breaks the Plural grammar makes the value plain text, not a Plural.
Write `#` for the number and `{name}` for a placeholder; the push warns about `%d` and `%1$d` in an item,
as it does for a `<string>`. A `<plurals>` with no items is sent with a null value, and
`translatable="false"` keeps it local. An item with an unknown or repeated `quantity` skips the whole
resource with a warning. `generateTranslationResources` gives each `<plurals>` a `Translations.*`
reference (its fallback is the default-locale Plural) and a `TranslationsBundledSnapshot` entry per locale.

`pullTranslations` keeps each Key's element type. A Key that the default-locale `strings.xml` holds as
`<plurals>` is written back as `<plurals>`, one `<item>` per form in CLDR order, keeping
`translatable="false"`; a Key held as `<string>` stays a `<string>`, even when its value is ICU text. When
the default-locale file has no element for the Key, the element in that locale's own file decides. A Key with
no local element becomes `<plurals>` when its remote value is a Plural with no exact case such as `=0`,
and a `<string>` otherwise. When the remote value of a `<plurals>` Key is a non-null value that is not such a Plural, pull leaves
that locale's resource as it is and warns with the Key, its Origin, and the locale. A null value removes that
locale's `<plurals>` resource without a warning, as it removes a `<string>`.

## Compose

If you use Compose, add `translationtools-client-compose` and provide the client through composition locals.

Runtime client (`translationtools-client-kmp`) publishes Android, JVM, and iOS variant
modules (`iosArm64`, `iosX64`, `iosSimulatorArm64`). Compose helpers
(`translationtools-client-compose`) publish Android, JVM, `iosArm64`, and
`iosSimulatorArm64` (Compose Multiplatform 1.11+ dropped Apple x86_64 / `iosX64`).

Publish stays on Ubuntu and uses Kotlin's default klib cross-compilation (on since
2.2.20). CI compiles those iOS klibs and runs `verifyMavenVariants` (Android and iOS) before Maven
Central; a missing variant fails the job. If Linux ever cannot produce a klib because of
C interop, switch **only** the publish job to a macOS runner — do not leave metadata
pointers without artifacts.

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import com.example.translations.Translations
import io.mvdm.translationtools.client.compose.LocalTranslationToolsClient
import io.mvdm.translationtools.client.compose.LocalTranslationToolsLocale
import io.mvdm.translationtools.client.compose.stringResource

CompositionLocalProvider(
    LocalTranslationToolsClient provides client,
    LocalTranslationToolsLocale provides "en",
) {
    val title = stringResource(Translations.home_title)
    val greeting = stringResource(Translations.greeting(userName = "Sam"))
}
```

`stringResource` with the value of a generated function renders its Placeholders and Plural and updates
when the translation changes.

## Sync Your XML With TranslationTools

Available Gradle tasks:

- `./gradlew.bat initTranslationTools`
  Creates a starter `translationtools.yaml`.
- `./gradlew.bat generateTranslationResources`
  Generates `Translations.*` and `TranslationsBundledSnapshot` from local XML.
- `./gradlew.bat pushTranslations`
  Uploads local XML — and, when `appleResources` is configured, Apple `.strings`, `.stringsdict` and `.xcstrings` — to TranslationTools.
  Sends only the local Keys and values, in one request; nothing is fetched from the server first.
  A Key deleted from `strings.xml` is not removed by a push. It disappears from TranslationTools once
  no running build of the app declares it for 7 days plus a 24-hour grace. While old and new app
  versions both run, TranslationTools keeps the Keys of both. One module's push leaves another
  module's Keys alone. A leftover `androidResources.prune` setting (or `-Ptranslationtools.prune`) is ignored.
  Strings and `<plurals>` marked `translatable="false"` are local-only — never pushed.
- `./gradlew.bat pullTranslations`
  Downloads translations from TranslationTools and updates local XML (and Apple `.strings`, `.stringsdict` and `.xcstrings`).
  When codegen is enabled (default), pull then regenerates Kotlin resources. With
  `generated.enabled: false`, pull only writes resources — it does not run generate.
  Existing entries are updated in place; new keys are added. Keys not managed remotely are written back
  with `translatable="false"`.

`generateTranslationResources` stays Android-only; it never reads Apple `.strings`. It
remains registered when `generated.enabled` is `false`, so you can still run it by hand.

Normal workflow (codegen on — default):

1. Edit `src/androidMain/res/values*/**/*.xml`.
2. Build or run `generateTranslationResources`.
3. Use `Translations.*` in shared or platform code.
4. Run `pushTranslations` when local XML should be sent to TranslationTools.
5. Run `pullTranslations` when remote changes should be merged back into XML.

Sync-only workflow (`generated.enabled: false` on the same module):

1. Edit Android XML (and optional Apple `.strings`, `.stringsdict` and `.xcstrings`).
2. Run `pushTranslations` / `pullTranslations` with configuration cache on as usual.
3. Keep using your existing string reads until you adopt the runtime client and turn codegen back on.

## Apple `.strings` (iOS)

iOS apps ship localized copy outside Android XML — Apple `.strings` files such as
`InfoPlist.strings` (app display name, permission usage descriptions) and
`Localizable.strings`, living in `<locale>.lproj/` directories. You can manage these in
TranslationTools as a **second source of truth** alongside Android XML.

Point the plugin at the directories that *contain* your `.lproj/` folders (paths may resolve
outside the Gradle module, e.g. an Xcode app target beside the shared module):

```yaml
appleResources:
  resourceDirectories:
    - ../iosApp/iosApp
```

Every `.strings` file inside the discovered `.lproj/` folders is then auto-discovered and
included in `pushTranslations` / `pullTranslations`. There is no per-key opt-out.

Behavior:

- **Origins.** Each filename is its own origin (`:/InfoPlist.strings`, `:/Localizable.strings`),
  parallel to `:/strings.xml`, so identical keys across platforms never collide.
- **Locales.** `.lproj` names map to the shared lowercase-hyphen locale axis: `en.lproj → en`,
  `pt-BR.lproj` (and legacy `pt_BR`) → `pt-br`, `Base.lproj → defaultLocale`. If both `Base.lproj`
  and an explicit default-locale directory exist, the explicit one wins and a warning is emitted.
  Write-back uses Apple's conventional casing (`pt-BR.lproj`).
- **Codec.** Reads UTF-8 and UTF-16 (BOM detection); writes UTF-8 without a BOM. Handles standard
  escapes (`\"`, `\\`, `\n`, `\t`, `\Uxxxx`). Unparseable lines are warned-and-skipped, never fatal.
- **Pull is preserve-on-merge.** Existing keys are updated in place; new keys are appended;
  comments, blank lines, and ordering are kept. A missing remote locale's `.lproj/<file>.strings`
  is created (with a managed-by-TranslationTools header) plus a warning to add the region to your
  Xcode project's `knownRegions` — the plugin does not edit `project.pbxproj`.

**Sync-only, by design.** Apple `.strings` get **no** generated `Translations.*` accessors and are
**not** part of `TranslationsBundledSnapshot`; the `.lproj/*.strings` files in the signed app
bundle are themselves the iOS bundled fallback. There is no runtime refresh for these keys, because
the OS (`InfoPlist.strings`) and native code (`NSLocalizedString`) read them from the read-only
bundle — there is no delivery path for a runtime-fetched value. Making app-owned iOS UI text
runtime-updatable (routing native reads through the KMP client) is a separate, larger initiative.
See [`docs/adr/0001`](docs/adr/0001-ios-strings-sync-only.md) and
[`docs/adr/0002`](docs/adr/0002-pull-creates-lproj-dirs-and-warns.md).

### `.stringsdict` plurals

`pushTranslations` also reads every `.stringsdict` file in the same `.lproj/` folders (no extra
setting) and sends each entry that can sync as a Plural, in the same push request as the `.strings`
items.

- **Origins.** Each `.stringsdict` file is its own origin named after the file:
  `Localizable.stringsdict` is `:/Localizable.stringsdict`, separate from `:/Localizable.strings`,
  so a Key in both files is two Translation refs. `Base.lproj` versus an explicit default-locale
  directory is decided per file name, as for `.strings`.
- **Which entries sync.** Only an entry whose format string is exactly one plural variable, such as
  `%#@items@` (no text around it, no whitespace), and whose variable has the rule type
  `NSStringPluralRuleType`. The Plural's argument is the variable name. This entry is sent as
  `{items, plural, one {# item} other {# items}}`:

  ```xml
  <key>cart_items</key>
  <dict>
      <key>NSStringLocalizedFormatKey</key>
      <string>%#@items@</string>
      <key>items</key>
      <dict>
          <key>NSStringFormatSpecTypeKey</key>
          <string>NSStringPluralRuleType</string>
          <key>NSStringFormatValueTypeKey</key>
          <string>d</string>
          <key>one</key>
          <string>%d item</string>
          <key>other</key>
          <string>%d items</string>
      </dict>
  </dict>
  ```

- **Forms.** The category keys `zero`, `one`, `two`, `few`, `many`, and `other` become the forms,
  sent in that order whatever their order in the file. Apple's `zero` is sent as the ICU `zero`
  category.
- **The number becomes `#`.** Inside a form, `%` plus exactly the variable's value type, with or
  without the position `1$`, is sent as `#` (`%d` or `%1$d` when the value type is `d`; `%lld` or
  `%1$lld` when it is `lld`). Every other specifier is sent as written: `%@`, `%2$@`, `%%`, and an
  integer specifier with a different value type such as `%d` in a `lld` entry. A literal `#` in a
  form round-trips: it is sent quoted as `'#'` (`Order #%d` becomes `Order '#'#`, and `%#x` becomes
  `%'#'x`), and pull writes it back without the quote marks. A single apostrophe directly before a
  literal `#` is doubled on the way out and restored on the way back (`Item '#%d` is sent as
  `Item '''#'#`). The same holds for a literal `#` in an Apple String Catalog plural form. Form text
  is otherwise sent as written, so a form with a `{`, `}`, or apostrophe that breaks the Plural grammar
  becomes plain text in TranslationTools. No Android format specifier warning is raised for Apple
  files.
- **Missing entries.** A Locale whose `.stringsdict` file lacks the entry is sent with no value.
- **Warnings.** Push skips what cannot sync, prints a warning, and sends the rest:
  - `Skipped 'KEY' in 'PATH': only an entry whose format string is exactly one plural variable, such as '%#@items@', syncs as a Plural.`
    (for example `You have %#@items@` or `%#@files@ in %#@folders@`; rewrite it as a whole sentence
    per form)
  - `Skipped 'KEY' in 'PATH': variable 'NAME' is not a plural rule.` (device-specific and
    variable-width rules)
  - `Skipped 'PATH': it is not a readable XML property list.` (a binary property list, or an XML
    property list whose root is not a dictionary)

The `.stringsdict` parser accepts the Apple `DOCTYPE` every property list carries but never loads
the DTD or an external entity.

`pullTranslations` writes the Plurals back into the same `.stringsdict` files. `.stringsdict` files
stay sync-only like `.strings`: no `Translations.*` reference, no bundled snapshot, no runtime refresh.

- **Merge into an existing file.** Pull parses the whole file and changes only what it owns. An
  entry the file already has gets exactly the forms of the remote Plural (a local category the
  remote lacks is removed, and a remote `=0` form is written as the `zero` key). The entry keeps
  its variable name, value type, and position in the file, and a form already in the file keeps its
  place. A Key the file lacks is appended after all existing entries, sorted by Key among the new
  ones; existing entries are not re-sorted.
- **`#` becomes the number's specifier.** Pull writes `#` as `%` plus the entry's value type
  (`%d`, `%lld`). A form that also holds a positional specifier such as `%2$@` gets `%1$` plus the
  value type instead (`%1$lld`); a form with only `%@` gets the plain `%lld`. A quoted `'#'` stays
  a literal `#`.
- **New Keys.** A new entry has the format string `%#@NAME@` (NAME is the Plural's argument), the
  rule type `NSStringPluralRuleType`, and a value type taken from the same Key in the default
  Locale's `.stringsdict` file (the `Base.lproj` file when that is the one in use). When that file
  lacks the Key, including when it is the file being written, the value type is `lld`.
- **Nothing is deleted.** A Key with no value in TranslationTools keeps its local entry, and so does
  every entry push skips (text around the variable, several variables, device-specific and
  variable-width rules), byte for byte.
- **Kept-local warning.** When the value in TranslationTools is not a Plural that a `.stringsdict`
  entry can hold (plain text, an empty string, or a Plural with an exact case such as `=5`), pull
  keeps the local entry and prints
  `Kept local 'KEY' in 'PATH': the value for 'LOCALE' in TranslationTools is not a Plural that a .stringsdict entry can hold.`
  A Key with no local entry gets nothing added and the same warning.
- **Unreadable file.** A file that is not an XML property list with a dictionary root (a binary
  property list, for instance) is left unchanged, with
  `Left 'PATH' unchanged: it is not a readable XML property list.` The other Locales' files are
  still written.
- **Xcode's format.** Pull writes the whole file as an XML property list in UTF-8 without a byte
  order mark, tab-indented the way Xcode writes it: the `<?xml ...?>` line, the Apple `DOCTYPE`,
  and a trailing newline. It compares the new bytes with the file's and writes only when they
  differ, so a file Xcode wrote that receives no changes stays byte for byte the same.
- **New Locales.** When TranslationTools has a value for a `.stringsdict` Origin in a Locale with no
  local file, pull creates `<locale>.lproj/<file>.stringsdict` (the standard property-list header,
  no managed-by comment) and prints the `knownRegions` warning, as for `.strings`. Pull never
  creates a `.stringsdict` file for an Origin that has no local file with that name in any Locale
  (an unreadable local file counts as a local file).

### String Catalogs (`.xcstrings`)

An Apple String Catalog (`Localizable.xcstrings`) holds a table's Keys in every Locale in one JSON
file. The plugin syncs it with the same two tasks. A catalog stays **sync-only** like `.strings`: no
`Translations.*` reference, no bundled snapshot, no runtime refresh. Apps can use `.strings`,
`.stringsdict` and String Catalogs together; each file syncs on its own terms.

- **Discovery.** Every `.xcstrings` file that sits directly inside a directory listed under
  `appleResources.resourceDirectories`, next to its `.lproj` directories, is read. It does not look
  inside `.lproj` directories or other subdirectories, and there is no new setting. The "No .lproj
  directories ..." warning appears only when the directories hold no `.strings` or `.stringsdict`
  files and no `.xcstrings` files.
- **Origins.** Each catalog is its own origin named after the file: `Localizable.xcstrings` is
  `:/Localizable.xcstrings`, separate from `:/Localizable.strings`, so a Key in both is two
  Translation refs. Locale codes follow the `.lproj` rule (`pt-BR` becomes `pt-br`).
- **Source language.** The catalog's own `sourceLanguage` decides which Locale falls back to the Key
  text. A Key with no localization in it is sent with the Key itself as the source-language value
  (Xcode leaves that localization out when the value equals the Key, as in `String(localized: "Done")`).
  A source-language value that is empty is sent empty. In every other Locale a Key without a value is
  sent with no value, and TranslationTools keeps what it has.
- **"Don't translate".** A Key marked "Don't translate" in Xcode is not sent and pull leaves it alone,
  like an Android `translatable="false"` entry.
- **Review states and comments.** Every value is sent whatever its review state ("Needs Review",
  "New", "Stale"). Comments stay in the catalog and are not sent.
- **Plurals.** A value that varies by plural syncs as a Plural in one of two shapes:
  - a value that varies by plural as a whole (Xcode's **Vary by Plural**) is a Plural whose argument is
    `count`: the forms `%lld item` and `%lld items` are sent as `{count, plural, one {# item} other {# items}}`;
  - a value that is exactly one substitution such as `%#@items@`, whose substitution varies by plural
    (the shape Xcode makes when it migrates a `.stringsdict`), is a Plural whose argument is the
    substitution's name: `{items, plural, one {# item} other {# items}}`.

  The categories are sent in CLDR order (`zero`, `one`, `two`, `few`, `many`, `other`), whatever their
  order in the file. In the whole-value shape the number is any integer specifier (`%d`, `%i`, `%u`,
  `%ld`, `%li`, `%lu`, `%lld`, `%lli`, `%llu`, with or without a position such as `%1$lld`) and
  becomes `#`. In the substitution shape it is `%arg` or `%` plus the substitution's `formatSpecifier`.
  Every other specifier, such as `%@`, is sent as written. Push gives no Android format specifier
  warning for catalogs.
- **Skipped values.** Push skips a value it cannot send, sends no value for that Key and Locale only,
  and prints one of two warnings; the Key's other Locales are sent as usual:
  - text around the substitution, several substitutions, or a substitution that does not vary by plural
    (`You have %#@items@`):
    `Skipped 'KEY' for 'LOCALE' in 'PATH': only a value that varies by plural as a whole, or is exactly one plural substitution such as '%#@items@', syncs as a Plural.`
  - a value or substitution that varies by device, or nests one variation inside another:
    `Skipped 'KEY' for 'LOCALE' in 'PATH': only plain text and values that vary by plural sync.`

  A file that is not a JSON object with a `strings` object and a non-empty `sourceLanguage` is skipped with
  `Skipped 'PATH': it is not a readable String Catalog.` The other files still sync.
- **Pull changes only what differs.** For each catalog Origin with a local file, pull writes a value
  only when the value from TranslationTools differs from the value push would send for that Key and
  Locale. A value it changes or adds gets the state `Translated`. Comments, the "Don't translate" mark
  and the review state of every other value stay as they are. A plain value is written as plain text,
  and it replaces a local value that varied by plural. Pull leaves alone each Key and Locale push skips.
- **Plural shapes on pull.** A Plural keeps the local value's shape: a value that varies by plural as a
  whole gets the remote forms, a value that is one plural substitution keeps its substitution name,
  `formatSpecifier` and `argNum` and gets the remote forms, and anything else (plain text, no local
  value, a new Key) is written in the whole-value shape. Either way the value ends with exactly the
  forms of the remote Plural, so a local category the remote lacks is removed. A remote `=0` form is
  written as the `zero` category.
- **`#` becomes the number token.** Pull writes `#` as the number token the same value already uses in
  that Locale (`%d` stays `%d`). With no such token (a plain or missing local value) it uses the token
  the Key's source-language value uses, and failing both `%lld` in the whole-value shape and `%arg`
  in the substitution shape. A form that also holds a positional specifier such as `%2$@` gets the
  token with `1$` (`%1$lld`).
- **Kept-local warning.** When the value in TranslationTools is a Plural with an exact case other than
  `=0` (such as `=1`), a String Catalog cannot hold it. Pull keeps the local value for that Key and
  Locale, still writes the Key's other Locales, and prints
  `Kept local 'KEY' in 'PATH': the value for 'LOCALE' in TranslationTools is not a Plural that a String Catalog can hold.`
- **New Keys and Locales.** Pull adds a Key the catalog lacks, in every Locale TranslationTools has a
  value for, with `extractionState: "manual"` as Xcode marks a Key added by hand, so a build does not
  mark it Stale. A source-language value that equals the Key is left out, as Xcode does. When pull adds
  a Locale that no Key of the catalog had before, it prints the `knownRegions` warning, as for
  `.strings`, and writes the Locale code in Xcode's casing (`pt-BR`).
- **Nothing is deleted, and no file is created.** A Key with no value in TranslationTools keeps its
  local value. Pull never creates a String Catalog: an Origin with no local file is ignored.
- **Xcode's layout.** Pull rewrites a catalog only when its content changes, and then writes the whole
  file in Xcode's layout (two-space indentation, ` : ` between a name and its value, new members in
  sorted order), so opening the file in Xcode and saving shows no further change. A catalog whose
  content does not change is not touched, whatever its layout.
- **Unreadable file.** A file that is not a readable String Catalog is left unchanged, with
  `Left 'PATH' unchanged: it is not a readable String Catalog.`

## Migrate From Regular `strings.xml`

If your app already uses Android `strings.xml`, migration is mostly mechanical.

### What you keep

- your existing `src/androidMain/res/values*/strings.xml` files
- your existing string names such as `home_title`
- Android XML as the editable source of truth

### What changes

- generated resources become `Translations.*`
- runtime reads go through `TranslationToolsClient`
- TranslationTools becomes the remote source for updates and sync

### Migration steps

1. Keep your existing `strings.xml` files under `src/androidMain/res/values*/`.
2. Apply `io.mvdm.translationtools.plugin` to the module that owns those files.
3. Add `translationtools.yaml`.
4. Run `./gradlew.bat generateTranslationResources`.
5. Create and initialize `TranslationToolsClient`.
6. Replace runtime-backed string reads with `Translations.*`.
7. Run `./gradlew.bat pushTranslations` once to upload your current XML to TranslationTools.

Typical code migration:

```kotlin
// before
context.getString(R.string.home_title)

// after
client.get(Translations.home_title)
```

Compose migration:

```kotlin
// before
androidx.compose.ui.res.stringResource(R.string.home_title)

// after
io.mvdm.translationtools.client.compose.stringResource(Translations.home_title)
```

Shared code migration:

```kotlin
// before
"Home"

// after
client.get(Translations.home_title)
```

### Migration notes

- only XML `<string>` and `<plurals>` entries are generated
- `<string-array>` is skipped and needs separate handling
- generated files live under `build/generated/...`; do not edit them by hand
- moving a string to a different XML file changes its translation origin in TranslationTools

### Breaking changes in 2.0.0

- Generated accessor renamed from `Res.string.*` to `Translations.*` (avoids clash with Compose's `Res`).
- Bundled snapshot renamed from `ResBundledSnapshot` to `TranslationsBundledSnapshot`.
- The `generated.objectName` key in `translationtools.yaml` has been removed. The generated object is always named `Translations`. Remove `objectName` from your config.
- In call sites, replace `Res.string.foo` with `Translations.foo` and update imports to `<your.package>.Translations` / `<your.package>.TranslationsBundledSnapshot`.

## What You Need In Production

For a complete production setup with runtime refresh and typed accessors, make sure all of
these are true:

1. `translationtools-client-kmp` is in your dependencies.
2. `io.mvdm.translationtools.plugin` is applied to the module with Android XML resources.
3. `translationtools.yaml` exists in the project root (`generated.enabled` omitted or `true`).
4. Your default locale XML exists in `src/androidMain/res/values/`.
5. Your app creates one `TranslationToolsClient` and calls `initialize()` at startup.
6. Your app reads translations through `Translations.*`.
7. You use `pushTranslations` and `pullTranslations` to sync local XML with TranslationTools.

For sync-only on a KMP module (no `Translations.*` yet): apply the plugin on that module,
set `generated.enabled: false`, keep composite-build install via `includeBuild`, and run
push/pull with configuration cache enabled. You do not need a separate non-Kotlin module
or `--no-configuration-cache`.

## Contributing

### Regenerating the CLDR plural table

The runtime client picks a Plural form with the CLDR cardinal plural rules (CLDR 49). Those rules live in
`src/commonMain/kotlin/io/mvdm/translationtools/client/placeholders/CldrPluralRules.kt`, a **generated**
file: do not edit it by hand. It is compiled from the committed CLDR data in `gradle/cldr/plurals.json` by
the `generateCldrPluralRules` Gradle task (`gradle/cldr/cldr-plural-rules.gradle.kts`):

```
./gradlew.bat generateCldrPluralRules
```

Run it, and commit the regenerated file, only when you update `gradle/cldr/plurals.json` to a newer CLDR
release or change the task script. `CldrPluralRulesSamplesTests` (`jvmTest`) checks the table against CLDR's
own integer samples, and the `verifyCldrPluralRules` task, part of `check`, fails when the committed file
differs from what `generateCldrPluralRules` would write.
