package io.mvdm.translationtools.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationToolsConfigTests
{
   @Test
   fun resolveConfig_should_read_yaml_defaults()
   {
      val projectDir = createTempDirectory("translationtools-config").toFile()
      File(projectDir, "translationtools.yaml").writeText(
         """
         apiKey: yaml-key
         locales:
           - nl
         snapshotFile: translationtools/snapshot.json
         generated:
           packageName: com.example.translations
           objectName: Res
         """.trimIndent()
      )
      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val extension = project.extensions.create("translationTools", TranslationToolsExtension::class.java)

      val resolved = resolveConfig(project, extension)

      assertEquals("yaml-key", resolved.config.apiKey)
      assertEquals(listOf("nl"), resolved.config.locales)
      assertEquals("translationtools/snapshot.json", resolved.config.snapshotFile)
      assertEquals("com.example.translations", resolved.config.generated.packageName)
      assertEquals("Res", resolved.config.generated.objectName)
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
      val extension = project.extensions.create("translationTools", TranslationToolsExtension::class.java)

      val resolved = resolveConfig(project, extension)

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
      val extension = project.extensions.create("translationTools", TranslationToolsExtension::class.java)

      val resolved = resolveConfig(project, extension)

      assertEquals(listOf("app/src/main/res", "src/androidMain/res"), resolved.config.androidResources.resourceDirectories)
      assertEquals(mapOf("action_save" to "action.save"), resolved.config.androidResources.keyOverrides)
   }
}
