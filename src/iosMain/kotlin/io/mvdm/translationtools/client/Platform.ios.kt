package io.mvdm.translationtools.client

import platform.Foundation.NSLocale
import platform.Foundation.NSLog
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSUUID

internal actual fun currentPlatform(): String = "kmp-ios"

internal actual fun newClientId(): String = NSUUID().UUIDString()

internal actual fun logPlaceholderWarning(message: String) = NSLog("%@", "TranslationTools: $message")

internal actual fun formatWholeNumber(number: Long, localeTag: String): String
{
   val formatter = NSNumberFormatter()
   formatter.numberStyle = NSNumberFormatterDecimalStyle
   formatter.maximumFractionDigits = 0UL
   formatter.locale = NSLocale(localeIdentifier = localeTag.replace('-', '_'))

   return checkNotNull(formatter.stringFromNumber(NSNumber(longLong = number))) {
      "NSNumberFormatter could not format $number for '$localeTag'."
   }
}
