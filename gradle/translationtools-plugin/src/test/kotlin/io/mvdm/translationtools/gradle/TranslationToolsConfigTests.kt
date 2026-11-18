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
         """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val resolved = resolveConfig(project)

      assertEquals(listOf("app/src/main/res", "src/androidMain/res"), resolved.config.androidResources.resourceDirectories)
      assertEquals(mapOf("action_save" to "action.save"), resolved.config.androidResources.keyOverrides)
   }

   @Test
   fun resolveConfig_should_ignore_a_leftover_prune_setting()
   {
      fun resolve(extraYaml: String): TranslationToolsConfig
      {
         val projectDir = createTempDirectory("translationtools-config").toFile()
         File(projectDir, "translationtools.yaml").writeText(
            "apiKey: yaml-key\nandroidResources:\n  resourceDirectories:\n    - app/src/main/res\n$extraYaml"
         )
         return resolveConfig(ProjectBuilder.builder().withProjectDir(projectDir).build()).config
      }

      assertEquals(resolve(""), resolve("  prune: true\n"))
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

   private fun parsePlaceholders(placeholdersYaml: String): TranslationToolsConfig =
      parseConfig("apiKey: yaml-key\n$placeholdersYaml", File("translationtools.yaml"))

   @Test
   fun parseConfig_should_read_placeholder_globals_in_order()
   {
      val config = parsePlaceholders("placeholders:\n  globals:\n    - appName\n    - brand\n    - appName2\n")

      assertEquals(listOf("appName", "brand", "appName2"), config.placeholders.globals)
   }

   @Test
   fun parseConfig_should_give_empty_globals_when_placeholders_is_missing()
   {
      assertEquals(emptyList(), parsePlaceholders("").placeholders.globals)
   }

   @Test
   fun parseConfig_should_give_empty_globals_when_globals_is_missing()
   {
      assertEquals(emptyList(), parsePlaceholders("placeholders: {}\n").placeholders.globals)
   }

   @Test
   fun parseConfig_should_accept_an_invalid_placeholder_name_in_globals()
   {
      val config = parsePlaceholders("placeholders:\n  globals: [AppName]\n")

      assertEquals(listOf("AppName"), config.placeholders.globals)
   }

   @Test
   fun parseConfig_should_reject_placeholders_that_is_not_a_map()
   {
      val exception = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
         parsePlaceholders("placeholders: appName\n")
      }

      assertEquals("'placeholders' in translationtools.yaml must be a YAML map with a 'globals' list.", exception.message)
   }

   @Test
   fun parseConfig_should_reject_globals_that_is_not_a_list()
   {
      val exception = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
         parsePlaceholders("placeholders:\n  globals: appName\n")
      }

      assertEquals("'placeholders.globals' in translationtools.yaml must be a YAML list of strings.", exception.message)
   }

   @Test
   fun parseConfig_should_reject_a_globals_entry_that_is_not_a_string()
   {
      val exception = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
         parsePlaceholders("placeholders:\n  globals:\n    - appName\n    - 42\n")
      }

      assertEquals("'placeholders.globals' in translationtools.yaml must be a YAML list of strings.", exception.message)
   }
}
