package io.mvdm.translationtools.client

/**
 * Fluent builder for binding key-scoped placeholders before rendering a translation value.
 *
 * Obtained from [TranslationToolsClient.withPlaceholders]. Bind values with [setPlaceholder] (and the number
 * of a Plural with [setPluralNumber]), then
 * call [render] (a suspend function) to fetch the translation and apply substitution. Bindings shadow
 * globals of the same name for this render; unbound managed tokens degrade per the client options.
 *
 * ```
 * val text = client.withPlaceholders(ref)
 *    .setPlaceholder("userName", "Bob")
 *    .render()
 * ```
 */
public class TranslationRenderBuilder internal constructor(
   private val read: suspend (placeholders: Map<String, String?>, pluralNumber: Long?) -> String,
)
{
   private val placeholders = LinkedHashMap<String, String?>()
   private var pluralNumber: Long? = null

   /**
    * Bind [value] to the placeholder [name] for this render. Returns this builder for chaining.
    * A later binding of the same name overwrites the earlier one.
    */
   public fun setPlaceholder(name: String, value: String?): TranslationRenderBuilder
   {
      placeholders[name] = value
      return this
   }

   /**
    * Set the number that picks the form of a Plural value. Returns this builder for chaining.
    * The last call wins. A value that is not a Plural ignores the number.
    */
   public fun setPluralNumber(number: Long): TranslationRenderBuilder
   {
      pluralNumber = number
      return this
   }

   /**
    * Set the number that picks the form of a Plural value. Returns this builder for chaining.
    * The last call wins. A value that is not a Plural ignores the number.
    */
   public fun setPluralNumber(number: Int): TranslationRenderBuilder
   {
      return setPluralNumber(number.toLong())
   }

   /**
    * Fetch the translation for the configured ref/locale and apply placeholder substitution,
    * returning the rendered string.
    */
   public suspend fun render(): String
   {
      return read(placeholders.toMap(), pluralNumber)
   }
}
