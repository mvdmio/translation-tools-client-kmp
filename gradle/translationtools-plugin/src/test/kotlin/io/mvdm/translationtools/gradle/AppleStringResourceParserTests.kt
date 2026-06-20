package io.mvdm.translationtools.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleStringResourceParserTests
{
   @Test
   fun parseAppleStringsContent_should_parse_quoted_form_and_skip_comments()
   {
      val parsed = parseAppleStringsContent(
         """
         /* Permission usage descriptions */
         "NSCameraUsageDescription" = "We use the camera to scan codes."; // inline comment
         "CFBundleDisplayName" = "Jewel";
         """.trimIndent(),
      )

      assertEquals(emptyList(), parsed.warnings)
      assertEquals(
         listOf(
            ParsedAppleEntry("NSCameraUsageDescription", "We use the camera to scan codes."),
            ParsedAppleEntry("CFBundleDisplayName", "Jewel"),
         ),
         parsed.entries,
      )
   }

   @Test
   fun parseAppleStringsContent_should_round_trip_standard_escapes()
   {
      val original = "Line1\nLine2\r\twith \"quotes\" and a backslash \\ end"
      val rendered = "\"key\" = \"${escapeAppleString(original)}\";"

      val parsed = parseAppleStringsContent(rendered)

      assertEquals(original, parsed.entries.single().value)
   }

   @Test
   fun parseAppleStringsContent_should_decode_unicode_escapes()
   {
      val parsed = parseAppleStringsContent("\"key\" = \"caf\\U00e9\";")

      assertEquals("café", parsed.entries.single().value)
   }

   @Test
   fun parseAppleStringsContent_should_warn_and_skip_malformed_lines()
   {
      val parsed = parseAppleStringsContent(
         """
         "good_key" = "Good";
         this is not a valid strings entry
         "another_key" = "Fine";
         """.trimIndent(),
      )

      assertEquals(listOf("good_key", "another_key"), parsed.entries.map { it.key })
      assertTrue(parsed.warnings.any { it.contains("unparseable", ignoreCase = true) })
   }

   @Test
   fun decodeAppleStrings_should_read_utf8_and_utf16_with_bom()
   {
      val content = "\"key\" = \"Café\";"

      assertEquals(content, decodeAppleStrings(content.toByteArray(Charsets.UTF_8)))

      val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + content.toByteArray(Charsets.UTF_8)
      assertEquals(content, decodeAppleStrings(utf8Bom))

      val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + content.toByteArray(Charsets.UTF_16LE)
      assertEquals(content, decodeAppleStrings(utf16LeBom))

      val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + content.toByteArray(Charsets.UTF_16BE)
      assertEquals(content, decodeAppleStrings(utf16BeBom))
   }

   @Test
   fun resolveAppleLocale_should_map_lproj_directories()
   {
      assertEquals("en", resolveAppleLocale("en.lproj", "en").locale)
      assertEquals("pt-br", resolveAppleLocale("pt-BR.lproj", "en").locale)
      assertEquals("pt-br", resolveAppleLocale("pt_BR.lproj", "en").locale)

      val base = resolveAppleLocale("Base.lproj", "en")
      assertEquals("en", base.locale)
      assertTrue(base.isBase)
      assertNull(base.warning)
   }

   @Test
   fun resolveAppleLocale_should_warn_for_unrecognized_qualifier_but_pass_through()
   {
      val resolution = resolveAppleLocale("zzInvalid.lproj", "en")

      assertEquals("zzinvalid", resolution.locale)
      assertTrue(resolution.warning.orEmpty().contains("Unrecognized"))
   }

   @Test
   fun toLprojDirectory_should_render_apple_conventional_casing()
   {
      assertEquals("en.lproj", toLprojDirectory("en"))
      assertEquals("pt-BR.lproj", toLprojDirectory("pt-br"))
      assertEquals("zh-Hans.lproj", toLprojDirectory("zh-hans"))
   }

   @Test
   fun discoverAppleLocaleFiles_should_prefer_explicit_default_locale_over_base_and_warn()
   {
      val root = createTempDirectory("translationtools-apple-base").toFile()
      writeStrings(root, "Base.lproj", "InfoPlist.strings", "\"k\" = \"From Base\";")
      writeStrings(root, "en.lproj", "InfoPlist.strings", "\"k\" = \"From en\";")

      val discovery = discoverAppleLocaleFiles(listOf(root), "en")

      val enVariant = discovery.files.single { it.locale == "en" }
      assertFalse(enVariant.isBase)
      assertTrue(enVariant.file.path.contains("en.lproj"))
      assertTrue(discovery.warnings.any { it.contains("Base.lproj") && it.contains("explicit") })
   }

   @Test
   fun parse_should_build_origin_aware_entries_per_filename()
   {
      val root = createTempDirectory("translationtools-apple-parse").toFile()
      writeStrings(root, "en.lproj", "InfoPlist.strings", "\"NSCameraUsageDescription\" = \"Camera\";")
      writeStrings(root, "de.lproj", "InfoPlist.strings", "\"NSCameraUsageDescription\" = \"Kamera\";")
      writeStrings(root, "en.lproj", "Localizable.strings", "\"home_title\" = \"Home\";")

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(listOf("de", "en"), project.locales)
      val camera = project.entries.single { it.key == "NSCameraUsageDescription" }
      assertEquals(":/InfoPlist.strings", camera.origin)
      assertEquals("Camera", camera.valuesByLocale["en"])
      assertEquals("Kamera", camera.valuesByLocale["de"])
      assertTrue(project.entries.any { it.origin == ":/Localizable.strings" && it.key == "home_title" })
   }

   @Test
   fun parse_should_be_a_noop_with_warning_when_no_lproj_directories_found()
   {
      val root = createTempDirectory("translationtools-apple-empty").toFile()

      val project = AppleStringResourceParser().parse(listOf(root), defaultLocale = "en", projectPath = ":")

      assertEquals(emptyList(), project.entries)
      assertTrue(project.warnings.any { it.contains("No .lproj directories") })
   }

   @Test
   fun mergeAppleStrings_should_update_existing_keys_in_place_and_preserve_comments()
   {
      val existing = """
         /* App permissions */
         "NSCameraUsageDescription" = "Old camera copy";

         "CFBundleDisplayName" = "Jewel";
      """.trimIndent()

      val merged = mergeAppleStrings(
         existing,
         mapOf(
            "NSCameraUsageDescription" to "New camera copy",
            "NSMicrophoneUsageDescription" to "Microphone copy",
         ),
      )

      assertTrue(merged.contains("/* App permissions */"))
      assertTrue(merged.contains("\"NSCameraUsageDescription\" = \"New camera copy\";"))
      assertFalse(merged.contains("Old camera copy"))
      assertTrue(merged.contains("\"CFBundleDisplayName\" = \"Jewel\";"))
      assertTrue(merged.contains("\"NSMicrophoneUsageDescription\" = \"Microphone copy\";"))
      // The original comment precedes the appended new key.
      assertTrue(merged.indexOf("App permissions") < merged.indexOf("NSMicrophoneUsageDescription"))
   }

   @Test
   fun mergeAppleStrings_should_keep_crlf_line_endings_when_appending_new_keys()
   {
      val existing = "/* perms */\r\n\"NSCameraUsageDescription\" = \"Old\";\r\n"

      val merged = mergeAppleStrings(
         existing,
         mapOf(
            "NSCameraUsageDescription" to "New",
            "NSMicrophoneUsageDescription" to "Mic",
         ),
      )

      assertTrue(merged.contains("\"NSMicrophoneUsageDescription\" = \"Mic\";"))
      assertFalse(merged.contains("\n\n"), "appended keys must not introduce bare-LF lines into a CRLF file")
      // Every line break is a CRLF: stripping CRLF leaves no stray LF behind.
      assertFalse(merged.replace("\r\n", "").contains("\n"))
   }

   @Test
   fun toLprojDirectory_should_render_script_and_region_for_three_part_locales()
   {
      assertEquals("zh-Hans-CN.lproj", toLprojDirectory("zh-hans-cn"))
   }

   @Test
   fun mergeAppleStrings_should_render_managed_header_for_a_fresh_file()
   {
      val merged = mergeAppleStrings(null, mapOf("k" to "v", "ignored" to null))

      assertTrue(merged.contains("Managed by TranslationTools"))
      assertTrue(merged.contains("knownRegions"))
      assertTrue(merged.contains("\"k\" = \"v\";"))
      assertFalse(merged.contains("ignored"))
   }
}

private fun writeStrings(root: File, lproj: String, fileName: String, content: String)
{
   val directory = File(root, lproj)
   directory.mkdirs()
   File(directory, fileName).writeText(content)
}
