package io.mvdm.translationtools.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidFormatSpecifierDetectorTests
{
   @Test
   fun findAndroidFormatSpecifiers_should_detect_positional_string()
   {
      assertEquals(listOf("%1\$s"), findAndroidFormatSpecifiers("Hello %1\$s"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_detect_positional_integer()
   {
      assertEquals(listOf("%2\$d"), findAndroidFormatSpecifiers("%2\$d items"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_detect_positional_float_with_precision()
   {
      assertEquals(listOf("%1\$.2f"), findAndroidFormatSpecifiers("Total %1\$.2f"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_detect_positional_with_flag_and_width()
   {
      assertEquals(listOf("%3\$05d"), findAndroidFormatSpecifiers("Id %3\$05d"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_detect_plain_string_integer_and_float()
   {
      assertEquals(listOf("%s"), findAndroidFormatSpecifiers("Hello %s"))
      assertEquals(listOf("%d"), findAndroidFormatSpecifiers("%d items"))
      assertEquals(listOf("%f"), findAndroidFormatSpecifiers("Total %f"))
      assertEquals(listOf("%.2f"), findAndroidFormatSpecifiers("Total %.2f"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_list_two_specifiers_in_order_of_appearance()
   {
      assertEquals(listOf("%1\$s", "%2\$d"), findAndroidFormatSpecifiers("%1\$s has %2\$d items"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_list_a_repeated_specifier_once()
   {
      assertEquals(listOf("%1\$s"), findAndroidFormatSpecifiers("%1\$s and %1\$s"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_treat_positional_and_plain_as_distinct_specifiers()
   {
      assertEquals(listOf("%1\$s", "%s"), findAndroidFormatSpecifiers("%1\$s then %s"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_ignore_escaped_percent()
   {
      assertEquals(emptyList(), findAndroidFormatSpecifiers("50%% off"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_read_escaped_percent_followed_by_s_as_literal()
   {
      assertEquals(emptyList(), findAndroidFormatSpecifiers("%%s"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_ignore_percent_followed_by_space()
   {
      assertEquals(emptyList(), findAndroidFormatSpecifiers("50% off"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_ignore_trailing_percent()
   {
      assertEquals(emptyList(), findAndroidFormatSpecifiers("Save 100%"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_ignore_placeholders_and_escaped_braces()
   {
      assertEquals(emptyList(), findAndroidFormatSpecifiers("Hello {userName}"))
      assertEquals(emptyList(), findAndroidFormatSpecifiers("Literal '{' brace"))
   }

   @Test
   fun findAndroidFormatSpecifiers_should_return_nothing_for_a_plain_value()
   {
      assertEquals(emptyList(), findAndroidFormatSpecifiers("Home"))
   }
}
