package io.mvdm.translationtools.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome

class TypedPlaceholderFunctionalTests
{
   @Test
   fun generated_functions_take_the_placeholders_of_the_default_locale_and_the_consumer_calls_compile()
   {
      val projectDir = scratchProject(placeholderGlobals = listOf("appName"))
      writeConsumer(
         projectDir,
         """
         val greeting = client.get(Translations.greeting(userName = "Sam"), "en")
         val greetingPositional = client.get(Translations.greeting("Sam"), "en")
         val cartLong = client.get(Translations.cart_items(count = 22L, cartName = "Koszyk"), "pl")
         val cartInt = client.get(Translations.cart_items(count = 22, cartName = "Koszyk"), "pl")
         val cartSize = client.get(Translations.cart_items(count = listOf(1, 2).size, cartName = "Koszyk"), "pl")
         val cartPositional = client.get(Translations.cart_items(22L, "Koszyk"), "pl")
         val footer = client.get(Translations.footer(company = "Acme"), "en")
         val footerOverride = client.withPlaceholders(Translations.footer(company = "Acme").resource, "en").setPlaceholder("company", "Acme").setPlaceholder("appName", "Other").render()
         val keyword = client.get(Translations.typeof_demo(`typeof` = "x"), "en")
         val properties = listOf(client.get(Translations.home_title, "pl"), client.get(Translations.about, "en"), client.get(Translations.escaped, "en"))
         """,
      )

      val compile = compile(projectDir)
      assertEquals(TaskOutcome.SUCCESS, compile.task(":compileKotlinJvm")?.outcome, compile.output)

      val generated = generatedSource(projectDir)
      listOf(
         "fun greeting(userName: String)",
         "fun cart_items(count: Long, cartName: String)",
         "fun cart_items(count: Int, cartName: String)",
         "fun footer(company: String)",
         "fun typeof_demo(`typeof`: String)",
         "val home_title: TranslationStringResource",
         "val about: TranslationStringResource",
         "val escaped: TranslationStringResource",
      ).forEach { assertTrue(generated.contains(it), "expected '$it' in:\n$generated") }
      assertTrue(!generated.contains("fun home_title"), generated)
      assertTrue(!generated.contains("val greeting"), generated)
   }

   @Test
   fun a_call_that_leaves_out_misspells_or_nulls_a_placeholder_fails_to_compile_and_names_it()
   {
      val projectDir = scratchProject(placeholderGlobals = listOf("appName"))
      val failures = listOf(
         "client.get(Translations.greeting, \"en\")" to "greeting",
         "Translations.greeting()" to "userName",
         "Translations.greeting(username = \"Sam\")" to "username",
         "Translations.greeting(userName = null)" to "Null cannot be a value of a non-null type 'String'",
         "Translations.cart_items(cartName = \"Cart\")" to "count",
         "Translations.footer(company = \"Acme\", appName = \"Other\")" to "appName",
      )

      for ((line, named) in failures) {
         writeConsumer(projectDir, "val broken = $line")
         val result = compileExpectingFailure(projectDir)
         assertTrue(result.output.contains("Consumer.kt"), "$line should fail in the consumer file:\n${result.output}")
         assertTrue(result.output.contains(named), "$line should name '$named':\n${result.output}")
      }
   }

   @Test
   fun without_a_placeholders_key_the_app_name_placeholder_is_a_parameter()
   {
      val projectDir = scratchProject(placeholderGlobals = null)
      writeConsumer(
         projectDir,
         """
         val about = client.get(Translations.about(appName = "Shop"), "en")
         val footer = client.get(Translations.footer(appName = "Shop", company = "Acme"), "en")
         val footerPositional = client.get(Translations.footer("Shop", "Acme"), "en")
         """,
      )

      val compile = compile(projectDir)
      assertEquals(TaskOutcome.SUCCESS, compile.task(":compileKotlinJvm")?.outcome, compile.output)

      val generated = generatedSource(projectDir)
      assertTrue(generated.contains("fun about(appName: String)"), generated)
      assertTrue(generated.contains("fun footer(appName: String, company: String)"), generated)
   }

   @Test
   fun generating_twice_with_the_same_input_leaves_the_generated_file_untouched()
   {
      val projectDir = scratchProject(placeholderGlobals = listOf("appName"))
      assertEquals(TaskOutcome.SUCCESS, runner(projectDir, "generateTranslationResources").build().task(":generateTranslationResources")?.outcome)
      val file = generatedFile(projectDir)
      val first = file.readText()
      file.setLastModified(1_000_000_000L)

      runner(projectDir, "generateTranslationResources", "--rerun-tasks").build()

      assertEquals(first, file.readText())
      assertEquals(1_000_000_000L, file.lastModified())
   }

   private fun scratchProject(placeholderGlobals: List<String>?): File
   {
      val projectDir = createTempDirectory("translationtools-typed-placeholders").toFile()
      writeBuildFiles(projectDir)
      writeTypedPlaceholderFixture(projectDir, placeholderGlobals)
      return projectDir
   }

   private fun writeConsumer(projectDir: File, body: String)
   {
      File(projectDir, "src/commonMain/kotlin/Consumer.kt").writeText(
         """
         package com.example.consumer

         import com.example.translations.Translations
         import io.mvdm.translationtools.client.TranslationToolsClient

         suspend fun consume(client: TranslationToolsClient)
         {
         ${body.trimIndent().prependIndent("   ")}
         }
         """.trimIndent()
      )
   }

   private fun generatedFile(projectDir: File) =
      File(projectDir, "build/generated/source/translationtools/commonMain/kotlin/com/example/translations/Translations.kt")

   private fun generatedSource(projectDir: File) = generatedFile(projectDir).readText()

   private fun compile(projectDir: File): BuildResult = runner(projectDir, "compileKotlinJvm").build()

   private fun compileExpectingFailure(projectDir: File): BuildResult = runner(projectDir, "compileKotlinJvm").buildAndFail()

   private fun runner(projectDir: File, vararg args: String) =
      GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(*args)
}
