package io.mvdm.translationtools.client

/**
 * Fluent builder for binding key-scoped placeholders before rendering a translation value.
 *
 * Obtained from [TranslationToolsClient.withPlaceholders]. Bind values with [setPlaceholder], then
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
   private val client: TranslationToolsClient,
   private val ref: TranslationRef,
   private val locale: String?,
   private val defaultValue: String?,
)
{
   private val placeholders = LinkedHashMap<String, String?>()

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
    * Fetch the translation for the configured ref/locale and apply placeholder substitution,
    * returning the rendered string.
    */
   public suspend fun render(): String
   {
      return client.get(ref, locale, defaultValue, placeholders.toMap())
   }
}
