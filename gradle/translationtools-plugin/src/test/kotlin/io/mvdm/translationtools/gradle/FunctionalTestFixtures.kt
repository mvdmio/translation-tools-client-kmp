package io.mvdm.translationtools.gradle

import java.io.File

internal fun writeBuildFiles(projectDir: File, kotlinVersion: String = "1.9.25")
{
   writeSettingsFiles(projectDir)

   File(projectDir, "build.gradle.kts").writeText(
      """
      plugins {
         id("org.jetbrains.kotlin.multiplatform") version "$kotlinVersion"
         id("io.mvdm.translationtools.plugin")
      }

      kotlin {
         jvm()
      }

      tasks.register("printCommonMainKotlinSrcDirs") {
         val srcDirs = project.extensions
            .getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            .sourceSets.getByName("commonMain").kotlin.srcDirs
         doLast {
            srcDirs.forEach { src ->
               println("COMMON_MAIN_SRC=" + src.invariantSeparatorsPath)
            }
         }
      }

      """.trimIndent()
   )
}

internal fun writeNonKmpBuildFiles(projectDir: File)
{
   writeSettingsFiles(projectDir)

   File(projectDir, "build.gradle.kts").writeText(
      """
      plugins {
         id("io.mvdm.translationtools.plugin")
      }
      """.trimIndent()
   )
}

private fun writeSettingsFiles(projectDir: File)
{
   File(projectDir, "settings.gradle.kts").writeText(
      """
      pluginManagement {
         repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
         }
      }

      dependencyResolutionManagement {
         repositories {
            google()
            mavenCentral()
         }
      }
      """.trimIndent()
   )
}

internal fun writeStandardTestFixtures(
   projectDir: File,
   enabled: Boolean? = null,
   locales: List<String> = listOf("en"),
   appleResourceDirectories: List<String> = emptyList(),
   placeholderGlobals: List<String>? = null,
)
{
   File(projectDir, "translationtools.yaml").writeText(
      buildString {
         appendLine("apiKey: test-key")
         appendLine("defaultLocale: en")
         appendLine("locales:")
         locales.forEach { appendLine("  - $it") }
         appendLine("generated:")
         if (enabled != null)
            appendLine("  enabled: $enabled")
         appendLine("  packageName: com.example.translations")
         appendLine("androidResources:")
         appendLine("  resourceDirectories:")
         appendLine("    - src/androidMain/res")
         if (placeholderGlobals != null)
         {
            appendLine("placeholders:")
            appendLine("  globals:")
            placeholderGlobals.forEach { appendLine("    - $it") }
         }
         if (appleResourceDirectories.isNotEmpty())
         {
            appendLine("appleResources:")
            appendLine("  resourceDirectories:")
            appleResourceDirectories.forEach { appendLine("    - $it") }
         }
      },
   )
   File(projectDir, "src/androidMain/res/values").mkdirs()
   File(projectDir, "src/androidMain/res/values/strings.xml").writeText(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
         <string name="home_title">Home</string>
      </resources>
      """.trimIndent()
   )
}

/**
 * Writes the Spec's How-to-Check-It `strings.xml` files (default `en`, plus `pl`) plus a `{typeof}` Key, and the runtime stand-ins
 * the generated code compiles against. [placeholderGlobals] is the `placeholders.globals` list, or null for no `placeholders` key.
 */
internal fun writeTypedPlaceholderFixture(projectDir: File, placeholderGlobals: List<String>?)
{
   writeStandardTestFixtures(projectDir, locales = listOf("en", "pl"), placeholderGlobals = placeholderGlobals)
   File(projectDir, "src/androidMain/res/values/strings.xml").writeText(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
         <string name="home_title">Home</string>
         <string name="greeting">Hello {userName}!</string>
         <string name="footer">{appName} by {company}</string>
         <string name="about">About {appName}</string>
         <string name="escaped">Use '{'braces'}' and {0} here</string>
         <string name="typeof_demo">Type {typeof}</string>
         <plurals name="cart_items">
            <item quantity="one"># item in {cartName}</item>
            <item quantity="other"># items in {cartName}</item>
         </plurals>
      </resources>
      """.trimIndent()
   )
   File(projectDir, "src/androidMain/res/values-pl").mkdirs()
   File(projectDir, "src/androidMain/res/values-pl/strings.xml").writeText(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
         <string name="home_title">Start {userName}</string>
         <plurals name="cart_items">
            <item quantity="one"># produkt w {cartName}</item>
            <item quantity="few"># produkty w {cartName}</item>
            <item quantity="many"># produktów w {cartName}</item>
         </plurals>
      </resources>
      """.trimIndent()
   )
   writeRuntimeStandIns(projectDir)
}

/**
 * Hand-written stand-ins for the runtime types and calls the generated code and consumers use, copied from the real public
 * signatures (the scratch project has no runtime client on its classpath).
 */
private fun writeRuntimeStandIns(projectDir: File)
{
   File(projectDir, "src/commonMain/kotlin").mkdirs()
   File(projectDir, "src/commonMain/kotlin/RuntimeStandIns.kt").writeText(
      """
      package io.mvdm.translationtools.client

      public data class TranslationRef(val origin: String, val key: String)

      public data class TranslationStringResource(
         val ref: TranslationRef,
         val fallback: String? = null,
         val managedRemotely: Boolean = true,
      )

      public data class ProjectMetadata(val locales: List<String>, val defaultLocale: String)

      public data class TranslationItem(val ref: TranslationRef, val value: String?)

      public data class TranslationSnapshot(val locale: String, val items: List<TranslationItem>)

      // The real type's `lastSuccessfulRefreshAt` is a kotlinx Instant; the generated snapshot only ever passes null.
      public data class StoredTranslations(
         val projectMetadata: ProjectMetadata?,
         val snapshots: List<TranslationSnapshot>,
         val lastSuccessfulRefreshAt: Any?,
      )

      public data class BoundTranslationStringResource(
         val resource: TranslationStringResource,
         val placeholders: Map<String, String> = emptyMap(),
         val pluralNumber: Long? = null,
      )

      public class TranslationRenderBuilder
      {
         public fun setPlaceholder(name: String, value: String?): TranslationRenderBuilder = this

         public suspend fun render(): String = ""
      }

      public class TranslationToolsClient
      {
         public fun withPlaceholders(resource: TranslationStringResource, locale: String? = null): TranslationRenderBuilder =
            TranslationRenderBuilder()

         public suspend fun get(resource: TranslationStringResource, locale: String? = null): String = ""

         public suspend fun get(resource: BoundTranslationStringResource, locale: String? = null): String = ""
      }
      """.trimIndent()
   )
}
