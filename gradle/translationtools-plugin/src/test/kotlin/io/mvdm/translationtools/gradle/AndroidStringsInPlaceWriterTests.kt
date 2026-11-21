package io.mvdm.translationtools.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidStringsInPlaceWriterTests
{
   private val tools = "http://schemas.android.com/tools"

   private val localFile = """
      <?xml version="1.0" encoding="utf-8"?>
      <resources xmlns:tools="$tools">
          <!-- Home screen -->
          <string name="zeta">Zeta</string>
          <string-array name="planets">
              <item>Mars</item>
              <item>Venus</item>
          </string-array>
          <color name="brand">#FF0000</color>
          <string name="alpha" tools:ignore="MissingTranslation">It's <b>here</b></string>
      </resources>

      """.trimIndent()

   private fun pull(local: String, vararg remote: Pair<String, String?>, keyOverrides: Map<String, String> = emptyMap()): String
   {
      val projectDir = createTempDirectory("translationtools-in-place").toFile()
      File(projectDir, "res/values").mkdirs()
      val target = File(projectDir, "res/values/strings.xml")
      target.writeText(local)

      runPull(
         projectDir,
         locales = listOf("en"),
         remoteByLocale = mapOf("en" to remote.map { (key, value) -> remoteItem(key, value) }),
         keyOverrides = keyOverrides,
      )

      return target.readText()
   }

   @Test
   fun pull_should_change_only_the_element_whose_value_changed()
   {
      val pulled = pull(localFile, "zeta" to "Zeta 2", "alpha" to "It's here")

      assertEquals(localFile.replace("<string name=\"zeta\">Zeta</string>", "<string name=\"zeta\">Zeta 2</string>"), pulled)
   }

   @Test
   fun pull_should_leave_a_file_byte_for_byte_alone_when_nothing_changed()
   {
      assertEquals(localFile, pull(localFile, "zeta" to "Zeta", "alpha" to "It's here"))
   }

   @Test
   fun pull_should_drop_markup_from_a_changed_string_and_keep_its_attributes()
   {
      val pulled = pull(localFile, "zeta" to "Zeta", "alpha" to "It's there")

      assertEquals(
         localFile.replace(
            "<string name=\"alpha\" tools:ignore=\"MissingTranslation\">It's <b>here</b></string>",
            "<string name=\"alpha\" tools:ignore=\"MissingTranslation\">It&apos;s there</string>",
         ),
         pulled,
      )
   }

   @Test
   fun editStringsXml_should_warn_with_key_origin_and_locale_when_the_replaced_string_held_markup()
   {
      val edited = editStringsXml(
         localFile,
         ":/strings.xml",
         "en",
         listOf(PulledChange("alpha", ParsedAndroidResourceValue("alpha", "It's there", true))),
      )

      assertEquals(
         listOf("'alpha' in ':/strings.xml' (locale en) held markup, which pull replaced with the plain text from TranslationTools."),
         edited.warnings,
      )
   }

   @Test
   fun editStringsXml_should_not_warn_when_the_replaced_string_held_no_markup()
   {
      val edited = editStringsXml(
         localFile,
         ":/strings.xml",
         "en",
         listOf(PulledChange("zeta", ParsedAndroidResourceValue("zeta", "Zeta 2", true))),
      )

      assertEquals(emptyList(), edited.warnings)
   }

   @Test
   fun pull_should_write_a_changed_plurals_in_cldr_order_and_keep_translatable_false()
   {
      val local = """
         <resources>
             <plurals name="cart" translatable="false">
                 <item quantity="other">old</item>
             </plurals>
         </resources>

         """.trimIndent()

      val pulled = pull(local, "cart" to "{count, plural, other {# items} one {# item}}")

      assertEquals(
         """
         <resources>
             <plurals name="cart" translatable="false">
                 <item quantity="one"># item</item>
                 <item quantity="other"># items</item>
             </plurals>
         </resources>

         """.trimIndent(),
         pulled,
      )
   }

   @Test
   fun pull_should_indent_the_items_of_a_plurals_by_the_element_when_it_has_no_item_line()
   {
      val local = "<resources>\n    <plurals name=\"cart\"><item quantity=\"other\">old</item></plurals>\n</resources>\n"

      val pulled = pull(local, "cart" to "{count, plural, one {# item} other {# items}}")

      assertEquals(
         "<resources>\n    <plurals name=\"cart\">\n        <item quantity=\"one\"># item</item>\n" +
            "        <item quantity=\"other\"># items</item>\n    </plurals>\n</resources>\n",
         pulled,
      )
   }

   @Test
   fun pull_should_add_new_keys_in_name_order_with_the_indentation_of_the_last_existing_element()
   {
      val pulled = pull(
         localFile,
         "zeta" to "Zeta",
         "alpha" to "It's here",
         "beta" to "Beta",
         "aaa" to "A",
         "cart" to "{count, plural, one {# item} other {# items}}",
      )

      assertEquals(
         localFile.replace(
            "</resources>",
            "    <string name=\"aaa\">A</string>\n" +
               "    <string name=\"beta\">Beta</string>\n" +
               "    <plurals name=\"cart\">\n" +
               "        <item quantity=\"one\"># item</item>\n" +
               "        <item quantity=\"other\"># items</item>\n" +
               "    </plurals>\n" +
               "</resources>",
         ),
         pulled,
      )
   }

   @Test
   fun editStringsXml_should_open_up_a_self_closing_resources_element_and_keep_its_attributes()
   {
      val pulled = editStringsXml(
         "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources xmlns:tools=\"$tools\" />\n",
         ":/strings.xml",
         "nl",
         listOf(
            PulledChange("beta", ParsedAndroidResourceValue("beta", "Beta", true)),
            PulledChange("alpha", ParsedAndroidResourceValue("alpha", "Alpha", true)),
         ),
      ).text

      assertEquals(
         "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources xmlns:tools=\"$tools\">\n" +
            "   <string name=\"alpha\">Alpha</string>\n   <string name=\"beta\">Beta</string>\n</resources>\n",
         pulled,
      )
   }

   @Test
   fun pull_should_put_each_new_element_and_the_closing_tag_on_their_own_line_in_a_single_line_file()
   {
      val pulled = pull(
         "<resources><string name=\"home_title\">Home</string></resources>",
         "home_title" to "Home",
         "beta" to "Beta",
      )

      assertEquals("<resources><string name=\"home_title\">Home</string>\n<string name=\"beta\">Beta</string>\n</resources>", pulled)
   }

   @Test
   fun pull_should_remove_the_line_of_a_null_value_and_keep_the_comment_above_it()
   {
      val pulled = pull(localFile, "zeta" to null, "alpha" to "It's here")

      assertEquals(localFile.replace("    <string name=\"zeta\">Zeta</string>\n", ""), pulled)
   }

   @Test
   fun pull_should_remove_only_the_element_of_a_null_value_that_shares_its_line()
   {
      val pulled = pull(
         "<resources>\n    <string name=\"a\">A</string><string name=\"b\">B</string>\n</resources>\n",
         "a" to null,
         "b" to "B",
      )

      assertEquals("<resources>\n    <string name=\"b\">B</string>\n</resources>\n", pulled)
   }

   @Test
   fun pull_should_write_nothing_for_a_null_value_of_a_key_without_an_element()
   {
      assertEquals(localFile, pull(localFile, "zeta" to "Zeta", "alpha" to "It's here", "beta" to null))
   }

   @Test
   fun pull_should_write_an_empty_value_as_an_empty_element_and_keep_an_empty_element_for_an_empty_value()
   {
      val pulled = pull(
         "<resources>\n   <string name=\"a\"/>\n   <string name=\"b\">B</string>\n</resources>\n",
         "a" to "",
         "b" to "",
         "c" to "",
      )

      assertEquals(
         "<resources>\n   <string name=\"a\"/>\n   <string name=\"b\"></string>\n   <string name=\"c\"></string>\n</resources>\n",
         pulled,
      )
   }

   @Test
   fun pull_should_fill_a_self_closing_string_and_keep_its_attributes()
   {
      val pulled = pull("<resources>\n   <string name=\"a\" tools:ignore=\"X\" />\n</resources>\n", "a" to "A")

      assertEquals("<resources>\n   <string name=\"a\" tools:ignore=\"X\">A</string>\n</resources>\n", pulled)
   }

   @Test
   fun pull_should_leave_an_invalid_plurals_as_written_and_add_no_second_element()
   {
      val local = "<resources>\n   <plurals name=\"cart\">\n      <item quantity=\"lots\">many</item>\n   </plurals>\n</resources>\n"

      assertEquals(local, pull(local, "cart" to "{count, plural, one {# item} other {# items}}"))
   }

   @Test
   fun pull_should_leave_a_string_array_with_a_remote_key_of_the_same_name_as_written()
   {
      assertEquals(localFile, pull(localFile, "zeta" to "Zeta", "alpha" to "It's here", "planets" to "Planets"))
   }

   @Test
   fun pull_should_keep_crlf_on_every_line_including_new_and_replaced_lines()
   {
      val local = "<resources>\n    <plurals name=\"cart\">\n        <item quantity=\"other\">old</item>\n    </plurals>\n</resources>\n"
         .replace("\n", "\r\n")

      val pulled = pull(local, "cart" to "{count, plural, one {# item} other {# items}}", "beta" to "Beta")

      assertEquals(
         ("<resources>\n    <plurals name=\"cart\">\n        <item quantity=\"one\"># item</item>\n" +
            "        <item quantity=\"other\"># items</item>\n    </plurals>\n    <string name=\"beta\">Beta</string>\n</resources>\n")
            .replace("\n", "\r\n"),
         pulled,
      )
   }

   @Test
   fun pull_should_update_a_key_under_key_overrides_by_its_resource_name_and_add_no_second_element()
   {
      val pulled = pull(
         "<resources>\n   <string name=\"home_title\">Home</string>\n</resources>\n",
         "screen.home" to "Start",
         keyOverrides = mapOf("home_title" to "screen.home"),
      )

      assertEquals("<resources>\n   <string name=\"home_title\">Start</string>\n</resources>\n", pulled)
   }

   @Test
   fun pull_should_create_a_missing_locale_file_with_its_keys_sorted_by_name()
   {
      val projectDir = createTempDirectory("translationtools-in-place-missing").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(localFile)

      runPull(
         projectDir,
         locales = listOf("en", "nl"),
         remoteByLocale = mapOf(
            "en" to listOf(remoteItem("zeta", "Zeta"), remoteItem("alpha", "It's here")),
            "nl" to listOf(remoteItem("zeta", "Zeta nl"), remoteItem("alpha", "Alfa")),
         ),
      )

      assertEquals(
         "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n   <string name=\"alpha\">Alfa</string>\n" +
            "   <string name=\"zeta\">Zeta nl</string>\n</resources>\n",
         File(projectDir, "res/values-nl/strings.xml").readText(),
      )
      assertEquals(localFile, File(projectDir, "res/values/strings.xml").readText())
   }
}
