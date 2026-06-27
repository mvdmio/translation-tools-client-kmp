package io.mvdm.translationtools.client.placeholders

/**
 * Runtime placeholder substitution engine (spec §5). Pure string substitution for Phase 1.
 *
 * Resolution per token (never a fallback chain): a binding wins (shadowing a global of the same
 * name); else a registered global resolves ambiently; else (with [knownSet] != null and the name
 * unknown) the token is an inert literal; else the managed token degrades to raw + warn.
 *
 * For the KMP string-keyed path [knownSet] is always `null` (open mode), so every unbound and
 * unregistered token degrades.
 */
internal object PlaceholderSubstitution
{
   /**
    * Substitute placeholder tokens in [value].
    *
    * @param value Raw translation value (may contain tokens).
    * @param bindings Per-call bindings; a binding shadows a global of the same name.
    * @param globals Ambient global resolver.
    * @param knownSet Generator path: `keyScopedNames ∪ declaredGlobalNames` (unknown tokens become
    *   inert literals). String-keyed path: `null`, so unbound/unregistered tokens degrade.
    * @param throwOnError When true, every warn case throws [PlaceholderSubstitutionException] instead.
    * @param warn Sink for warning messages (no-op when null).
    */
   fun substitute(
      value: String,
      bindings: Map<String, String?>?,
      globals: GlobalPlaceholderResolver,
      knownSet: Set<String>?,
      throwOnError: Boolean,
      warn: ((String) -> Unit)? = null,
   ): String
   {
      if (value.isEmpty())
         return value

      val segments = PlaceholderTokenParser.parse(value)

      val builder = StringBuilder(value.length)
      val hasBindings = !bindings.isNullOrEmpty()
      val consumedTokens = if (hasBindings) mutableSetOf<String>() else null

      for (segment in segments) {
         when (segment) {
            is PlaceholderSegment.Literal -> builder.append(segment.text)

            is PlaceholderSegment.Token -> {
               val name = segment.name

               // 1. Binding wins (shadows a global of the same name).
               if (bindings != null && bindings.containsKey(name)) {
                  builder.append(bindings[name] ?: "")
                  consumedTokens?.add(name)
                  continue
               }

               // 2. Registered global -> resolve ambiently.
               if (globals.isRegistered(name)) {
                  val resolved = globals.resolve(name)
                  if (resolved != null) {
                     builder.append(resolved)
                  }
                  else {
                     degrade(
                        builder,
                        name,
                        throwOnError,
                        warn,
                        "Could not resolve global placeholder '{$name}' (resolver failed or returned null).",
                     )
                  }
                  continue
               }

               // 3. knownSet provided and name not in it -> inert literal, no warning (US29).
               if (knownSet != null && name !in knownSet) {
                  builder.append('{').append(name).append('}')
                  continue
               }

               // 4. Managed token with no supplied value -> degrade.
               degrade(builder, name, throwOnError, warn, "No value supplied for placeholder '{$name}'.")
            }
         }
      }

      // Extra supplied binding that never appeared as a token -> warn, still succeed.
      if (bindings != null && consumedTokens != null) {
         for (key in bindings.keys) {
            if (key !in consumedTokens)
               warnOrThrow(throwOnError, warn, "Supplied placeholder '$key' is not present in the value.")
         }
      }

      return builder.toString()
   }

   private fun degrade(
      builder: StringBuilder,
      name: String,
      throwOnError: Boolean,
      warn: ((String) -> Unit)?,
      message: String,
   )
   {
      if (throwOnError)
         throw PlaceholderSubstitutionException(message)

      warn?.invoke(message)
      builder.append('{').append(name).append('}')
   }

   private fun warnOrThrow(throwOnError: Boolean, warn: ((String) -> Unit)?, message: String)
   {
      if (throwOnError)
         throw PlaceholderSubstitutionException(message)

      warn?.invoke(message)
   }
}
