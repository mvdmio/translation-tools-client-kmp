package io.mvdm.translationtools.gradle

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * An Apple String Catalog (`.xcstrings`) read from [path]: the [keys] it holds, its [sourceLanguageName] as the file spells it,
 * and the parsed JSON [root] with its [strings] object, where every field the plugin does not act on stays untouched.
 */
internal class AppleStringCatalog(
   val path: String,
   val root: JsonObject,
   val strings: JsonObject,
   val sourceLanguageName: String,
   val keys: List<AppleCatalogKey>,
)
{
   /** The source language as a normalized Locale. */
   val sourceLanguage: String = normalizeAppleLocale(sourceLanguageName)

   /** The source language plus every Locale in any Key's localizations, including Keys marked "Don't translate". */
   val locales: Set<String> = keys.flatMapTo(linkedSetOf(sourceLanguage)) { it.localizations.keys }

   /** The value push sends for [key] in [locale] (normalized); the source language falls back to the Key text. */
   fun pushValue(key: AppleCatalogKey, locale: String): CatalogPushValue
   {
      return when (val localization = key.localizations[locale]) {
         is AppleCatalogLocalization.Plain -> CatalogPushValue.Value(localization.value)
         is AppleCatalogLocalization.WholePlural -> pluralPushValue("count", localization.forms, NumberSpecifiers.WHOLE_VALUE)
         is AppleCatalogLocalization.SubstitutionPlural ->
            pluralPushValue(localization.name, localization.forms, NumberSpecifiers.substitution(localization.formatSpecifier))
         is AppleCatalogLocalization.Unsyncable -> CatalogPushValue.Skipped("Skipped '${key.key}' for '$locale' in '$path': ${localization.reason.warning}")
         AppleCatalogLocalization.Empty, null -> if (locale == sourceLanguage) CatalogPushValue.Value(key.key) else CatalogPushValue.NoValue
      }
   }
}

/**
 * The format specifiers (the part after `%` and an optional position) that stand for the number in a Plural form. A number
 * token is written as `%` plus its body: a specifier such as `lld`, or `arg` where [hasArgToken].
 */
internal class NumberSpecifiers private constructor(private val specifiers: Set<String>, private val hasArgToken: Boolean)
{
   /** The body of the number token a form of this shape writes when it holds none yet: `lld` in the whole-value shape, else `arg`. */
   val defaultBody: String = if (hasArgToken) "arg" else "lld"

   /** The body of the first number token in [forms], or null when none holds one. */
   fun bodyIn(forms: List<AndroidPluralItem>): String? =
      forms.firstNotNullOfOrNull { form -> numberToken.findAll(form.text).firstNotNullOfOrNull { bodyOf(it) } }

   /** The body of [token] when it stands for the number, else null. */
   fun bodyOf(token: MatchResult): String?
   {
      val specifier = token.groups["spec"]?.value
      return when {
         specifier != null -> specifier.takeIf { it in specifiers }
         hasArgToken && token.value != "%%" -> "arg"
         else -> null
      }
   }

   companion object
   {
      /** The whole-value shape: any integer specifier. */
      val WHOLE_VALUE = NumberSpecifiers(setOf("d", "i", "u", "ld", "li", "lu", "lld", "lli", "llu"), hasArgToken = false)

      /** The substitution shape: `%arg`, or exactly [formatSpecifier]. */
      fun substitution(formatSpecifier: String?) = NumberSpecifiers(setOfNotNull(formatSpecifier), hasArgToken = true)
   }
}

/** `%arg`, with an optional position, or any Apple specifier. */
private val numberToken = Regex("%(?:\\d+\\$)?arg|${appleSpecifier.pattern}")

private fun pluralPushValue(argument: String, forms: List<AndroidPluralItem>, number: NumberSpecifiers): CatalogPushValue
{
   if (forms.isEmpty())
      return CatalogPushValue.NoValue

   return CatalogPushValue.Value(composePluralValue(argument, forms.map { AndroidPluralItem(it.quantity, it.text.numberToHash(number)) }))
}

/** Replaces each specifier that stands for the number with `#`; every other specifier and `%%` stay as written. */
private fun String.numberToHash(number: NumberSpecifiers): String =
   numberToken.replace(this) { match -> if (number.bodyOf(match) != null) "#" else match.value }

/** One Key of a catalog; [localizations] is keyed by normalized Locale. */
internal data class AppleCatalogKey(
   val key: String,
   val shouldTranslate: Boolean,
   val comment: String?,
   val extractionState: String?,
   val localizations: Map<String, AppleCatalogLocalization>,
)

internal sealed interface AppleCatalogLocalization
{
   /** A `stringUnit` with a [value] and no `variations` or `substitutions`; [state] is its review state. */
   data class Plain(val state: String?, val value: String) : AppleCatalogLocalization

   /** It varies by plural as a whole; [forms] are in file order and empty when no category holds a readable value. */
   data class WholePlural(val forms: List<AndroidPluralItem>) : AppleCatalogLocalization

   /**
    * Its value is exactly `%#@name@` and the substitution [name] varies by plural; [substitution] is that substitution's JSON
    * object and [formatSpecifier] the specifier of its number.
    */
   data class SubstitutionPlural(
      val name: String,
      val substitution: JsonObject,
      val formatSpecifier: String?,
      val forms: List<AndroidPluralItem>,
   ) : AppleCatalogLocalization

   /** A value that push cannot send as a Plural or as plain text, for [reason]. */
   data class Unsyncable(val reason: UnsyncableReason) : AppleCatalogLocalization

   /** The Locale is listed but holds no value: no `stringUnit` with a string `value`, and nothing that varies. */
   data object Empty : AppleCatalogLocalization
}

/** The forms of a value that varies by plural, in file order; empty for any other value. */
internal fun AppleCatalogLocalization.pluralForms(): List<AndroidPluralItem> =
   when (this) {
      is AppleCatalogLocalization.WholePlural -> forms
      is AppleCatalogLocalization.SubstitutionPlural -> forms
      is AppleCatalogLocalization.Plain, is AppleCatalogLocalization.Unsyncable, AppleCatalogLocalization.Empty -> emptyList()
   }

internal enum class UnsyncableReason(val warning: String)
{
   NOT_A_PLURAL("only a value that varies by plural as a whole, or is exactly one plural substitution such as '%#@items@', syncs as a Plural."),
   DEVICE_OR_NESTED("only plain text and values that vary by plural sync."),
}

internal sealed interface ParsedAppleStringCatalog
{
   data class Readable(val catalog: AppleStringCatalog) : ParsedAppleStringCatalog

   /** A file that is not a JSON object with a `strings` object and a non-empty `sourceLanguage`; [detail] is why the JSON failed to parse, if it did. */
   data class Unreadable(val warning: String, val detail: String? = null) : ParsedAppleStringCatalog
}

/** [path] only names the file in the warning; [content] is its text. */
internal fun parseAppleStringCatalog(content: String, path: String): ParsedAppleStringCatalog
{
   var detail: String? = null
   val root = try {
      Json.parseToJsonElement(content) as? JsonObject
   }
   catch (exception: SerializationException) {
      detail = "Could not parse '$path' as JSON: ${exception.message}"
      null
   }

   val sourceLanguage = root?.stringMember("sourceLanguage")?.takeIf { it.isNotEmpty() }
   val strings = root?.get("strings") as? JsonObject
   if (root == null || sourceLanguage == null || strings == null)
      return ParsedAppleStringCatalog.Unreadable("Skipped '$path': it is not a readable String Catalog.", detail)

   val keys = strings.map { (key, node) -> readCatalogKey(key, node as? JsonObject ?: JsonObject(emptyMap())) }
   return ParsedAppleStringCatalog.Readable(AppleStringCatalog(path, root, strings, sourceLanguage, keys))
}

private fun readCatalogKey(key: String, node: JsonObject): AppleCatalogKey
{
   val localizations = (node["localizations"] as? JsonObject).orEmpty().entries.associate { (locale, localization) ->
      normalizeAppleLocale(locale) to readCatalogLocalization(localization as? JsonObject)
   }

   return AppleCatalogKey(
      key = key,
      shouldTranslate = (node["shouldTranslate"] as? JsonPrimitive)?.booleanOrNull ?: true,
      comment = node.stringMember("comment"),
      extractionState = node.stringMember("extractionState"),
      localizations = localizations,
   )
}

private fun readCatalogLocalization(node: JsonObject?): AppleCatalogLocalization
{
   if (node == null)
      return AppleCatalogLocalization.Empty
   if ("variations" in node)
      return readPluralVariations(node["variations"])

   val unit = node["stringUnit"] as? JsonObject ?: return AppleCatalogLocalization.Empty
   val value = unit.stringMember("value") ?: return AppleCatalogLocalization.Empty
   val substitutions = node["substitutions"] as? JsonObject
   if (substitutions == null || !wholeVariableFormat.containsMatchIn(value))
      return AppleCatalogLocalization.Plain(state = unit.stringMember("state"), value = value)

   return readSubstitutionPlural(value, substitutions)
}

/** `variations` that hold only a `plural` map whose forms nest no variation; a device variation or a nested one is unsyncable. */
private fun readPluralVariations(variations: JsonElement?): AppleCatalogLocalization
{
   val variationsByKind = variations as? JsonObject ?: return AppleCatalogLocalization.Empty
   if (variationsByKind.keys.any { it != "plural" })
      return AppleCatalogLocalization.Unsyncable(UnsyncableReason.DEVICE_OR_NESTED)

   val plural = variationsByKind["plural"] as? JsonObject ?: return AppleCatalogLocalization.Empty
   if (plural.values.any { (it as? JsonObject)?.containsKey("variations") == true })
      return AppleCatalogLocalization.Unsyncable(UnsyncableReason.DEVICE_OR_NESTED)

   val forms = plural.mapNotNull { (category, form) ->
      val quantity = PluralQuantity.entries.firstOrNull { it.keyword == category } ?: return@mapNotNull null
      ((form as? JsonObject)?.get("stringUnit") as? JsonObject)?.stringMember("value")?.let { AndroidPluralItem(quantity, it) }
   }

   return AppleCatalogLocalization.WholePlural(forms)
}

/** [value] holds at least one `%#@name@`: it syncs only when it is exactly one and that substitution varies by plural. */
private fun readSubstitutionPlural(value: String, substitutions: JsonObject): AppleCatalogLocalization
{
   val referenced = wholeVariableFormat.findAll(value).map { it.groupValues[1] }.associateWith { name ->
      val substitution = substitutions[name] as? JsonObject
      readPluralVariations(substitution?.get("variations")) to substitution
   }

   if (referenced.values.any { (variations, _) -> variations is AppleCatalogLocalization.Unsyncable })
      return AppleCatalogLocalization.Unsyncable(UnsyncableReason.DEVICE_OR_NESTED)

   val name = wholeVariableFormat.matchEntire(value)?.groupValues?.get(1)
      ?: return AppleCatalogLocalization.Unsyncable(UnsyncableReason.NOT_A_PLURAL)
   val (variations, substitution) = referenced.getValue(name)
   if (variations !is AppleCatalogLocalization.WholePlural || substitution == null)
      return AppleCatalogLocalization.Unsyncable(UnsyncableReason.NOT_A_PLURAL)

   return AppleCatalogLocalization.SubstitutionPlural(name, substitution, substitution.stringMember("formatSpecifier"), variations.forms)
}

internal fun JsonObject.stringMember(name: String): String? =
   (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

/** What push sends for one Key and Locale of a catalog. */
internal sealed interface CatalogPushValue
{
   data class Value(val text: String) : CatalogPushValue

   /** Sent as `null`, which TranslationTools reads as "keep the stored value". */
   data object NoValue : CatalogPushValue

   /** Sent as `null` after [warning]. */
   data class Skipped(val warning: String) : CatalogPushValue
}
