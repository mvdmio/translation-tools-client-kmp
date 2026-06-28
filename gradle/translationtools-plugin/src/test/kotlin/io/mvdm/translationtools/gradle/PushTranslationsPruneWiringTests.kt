package io.mvdm.translationtools.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushTranslationsPruneWiringTests
{
   @Test
   fun pushTranslations_prune_should_default_to_yaml_value()
   {
      val task = registerPushTask(prune = true)

      assertTrue(task.prune.get())
   }

   @Test
   fun pushTranslations_prune_should_default_to_false_when_not_configured()
   {
      val task = registerPushTask(prune = null)

      assertFalse(task.prune.get())
   }

   @Test
   fun pushTranslations_prune_gradle_property_true_should_override_yaml_false()
   {
      assertEquals("true", resolvePruneViaBuild(yamlPrune = false, gradleProperty = "true"))
   }

   @Test
   fun pushTranslations_prune_gradle_property_false_should_override_yaml_true()
   {
      assertEquals("false", resolvePruneViaBuild(yamlPrune = true, gradleProperty = "false"))
   }

   private fun registerPushTask(prune: Boolean?): PushTranslationsTask
   {
      val projectDir = createTempDirectory("translationtools-prune-wiring").toFile()
      val pruneLine = if (prune != null) "  prune: $prune" else ""
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           packageName: com.example.translations
         androidResources:
           resourceDirectories:
             - src/androidMain/res
         $pruneLine
         """.trimIndent()
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      project.pluginManager.apply(TranslationToolsPlugin::class.java)

      return project.tasks.named("pushTranslations", PushTranslationsTask::class.java).get()
   }

   // ProjectBuilder has no Gradle-property source wired up, so property precedence can only be
   // exercised through a real build. Apply just our plugin (the push task is registered
   // unconditionally) and read the resolved prune value back via a helper task.
   private fun resolvePruneViaBuild(yamlPrune: Boolean, gradleProperty: String): String
   {
      val projectDir = createTempDirectory("translationtools-prune-override").toFile()
      File(projectDir, "settings.gradle.kts").writeText(
         """
         pluginManagement {
            repositories {
               google()
               mavenCentral()
               gradlePluginPortal()
            }
         }
         """.trimIndent()
      )
      File(projectDir, "build.gradle.kts").writeText(
         """
         plugins {
            id("io.mvdm.translationtools.plugin")
         }

         tasks.register("printPrune") {
            doLast {
               val push = tasks.getByName("pushTranslations") as io.mvdm.translationtools.gradle.PushTranslationsTask
               println("PRUNE_VALUE=" + push.prune.get())
            }
         }
         """.trimIndent()
      )
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: test-key
         generated:
           packageName: com.example.translations
         androidResources:
           resourceDirectories:
             - src/androidMain/res
           prune: $yamlPrune
         """.trimIndent()
      )

      val result = GradleRunner.create()
         .withProjectDir(projectDir)
         .withPluginClasspath()
         .withArguments("printPrune", "-Ptranslationtools.prune=$gradleProperty")
         .build()

      return result.output.lines()
         .first { it.contains("PRUNE_VALUE=") }
         .substringAfter("PRUNE_VALUE=")
         .trim()
   }
}
