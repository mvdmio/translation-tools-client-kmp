package io.mvdm.translationtools.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationToolsConfigTests
{
   @Test
   fun resolveConfig_should_read_yaml_defaults()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
       File(projectDir, "translationtools.yaml").writeText(
            """
            apiKey: yaml-key
            defaultLocale: en
            locales:
              - nl
            generated:
              packageName: com.example.translations
              objectName: Res
           """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

       assertEquals("yaml-key", resolved.config.apiKey)
       assertEquals("en", resolved.config.defaultLocale)
       assertEquals(listOf("nl"), resolved.config.locales)
       assertEquals("com.example.translations", resolved.config.generated?.packageName)
       assertEquals("Res", resolved.config.generated?.objectName)
   }

   @Test
   fun resolveConfig_should_prefer_gradle_property_api_key()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           packageName: com.example.translations
         """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      project.extensions.extraProperties.set("translationtools.apiKey", "property-key")
      val resolved = resolveConfig(project)

      assertEquals("property-key", resolved.config.apiKey)
   }

   @Test
   fun resolveConfig_should_read_android_resource_import_settings()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           packageName: com.example.translations
         androidResources:
           resourceDirectories:
             - app/src/main/res
             - src/androidMain/res
           keyOverrides:
             action_save: action.save
           prune: true
         """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

      assertEquals(listOf("app/src/main/res", "src/androidMain/res"), resolved.config.androidResources.resourceDirectories)
      assertEquals(mapOf("action_save" to "action.save"), resolved.config.androidResources.keyOverrides)
      assertEquals(true, resolved.config.androidResources.prune)
   }

   @Test
   fun renderDefaultConfig_should_include_default_locale_and_android_resource_directory()
   {
      val rendered = renderDefaultConfig()

      assertTrue(rendered.contains("defaultLocale: en"))
      assertTrue(rendered.contains("resourceDirectories:"))
      assertTrue(rendered.contains("src/androidMain/res"))
   }

   @Test
   fun resolveConfig_should_reject_snapshot_file_override()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         snapshotFile: nested/snapshot.json
         generated:
           packageName: com.example.translations
         """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

       val exception = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
          resolveConfig(project)
       }
       assertTrue(exception.message!!.contains("snapshotFile is no longer supported"))
    }
}
