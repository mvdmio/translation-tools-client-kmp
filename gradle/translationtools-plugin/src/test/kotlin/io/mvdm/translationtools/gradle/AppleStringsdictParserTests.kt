package io.mvdm.translationtools.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppleStringsdictParserTests
{
   @Test
   fun parse_should_send_the_cart_items_entry_as_a_plural_named_after_its_variable()
   {
      val file = stringsdictPlist(pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items"))

      val read = readStringsdict(file)

      assertEquals(emptyList(), read.warnings)
      assertEquals(
         mapOf("cart_items" to "{items, plural, one {# item} other {# items}}"),
         read.entries.associate { it.key to it.toPluralValue() },
      )
      assertEquals("d", read.entries.single().valueType)
   }

   @Test
   fun toPluralValue_should_send_forms_in_cldr_order_whatever_their_order_in_the_file()
   {
      val file = stringsdictPlist(
         pluralEntry("files", "count", "d", "other" to "%d plików", "many" to "%d plików many", "few" to "%d pliki", "one" to "%d plik"),
         pluralEntry("empty", "count", "d", "other" to "%d things", "zero" to "nothing", "one" to "%d thing"),
      )

      val read = readStringsdict(file)

      assertEquals(
         listOf(PluralQuantity.OTHER, PluralQuantity.MANY, PluralQuantity.FEW, PluralQuantity.ONE),
         read.entries.first { it.key == "files" }.forms.map { it.quantity },
      )
      assertEquals(
         "{count, plural, one {# plik} few {# pliki} many {# plików many} other {# plików}}",
         read.entries.first { it.key == "files" }.toPluralValue(),
      )
      assertEquals(
         "{count, plural, zero {nothing} one {# thing} other {# things}}",
         read.entries.first { it.key == "empty" }.toPluralValue(),
      )
   }

   @Test
   fun toPluralValue_should_turn_only_the_numbers_own_specifier_into_a_hash()
   {
      val cases = listOf(
         Triple("d", "%d item", "# item"),
         Triple("d", "%1\$d item", "# item"),
         Triple("lld", "%lld items", "# items"),
         Triple("lld", "%1\$lld items", "# items"),
         Triple("d", "%d of %d", "# of #"),
         Triple("d", "%@ has %d", "%@ has #"),
         Triple("d", "%2\$@ has %1\$d", "%2\$@ has #"),
         Triple("d", "100%% of %d", "100%% of #"),
         Triple("d", "%%d and %d", "%%d and #"),
         Triple("lld", "%d items", "%d items"),
         Triple("d", "%lld items", "%lld items"),
         Triple("d", "%5d items", "%5d items"),
         Triple("d", "plain text", "plain text"),
      )

      cases.forEach { (valueType, form, expected) ->
         val entry = ParsedStringsdictEntry("k", "n", valueType, listOf(AndroidPluralItem(PluralQuantity.OTHER, form)))

         assertEquals("{n, plural, other {$expected}}", entry.toPluralValue(), "$valueType $form")
      }
   }

   @Test
   fun toPluralValue_should_send_forms_as_written_when_the_entry_has_no_value_type()
   {
      val read = readStringsdict(stringsdictPlist(pluralEntry("k", "n", null, "other" to "%d items")))

      assertEquals("{n, plural, other {%d items}}", read.entries.single().toPluralValue())
   }

   @Test
   fun parse_should_not_count_other_keys_or_non_string_values_as_forms()
   {
      val file = stringsdictPlist(
         """
         <key>k</key>
         <dict>
            <key>NSStringLocalizedFormatKey</key>
            <string>%#@n@</string>
            <key>n</key>
            <dict>
               <key>NSStringFormatSpecTypeKey</key>
               <string>NSStringPluralRuleType</string>
               <key>NSStringFormatValueTypeKey</key>
               <string>d</string>
               <key>one</key>
               <integer>1</integer>
               <key>comment</key>
               <string>not a form</string>
               <key>other</key>
               <string>%d things</string>
            </dict>
         </dict>
         """.trimIndent(),
      )

      assertEquals(
         "{n, plural, other {# things}}",
         readStringsdict(file).entries.single().toPluralValue(),
      )
   }

   @Test
   fun parse_should_skip_entries_whose_format_string_is_not_exactly_one_variable_and_still_sync_the_rest()
   {
      val file = stringsdictPlist(
         pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items"),
         pluralEntry("text_around", "items", "d", "other" to "%d items", format = "You have %#@items@"),
         pluralEntry("two_variables", "files", "d", "other" to "%d files", format = "%#@files@ in %#@folders@"),
         pluralEntry("padded", "items", "d", "other" to "%d items", format = " %#@items@"),
         pluralEntry("no_variable_dict", "items", "d", "other" to "%d items", format = "%#@other@"),
         pluralEntry("plain_format", "items", "d", "other" to "%d items", format = "%d items"),
         "<key>no_format</key><dict><key>items</key><dict><key>other</key><string>x</string></dict></dict>",
         "<key>not_a_dict</key><string>plain</string>",
      )

      val read = readStringsdict(file, "/ios/en.lproj/Localizable.stringsdict")

      assertEquals(listOf("cart_items"), read.entries.map { it.key })
      assertEquals(
         listOf("text_around", "two_variables", "padded", "no_variable_dict", "plain_format", "no_format", "not_a_dict").map {
            "Skipped '$it' in '/ios/en.lproj/Localizable.stringsdict': only an entry whose format string is exactly one plural variable, such as '%#@items@', syncs as a Plural."
         },
         read.warnings,
      )
   }

   @Test
   fun parse_should_skip_a_variable_that_is_not_a_plural_rule_with_a_named_warning()
   {
      val file = stringsdictPlist(
         pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items"),
         pluralEntry("device", "items", "d", "iphone" to "on iPhone", "other" to "elsewhere", ruleType = "NSStringDeviceSpecificRuleType"),
         pluralEntry("width", "w", "d", "1" to "narrow", "other" to "wide", ruleType = "NSStringVariableWidthRuleType"),
         pluralEntry("no_rule", "n", "d", "other" to "%d things", ruleType = null),
         pluralEntry("no_forms", "n", "d", "iphone" to "x"),
      )

      val read = readStringsdict(file, "PATH")

      assertEquals(listOf("cart_items"), read.entries.map { it.key })
      assertEquals(
         listOf(
            "Skipped 'device' in 'PATH': variable 'items' is not a plural rule.",
            "Skipped 'width' in 'PATH': variable 'w' is not a plural rule.",
            "Skipped 'no_rule' in 'PATH': variable 'n' is not a plural rule.",
            "Skipped 'no_forms' in 'PATH': variable 'n' is not a plural rule.",
         ),
         read.warnings,
      )
   }

   @Test
   fun parse_should_report_a_file_that_is_not_an_xml_property_list_with_a_dictionary_root()
   {
      val unreadable = mapOf(
         "binary property list" to byteArrayOf('b'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), '0'.code.toByte(), '0'.code.toByte(), 0x10, 0x01, 0x00),
         "array root" to """<?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><array/></plist>""".toByteArray(),
         "not a plist root" to """<?xml version="1.0" encoding="UTF-8"?><resources/>""".toByteArray(),
         "not xml" to "\"k\" = \"v\";".toByteArray(),
         "empty file" to ByteArray(0),
         "truncated" to """<?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><dict><key>k</key>""".toByteArray(),
      )

      unreadable.forEach { (name, bytes) ->
         val parsed = parseAppleStringsdict(bytes, "PATH")

         assertEquals(
            ParsedAppleStringsdict.Unreadable("Skipped 'PATH': it is not a readable XML property list."),
            parsed,
            name,
         )
      }
   }

   @Test
   fun parse_should_read_the_apple_doctype_without_loading_its_dtd()
   {
      val cartItems = pluralEntry("cart_items", "items", "d", "one" to "%d item", "other" to "%d items")
      val dtds = listOf(
         "http://www.apple.com/DTDs/PropertyList-1.0.dtd",
         "http://127.0.0.1:1/PropertyList-1.0.dtd",
         "file:///nonexistent-directory/PropertyList-1.0.dtd",
      )

      dtds.forEach { dtd ->
         val file = stringsdictPlist(cartItems, doctype = """<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "$dtd">""")

         assertEquals(listOf("cart_items"), readStringsdict(file).entries.map { it.key }, dtd)
      }
   }

   @Test
   fun parse_should_not_expand_an_external_entity()
   {
      val file = stringsdictPlist(
         pluralEntry("cart_items", "items", "d", "one" to "&xxe; %d item", "other" to "%d items"),
         doctype = """<!DOCTYPE plist [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>""",
      )

      assertEquals(
         "{items, plural, one { # item} other {# items}}",
         readStringsdict(file).entries.single().toPluralValue(),
      )
   }
}

private fun readStringsdict(content: String, path: String = "PATH"): ParsedAppleStringsdict.Readable
{
   val parsed = parseAppleStringsdict(content.toByteArray(), path)

   return assertIs<ParsedAppleStringsdict.Readable>(parsed)
}

internal const val APPLE_PLIST_DOCTYPE =
   """<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">"""

/** An XML property list with an Apple DOCTYPE whose root dictionary holds [entries] (already `<key>` / value XML). */
internal fun stringsdictPlist(vararg entries: String, doctype: String = APPLE_PLIST_DOCTYPE): String =
   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$doctype\n<plist version=\"1.0\">\n<dict>\n${entries.joinToString("\n")}\n</dict>\n</plist>\n"

/** A `.stringsdict` entry for [key]; [format] defaults to the whole variable and [ruleType] to the plural rule. */
internal fun pluralEntry(
   key: String,
   variable: String,
   valueType: String?,
   vararg forms: Pair<String, String>,
   format: String = "%#@$variable@",
   ruleType: String? = "NSStringPluralRuleType",
): String = buildString {
   appendLine("<key>$key</key>")
   appendLine("<dict>")
   appendLine("<key>NSStringLocalizedFormatKey</key>")
   appendLine("<string>$format</string>")
   appendLine("<key>$variable</key>")
   appendLine("<dict>")
   if (ruleType != null) {
      appendLine("<key>NSStringFormatSpecTypeKey</key>")
      appendLine("<string>$ruleType</string>")
   }
   if (valueType != null) {
      appendLine("<key>NSStringFormatValueTypeKey</key>")
      appendLine("<string>$valueType</string>")
   }
   forms.forEach { (category, text) ->
      appendLine("<key>$category</key>")
      appendLine("<string>$text</string>")
   }
   appendLine("</dict>")
   appendLine("</dict>")
}.trimEnd()
