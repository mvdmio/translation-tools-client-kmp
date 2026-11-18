package io.mvdm.translationtools.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluralsFunctionalTests
{
   @Test
   fun generate_should_give_plurals_a_reference_and_bundled_snapshot_entries_without_a_not_supported_warning()
   {
      val projectDir = createTempDirectory("translationtools-plurals-generate").toFile()
      writeBuildFiles(projectDir)
      writeStandardTestFixtures(projectDir, locales = listOf("en", "pl"))
      File(projectDir, "src/androidMain/res/values/strings.xml").writeText(
         """
         <resources>
            <plurals name="cart_items">
               <item quantity="other"># items in {cartName}</item>
               <item quantity="one"># item in {cartName}</item>
            </plurals>
            <string name="greeting">Hello {userName}!</string>
         </resources>
         """.trimIndent(),
      )
      File(projectDir, "src/androidMain/res/values-pl").mkdirs()
      File(projectDir, "src/androidMain/res/values-pl/strings.xml").writeText(
         """
         <resources>
            <plurals name="cart_items">
               <item quantity="one"># produkt w {cartName}</item>
               <item quantity="few"># produkty w {cartName}</item>
               <item quantity="many"># produktów w {cartName}</item>
            </plurals>
         </resources>
         """.trimIndent(),
      )

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("generateTranslationResources")
         .build()

      assertEquals(TaskOutcome.SUCCESS, result.task(":generateTranslationResources")?.outcome)
      assertFalse(result.output.contains("plurals are not supported"), result.output)
      val generatedDir = File(projectDir, "build/generated/source/translationtools/commonMain/kotlin/com/example/translations")
      val translations = File(generatedDir, "Translations.kt").readText()
      assertTrue(
         translations.contains("""fallback = "{count, plural, one {# item in {cartName}} other {# items in {cartName}}}""""),
         translations,
      )
      val snapshot = File(generatedDir, "TranslationsBundledSnapshot.kt").readText()
      assertTrue(
         snapshot.contains("""value = "{count, plural, one {# item in {cartName}} other {# items in {cartName}}}""""),
         snapshot,
      )
      assertTrue(
         snapshot.contains("""value = "{count, plural, one {# produkt w {cartName}} few {# produkty w {cartName}} many {# produktów w {cartName}}}""""),
         snapshot,
      )
   }
}
