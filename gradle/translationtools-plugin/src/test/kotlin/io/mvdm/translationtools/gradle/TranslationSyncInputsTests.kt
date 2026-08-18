package io.mvdm.translationtools.gradle

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TranslationSyncInputsTests
{
   @Test
   fun resolveSyncExecutionInputs_should_default_blank_locale_and_drop_missing_apple_dirs()
   {
      val projectDir = createTempDirectory("sync-inputs").toFile()
      val androidDir = File(projectDir, "res").apply { mkdirs() }
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("  ")
      task.resourceDirectories.from(androidDir)
      task.appleResourceDirectories.from(File(projectDir, "missing-ios"))
      task.projectPathInput.set(":app")
      task.baseUrl.set("https://example.test")

      val inputs = resolveSyncExecutionInputs(
         task.apiKey,
         task.defaultLocale,
         task.projectPathInput,
         task.baseUrl,
         task.resourceDirectories,
         task.appleResourceDirectories,
      )

      assertEquals("test-key", inputs.apiKey)
      assertEquals("en", inputs.defaultLocale)
      assertEquals(listOf(androidDir.canonicalFile), inputs.androidDirectories.map(File::getCanonicalFile))
      assertEquals(emptyList(), inputs.appleDirectories)
      assertEquals(":app", inputs.projectPath)
      assertEquals("https://example.test", inputs.baseUrl)
   }

   @Test
   fun resolveSyncExecutionInputs_should_require_api_key()
   {
      val projectDir = createTempDirectory("sync-inputs-key").toFile()
      File(projectDir, "res").mkdirs()
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set(" ")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.projectPathInput.set(":")
      task.baseUrl.set(BASE_URL)

      val exception = assertFailsWith<GradleException> {
         resolveSyncExecutionInputs(
            task.apiKey,
            task.defaultLocale,
            task.projectPathInput,
            task.baseUrl,
            task.resourceDirectories,
            task.appleResourceDirectories,
         )
      }
      assertTrue(exception.message!!.contains("API key is required"))
   }

   @Test
   fun resolveSyncExecutionInputs_should_require_existing_android_directories()
   {
      val projectDir = createTempDirectory("sync-inputs-dirs").toFile()
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "missing-res"))
      task.projectPathInput.set(":")
      task.baseUrl.set(BASE_URL)

      val exception = assertFailsWith<GradleException> {
         resolveSyncExecutionInputs(
            task.apiKey,
            task.defaultLocale,
            task.projectPathInput,
            task.baseUrl,
            task.resourceDirectories,
            task.appleResourceDirectories,
         )
      }
      assertTrue(exception.message!!.contains("androidResources.resourceDirectories"))
   }
}
