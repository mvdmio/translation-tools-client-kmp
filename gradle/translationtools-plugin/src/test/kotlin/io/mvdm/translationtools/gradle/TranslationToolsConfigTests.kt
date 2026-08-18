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
           """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

       assertEquals("yaml-key", resolved.config.apiKey)
       assertEquals("en", resolved.config.defaultLocale)
       assertEquals(listOf("nl"), resolved.config.locales)
       assertEquals("com.example.translations", resolved.config.generated?.packageName)
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
   fun resolveConfig_should_read_apple_resource_directories()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           packageName: com.example.translations
         androidResources:
           resourceDirectories:
             - src/androidMain/res
         appleResources:
           resourceDirectories:
             - ../iosApp/iosApp
         """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

      assertEquals(listOf("../iosApp/iosApp"), resolved.config.appleResources?.resourceDirectories)
   }

   @Test
   fun resolveConfig_should_treat_missing_apple_resources_as_a_clean_noop()
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
      val resolved = resolveConfig(project)

      assertEquals(null, resolved.config.appleResources)
   }

   @Test
   fun resolveConfig_should_reject_malformed_apple_resources_shape()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           packageName: com.example.translations
         appleResources:
           resourceDirectories: not-a-list
         """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

      val exception = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
         resolveConfig(project)
      }
      assertTrue(exception.message!!.contains("appleResources.resourceDirectories"))
   }

   @Test
   fun resolveConfig_should_read_generated_enabled_true()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           enabled: true
           packageName: com.example.translations
         """.trimIndent(),
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

      assertEquals(true, resolved.config.generated?.enabled)
      assertEquals("com.example.translations", resolved.config.generated?.packageName)
   }

   @Test
   fun resolveConfig_should_read_generated_enabled_false()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           enabled: false
           packageName: com.example.translations
         """.trimIndent(),
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

      assertEquals(false, resolved.config.generated?.enabled)
      assertEquals("com.example.translations", resolved.config.generated?.packageName)
   }

   @Test
   fun resolveConfig_should_default_generated_enabled_to_true_when_omitted()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           packageName: com.example.translations
         """.trimIndent(),
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

      assertEquals(true, resolved.config.generated?.enabled)
   }

   @Test
   fun resolveConfig_should_default_generated_enabled_to_true_when_generated_block_missing()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         """.trimIndent(),
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

      assertEquals(true, resolved.config.generated?.enabled)
   }

   @Test
   fun resolveConfig_should_reject_non_boolean_generated_enabled()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           enabled: yes-please
           packageName: com.example.translations
         """.trimIndent(),
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

      val exception = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
         resolveConfig(project)
      }
      assertTrue(exception.message!!.contains("generated.enabled"))
      assertTrue(exception.message!!.contains("boolean"))
   }

   @Test
   fun renderDefaultConfig_should_include_default_locale_and_android_resource_directory()
   {
      val rendered = renderDefaultConfig()

      assertTrue(rendered.contains("defaultLocale: en"))
      assertTrue(rendered.contains("resourceDirectories:"))
      assertTrue(rendered.contains("src/androidMain/res"))
      assertTrue(!rendered.contains("enabled"))
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

   @Test
   fun resolveConfig_should_reject_object_name_override()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         generated:
           packageName: com.example.translations
           objectName: Res
         """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()

      val exception = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
         resolveConfig(project)
      }
      assertTrue(exception.message!!.contains("generated.objectName"))
      assertTrue(exception.message!!.contains("Translations"))
   }
}
