package io.mvdm.translationtools.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AndroidResourceImportTests
{
   @Test
   fun loadAndroidResources_should_parse_default_and_localized_strings_and_skip_unsupported_entries()
   {
      val resourceDir = createTempDirectory("translationtools-android-resources").toFile()
      writeStringsFile(
         resourceDir,
         "values",
         """
         <resources>
            <string name="action_save">Save</string>
            <string name="hidden_label" translatable="false">Hidden</string>
            <plurals name="cart_items">
               <item quantity="one">1 item</item>
            </plurals>
            <string-array name="menu_entries">
               <item>Home</item>
            </string-array>
         </resources>
         """.trimIndent(),
      )
      writeStringsFile(
         resourceDir,
         "values-nl",
         """
         <resources>
            <string name="action_save">Opslaan</string>
         </resources>
         """.trimIndent(),
      )
      writeStringsFile(
         resourceDir,
         "values-night",
         """
         <resources>
            <string name="action_save">Night</string>
         </resources>
         """.trimIndent(),
      )

      val imported = loadAndroidResources(listOf(resourceDir), emptyMap())

      assertEquals(mapOf("action.save" to "Save"), imported.defaultLocaleEntries)
      assertEquals(mapOf("nl" to mapOf("action.save" to "Opslaan")), imported.localizedEntries)
      assertEquals(
         listOf(
            "hidden_label: translatable=false",
            "cart_items: plurals are not supported",
            "menu_entries: string-array is not supported",
         ),
         imported.skippedEntries.map { "${it.resourceName}: ${it.reason}" },
      )
   }

   @Test
   fun loadAndroidResources_should_apply_key_overrides_and_fail_for_conflicting_keys()
   {
      val resourceDir = createTempDirectory("translationtools-android-resources").toFile()
      writeStringsFile(
         resourceDir,
         "values",
         """
         <resources>
            <string name="action_save">Save</string>
            <string name="action_cancel">Cancel</string>
         </resources>
         """.trimIndent(),
      )

      val exception = assertFailsWith<IllegalStateException> {
         loadAndroidResources(
            resourceDirectories = listOf(resourceDir),
            keyOverrides = mapOf(
               "action_save" to "common.action",
               "action_cancel" to "common.action",
            ),
         )
      }

      assertEquals(true, exception.message.orEmpty().contains("common.action"))
   }

   @Test
   fun buildTranslationPushRequest_should_include_sorted_default_locale_values_only()
   {
      val request = buildTranslationPushRequest(
         ImportedAndroidResources(
            defaultLocaleEntries = mapOf(
               "z.key" to "Last",
               "a.key" to "First",
            ),
            localizedEntries = mapOf(
               "nl" to mapOf("a.key" to "Eerste"),
            ),
            skippedEntries = emptyList(),
         )
      )

      assertEquals(
         listOf(
            TranslationPushItemRequest(key = "a.key", defaultValue = "First"),
            TranslationPushItemRequest(key = "z.key", defaultValue = "Last"),
         ),
         request.items,
      )
   }
}

private fun writeStringsFile(resourceDir: File, valuesDirectory: String, content: String)
{
   val directory = File(resourceDir, valuesDirectory)
   directory.mkdirs()
   File(directory, "strings.xml").writeText(content)
}
