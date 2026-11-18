package io.mvdm.translationtools.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleStringsdictWriterTests
{
   @Test
   fun write_should_leave_a_file_xcode_wrote_byte_for_byte_the_same_and_not_rewrite_it()
   {
      val target = fileWith(XCODE_FIXTURE)
      target.setLastModified(1_000_000_000_000L)

      val result = writeAppleStringsdictFile(
         target,
         "en",
         mapOf("cart_items" to "{items, plural, one {# item} other {# items}}", "inbox" to null),
         emptyMap(),
      )

      assertEquals(AppleStringsdictWrite(created = false, warnings = emptyList()), result)
      assertEquals(XCODE_FIXTURE, target.readText())
      assertEquals(1_000_000_000_000L, target.lastModified())
   }

   @Test
   fun write_should_change_only_the_edited_line_and_keep_name_value_type_position_and_skipped_entries()
   {
      val target = fileWith(XCODE_FIXTURE)

      writeAppleStringsdictFile(target, "en", mapOf("cart_items" to "{items, plural, one {# item} other {# things}}"), emptyMap())

      assertEquals(XCODE_FIXTURE.replace("<string>%d items</string>", "<string>%d things</string>"), target.readText())
   }

   @Test
   fun write_should_remove_a_category_the_remote_lacks_and_write_an_exact_zero_case_as_the_zero_key()
   {
      val target = fileWith(XCODE_FIXTURE)

      writeAppleStringsdictFile(target, "en", mapOf("cart_items" to "{items, plural, =0 {no items} other {# items}}"), emptyMap())

      // zero goes where the category order puts it: before the first later category, the file's own `other` here.
      assertEquals(
         XCODE_FIXTURE.replace(
            "\t\t\t<key>one</key>\n\t\t\t<string>%d item</string>\n\t\t\t<key>other</key>",
            "\t\t\t<key>zero</key>\n\t\t\t<string>no items</string>\n\t\t\t<key>other</key>",
         ),
         target.readText(),
      )
   }

   @Test
   fun write_should_keep_the_forms_in_the_order_the_file_holds_them_and_place_a_new_one_by_category_order()
   {
      val target = fileWith(
         stringsdictXcode(
            plurals("files", "n", "d", "other" to "%d plików", "one" to "%d plik"),
            plurals("folders", "n", "d", "one" to "%d folder", "other" to "%d folderów"),
         ),
      )

      writeAppleStringsdictFile(
         target,
         "pl",
         mapOf(
            "files" to "{n, plural, one {# plik} other {# pliku}}",
            "folders" to "{n, plural, one {# folder} few {# foldery} many {# folderów} other {# folderu}}",
         ),
         emptyMap(),
      )

      assertEquals(mapOf("other" to "%d pliku", "one" to "%d plik"), formsOf(target, "files", "n"))
      assertEquals(listOf("other", "one"), formsOf(target, "files", "n").keys.toList())
      assertEquals(
         mapOf("one" to "%d folder", "few" to "%d foldery", "many" to "%d folderów", "other" to "%d folderu"),
         formsOf(target, "folders", "n"),
      )
      assertEquals(listOf("one", "few", "many", "other"), formsOf(target, "folders", "n").keys.toList())
   }

   @Test
   fun write_should_write_the_number_as_the_entrys_value_type_and_a_quoted_hash_without_its_quote_marks()
   {
      val target = fileWith(
         stringsdictXcode(
            plurals("a", "n", "lld", "one" to "x", "other" to "x"),
            plurals("b", "n", "lld", "one" to "x", "other" to "x"),
            plurals("c", "n", "lld", "one" to "x", "other" to "x"),
            plurals("d", "n", "lld", "one" to "x", "other" to "x"),
         ),
      )

      writeAppleStringsdictFile(
         target,
         "en",
         mapOf(
            "a" to "{n, plural, one {# item} other {# items}}",
            "b" to "{n, plural, one {# of %2\$@} other {%2\$@ has # items, # in all}}",
            "c" to "{n, plural, one {# item for %@} other {# items for %@}}",
            "d" to "{n, plural, one {'#' item} other {'#' is # items}}",
         ),
         emptyMap(),
      )

      assertEquals("%lld item", formsOf(target, "a", "n").getValue("one"))
      assertEquals("%1\$lld of %2\$@", formsOf(target, "b", "n").getValue("one"))
      assertEquals("%2\$@ has %1\$lld items, %1\$lld in all", formsOf(target, "b", "n").getValue("other"))
      assertEquals("%lld item for %@", formsOf(target, "c", "n").getValue("one"))
      assertEquals("# item", formsOf(target, "d", "n").getValue("one"))
      assertEquals("# is %lld items", formsOf(target, "d", "n").getValue("other"))
   }

   @Test
   fun write_should_write_a_quoted_hash_without_quote_marks_whether_or_not_the_form_holds_the_number()
   {
      val target = fileWith(
         stringsdictXcode(
            plurals("order", "count", "d", "one" to "x", "other" to "x"),
            plurals("late", "count", "d", "one" to "x", "other" to "x"),
            plurals("item", "count", "d", "one" to "x", "other" to "x"),
         ),
      )

      writeAppleStringsdictFile(
         target,
         "en",
         mapOf(
            "order" to "{count, plural, one {Order '#'#} other {Order '#'}}",
            "late" to "{count, plural, one {Order '#'# (late)} other {Order '#'#s}}",
            "item" to "{count, plural, one {Item '''#'#} other {Item '''#'}}",
         ),
         emptyMap(),
      )

      assertEquals("Order #%d", formsOf(target, "order", "count").getValue("one"))
      assertEquals("Order #", formsOf(target, "order", "count").getValue("other"))
      assertEquals("Order #%d (late)", formsOf(target, "late", "count").getValue("one"))
      assertEquals("Order #%ds", formsOf(target, "late", "count").getValue("other"))
      assertEquals("Item '#%d", formsOf(target, "item", "count").getValue("one"))
      assertEquals("Item '#", formsOf(target, "item", "count").getValue("other"))
   }

   @Test
   fun write_should_write_any_other_quoted_run_as_it_is()
   {
      val target = fileWith(stringsdictXcode(plurals("k", "n", "d", "one" to "x", "other" to "x")))

      writeAppleStringsdictFile(
         target,
         "en",
         mapOf("k" to "{n, plural, one {'{x}' # it''s} other {'{'#'}' and '#'}}"),
         emptyMap(),
      )

      assertEquals("'{x}' %d it''s", formsOf(target, "k", "n").getValue("one"))
      assertEquals("'{'%d'}' and #", formsOf(target, "k", "n").getValue("other"))
   }

   @Test
   fun write_should_leave_the_file_unchanged_when_pull_returns_what_push_sent_for_a_literal_hash()
   {
      val forms = listOf("Order #%d", "Hex %#x and %d", "Item '#%d", "# and # of %d")
      val content = stringsdictXcode(*forms.mapIndexed { index, form -> plurals("k$index", "count", "d", "other" to form) }.toTypedArray())
      val target = fileWith(content)
      val pushed = (parseAppleStringsdict(target.readBytes(), target.path) as ParsedAppleStringsdict.Readable).entries.associate { it.key to it.toPluralValue() }
      target.setLastModified(1_000_000_000_000L)

      writeAppleStringsdictFile(target, "en", pushed, emptyMap())

      assertEquals(content, target.readText())
      assertEquals(1_000_000_000_000L, target.lastModified())
   }

   @Test
   fun write_should_leave_the_file_unchanged_when_pull_returns_what_push_sent_for_an_entry_without_a_value_type()
   {
      val content = stringsdictXcode(plurals("none", "count", null, "one" to "Order #", "other" to "Order #"))
      val target = fileWith(content)
      val pushed = (parseAppleStringsdict(target.readBytes(), target.path) as ParsedAppleStringsdict.Readable).entries.associate { it.key to it.toPluralValue() }

      writeAppleStringsdictFile(target, "en", pushed, emptyMap())

      assertEquals("{count, plural, one {Order '#'} other {Order '#'}}", pushed.getValue("none"))
      assertEquals(content, target.readText())
   }

   @Test
   fun write_should_append_missing_keys_after_the_existing_entries_sorted_among_themselves_with_value_type_lld()
   {
      val target = fileWith(XCODE_FIXTURE)

      writeAppleStringsdictFile(
         target,
         "en",
         mapOf(
            "inbox_messages" to "{count, plural, one {# message} other {# messages}}",
            "a_first" to "{total, plural, other {# total}}",
         ),
         emptyMap(),
      )

      val appended = listOf(
         "\t<key>a_first</key>",
         "\t<dict>",
         "\t\t<key>NSStringLocalizedFormatKey</key>",
         "\t\t<string>%#@total@</string>",
         "\t\t<key>total</key>",
         "\t\t<dict>",
         "\t\t\t<key>NSStringFormatSpecTypeKey</key>",
         "\t\t\t<string>NSStringPluralRuleType</string>",
         "\t\t\t<key>NSStringFormatValueTypeKey</key>",
         "\t\t\t<string>lld</string>",
         "\t\t\t<key>other</key>",
         "\t\t\t<string>%lld total</string>",
         "\t\t</dict>",
         "\t</dict>",
         "\t<key>inbox_messages</key>",
         "\t<dict>",
         "\t\t<key>NSStringLocalizedFormatKey</key>",
         "\t\t<string>%#@count@</string>",
         "\t\t<key>count</key>",
         "\t\t<dict>",
         "\t\t\t<key>NSStringFormatSpecTypeKey</key>",
         "\t\t\t<string>NSStringPluralRuleType</string>",
         "\t\t\t<key>NSStringFormatValueTypeKey</key>",
         "\t\t\t<string>lld</string>",
         "\t\t\t<key>one</key>",
         "\t\t\t<string>%lld message</string>",
         "\t\t\t<key>other</key>",
         "\t\t\t<string>%lld messages</string>",
         "\t\t</dict>",
         "\t</dict>",
      ).joinToString("\n", postfix = "\n")
      assertEquals(XCODE_FIXTURE.replace("</dict>\n</plist>\n", "$appended</dict>\n</plist>\n"), target.readText())
   }

   @Test
   fun write_should_give_a_new_entry_the_value_type_of_the_same_key_in_the_default_locales_file()
   {
      val target = fileWith(stringsdictXcode())

      writeAppleStringsdictFile(
         target,
         "pl",
         mapOf("inbox" to "{count, plural, other {# x}}", "other_key" to "{count, plural, other {# y}}"),
         mapOf("inbox" to "d"),
      )

      assertEquals("%d x", formsOf(target, "inbox", "count").getValue("other"))
      assertEquals("%lld y", formsOf(target, "other_key", "count").getValue("other"))
   }

   @Test
   fun stringsdictValueTypes_should_list_the_value_type_of_each_entry_that_names_one()
   {
      val file = fileWith(
         stringsdictXcode(
            plurals("cart_items", "items", "d", "other" to "%d items"),
            plurals("no_type", "items", null, "other" to "%d items"),
         ),
      )

      assertEquals(mapOf("cart_items" to "d"), stringsdictValueTypes(file))
      assertEquals(emptyMap(), stringsdictValueTypes(null))
      assertEquals(emptyMap(), stringsdictValueTypes(fileWith("not a plist")))
   }

   @Test
   fun write_should_keep_the_local_entry_and_warn_for_a_value_no_entry_can_hold()
   {
      val unholdable = mapOf(
         "cart_items" to "Items",
         "empty" to "",
         "exact" to "{items, plural, =5 {five} other {# items}}",
         "missing_key" to "Items",
      )
      val target = fileWith(
         stringsdictXcode(
            plurals("cart_items", "items", "d", "one" to "%d item", "other" to "%d items"),
            plurals("empty", "items", "d", "other" to "%d items"),
            plurals("exact", "items", "d", "other" to "%d items"),
         ),
      )
      val before = target.readText()

      val result = writeAppleStringsdictFile(target, "en", unholdable, emptyMap())

      assertEquals(before, target.readText())
      assertEquals(
         listOf("cart_items", "empty", "exact", "missing_key").map {
            "Kept local '$it' in '${target.path}': the value for 'en' in TranslationTools is not a Plural that a .stringsdict entry can hold."
         },
         result.warnings,
      )
   }

   @Test
   fun write_should_keep_a_local_entry_that_has_no_remote_value()
   {
      val target = fileWith(XCODE_FIXTURE)

      writeAppleStringsdictFile(target, "en", mapOf("cart_items" to null, "missing" to null), emptyMap())

      assertEquals(XCODE_FIXTURE, target.readText())
   }

   @Test
   fun write_should_leave_a_file_that_is_not_an_xml_property_list_unchanged_and_say_so()
   {
      val binary = byteArrayOf('b'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 0, 1, 2, 0xFF.toByte())
      val target = File(createTempDirectory("translationtools-stringsdict-writer").toFile(), "en.lproj/Localizable.stringsdict")
      target.parentFile.mkdirs()
      target.writeBytes(binary)

      val result = writeAppleStringsdictFile(target, "en", mapOf("cart_items" to "{items, plural, other {# items}}"), emptyMap())

      assertContentEquals(binary, target.readBytes())
      assertEquals(
         AppleStringsdictWrite(created = false, warnings = listOf("Left '${target.path}' unchanged: it is not a readable XML property list.")),
         result,
      )
   }

   @Test
   fun write_should_create_a_missing_file_with_the_standard_header_and_no_managed_by_comment()
   {
      val target = File(createTempDirectory("translationtools-stringsdict-writer").toFile(), "de.lproj/Localizable.stringsdict")

      val result = writeAppleStringsdictFile(
         target,
         "de",
         mapOf("cart_items" to "{items, plural, one {# Artikel} other {# Artikel}}"),
         mapOf("cart_items" to "d"),
      )

      assertEquals(AppleStringsdictWrite(created = true, warnings = emptyList()), result)
      assertEquals(
         stringsdictXcode(plurals("cart_items", "items", "d", "one" to "%d Artikel", "other" to "%d Artikel")),
         target.readText(),
      )
      assertFalse(target.readBytes().take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
   }

   @Test
   fun write_should_create_a_file_with_an_empty_dictionary_when_no_value_can_be_held()
   {
      val target = File(createTempDirectory("translationtools-stringsdict-writer").toFile(), "de.lproj/Localizable.stringsdict")

      val result = writeAppleStringsdictFile(target, "de", mapOf("cart_items" to "Artikel"), emptyMap())

      assertTrue(result.created)
      assertEquals(stringsdictXcode().replace("<dict>\n</dict>", "<dict/>"), target.readText())
   }

   @Test
   fun write_should_reach_the_same_bytes_when_run_twice_on_the_same_input()
   {
      val target = fileWith(XCODE_FIXTURE)
      val updates = mapOf(
         "cart_items" to "{items, plural, =0 {none} one {# item} other {# things}}",
         "inbox" to "{count, plural, other {# messages}}",
      )

      writeAppleStringsdictFile(target, "en", updates, emptyMap())
      val first = target.readBytes()
      target.setLastModified(1_000_000_000_000L)
      val second = writeAppleStringsdictFile(target, "en", updates, emptyMap())

      assertContentEquals(first, target.readBytes())
      assertEquals(AppleStringsdictWrite(created = false, warnings = emptyList()), second)
      assertEquals(1_000_000_000_000L, target.lastModified())
   }

   private fun fileWith(content: String): File
   {
      val file = File(createTempDirectory("translationtools-stringsdict-writer").toFile(), "en.lproj/Localizable.stringsdict")
      file.parentFile.mkdirs()
      file.writeText(content)
      return file
   }

   /** The text of each form of [variable] under [key], read back with the push parser. */
   private fun formsOf(file: File, key: String, variable: String): Map<String, String>
   {
      val parsed = parseAppleStringsdict(file.readBytes(), file.path) as ParsedAppleStringsdict.Readable
      val entry = parsed.entries.single { it.key == key }
      assertEquals(variable, entry.variable)
      return entry.forms.associate { it.quantity.keyword to it.text }
   }
}

/** A `.stringsdict` file as Xcode writes it: [entries] are the root dictionary's lines, tab indented. */
private fun stringsdictXcode(vararg entries: String): String =
   (
      listOf(
         "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
         "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">",
         "<plist version=\"1.0\">",
         "<dict>",
      ) + entries + listOf("</dict>", "</plist>")
   ).joinToString("\n", postfix = "\n")

/** The Xcode-shaped lines of a plural entry for [key]. */
private fun plurals(key: String, variable: String, valueType: String?, vararg forms: Pair<String, String>): String =
   (
      listOf(
         "\t<key>$key</key>",
         "\t<dict>",
         "\t\t<key>NSStringLocalizedFormatKey</key>",
         "\t\t<string>%#@$variable@</string>",
         "\t\t<key>$variable</key>",
         "\t\t<dict>",
         "\t\t\t<key>NSStringFormatSpecTypeKey</key>",
         "\t\t\t<string>NSStringPluralRuleType</string>",
      ) +
         listOfNotNull(valueType?.let { "\t\t\t<key>NSStringFormatValueTypeKey</key>\n\t\t\t<string>$it</string>" }) +
         forms.map { (category, text) -> "\t\t\t<key>$category</key>\n\t\t\t<string>$text</string>" } +
         listOf("\t\t</dict>", "\t</dict>")
      ).joinToString("\n")

/**
 * A `.stringsdict` file in the exact shape Xcode writes it, written out line by line (not by the serializer under test):
 * a syncing entry, an entry push skips (text around the variable), a variable-width entry, and nodes pull does not manage.
 */
private val XCODE_FIXTURE: String = listOf(
   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
   "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">",
   "<plist version=\"1.0\">",
   "<dict>",
   "\t<key>cart_items</key>",
   "\t<dict>",
   "\t\t<key>NSStringLocalizedFormatKey</key>",
   "\t\t<string>%#@items@</string>",
   "\t\t<key>items</key>",
   "\t\t<dict>",
   "\t\t\t<key>NSStringFormatSpecTypeKey</key>",
   "\t\t\t<string>NSStringPluralRuleType</string>",
   "\t\t\t<key>NSStringFormatValueTypeKey</key>",
   "\t\t\t<string>d</string>",
   "\t\t\t<key>one</key>",
   "\t\t\t<string>%d item</string>",
   "\t\t\t<key>other</key>",
   "\t\t\t<string>%d items</string>",
   "\t\t</dict>",
   "\t</dict>",
   "\t<key>greeting</key>",
   "\t<dict>",
   "\t\t<key>NSStringLocalizedFormatKey</key>",
   "\t\t<string>You have %#@items@</string>",
   "\t\t<key>items</key>",
   "\t\t<dict>",
   "\t\t\t<key>NSStringFormatSpecTypeKey</key>",
   "\t\t\t<string>NSStringPluralRuleType</string>",
   "\t\t\t<key>NSStringFormatValueTypeKey</key>",
   "\t\t\t<string>d</string>",
   "\t\t\t<key>other</key>",
   "\t\t\t<string>%d &amp; &lt;things&gt;</string>",
   "\t\t</dict>",
   "\t</dict>",
   "\t<key>fits</key>",
   "\t<dict>",
   "\t\t<key>NSStringLocalizedFormatKey</key>",
   "\t\t<string>%#@width@</string>",
   "\t\t<key>width</key>",
   "\t\t<dict>",
   "\t\t\t<key>NSStringFormatSpecTypeKey</key>",
   "\t\t\t<string>NSStringVariableWidthRuleType</string>",
   "\t\t\t<key>1</key>",
   "\t\t\t<string>Short</string>",
   "\t\t</dict>",
   "\t</dict>",
   "\t<key>notes</key>",
   "\t<dict>",
   "\t\t<key>enabled</key>",
   "\t\t<true/>",
   "\t\t<key>disabled</key>",
   "\t\t<false/>",
   "\t\t<key>count</key>",
   "\t\t<integer>5</integer>",
   "\t\t<key>tags</key>",
   "\t\t<array>",
   "\t\t\t<string>a</string>",
   "\t\t\t<string></string>",
   "\t\t</array>",
   "\t\t<key>none</key>",
   "\t\t<array/>",
   "\t\t<key>empty</key>",
   "\t\t<dict/>",
   "\t</dict>",
   "</dict>",
   "</plist>",
).joinToString("\n", postfix = "\n")
