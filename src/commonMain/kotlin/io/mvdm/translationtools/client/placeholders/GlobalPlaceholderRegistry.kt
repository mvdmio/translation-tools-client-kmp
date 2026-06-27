package io.mvdm.translationtools.client.placeholders

/**
 * Resolves global placeholder values ambiently (spec §5). A global is resolved per render with no
 * per-call passing: the registered lambda is invoked each time the token is encountered.
 */
internal interface GlobalPlaceholderResolver
{
   /** All declared global placeholder names, in declaration order. */
   val registeredNames: List<String>

   /** True when [name] is a registered global placeholder. */
   fun isRegistered(name: String): Boolean

   /**
    * Try to resolve a global placeholder value.
    * Returns null (degrade) when not registered, when the resolver throws, or when it returns null.
    */
   fun resolve(name: String): String?
}

/**
 * Immutable registry of declared global placeholder names to their ambient resolver lambdas
 * (spec §2/§5/§6). Names must be valid camelCase identifiers (`[a-z][a-zA-Z0-9]*`); each lambda is
 * invoked per render. A throwing lambda or a null return degrades the token (raw + warn) by default.
 */
internal class GlobalPlaceholderRegistry private constructor(
   private val resolversByName: Map<String, () -> String?>,
   override val registeredNames: List<String>,
) : GlobalPlaceholderResolver
{
   override fun isRegistered(name: String): Boolean = resolversByName.containsKey(name)

   override fun resolve(name: String): String?
   {
      val resolver = resolversByName[name] ?: return null
      return try {
         resolver()
      }
      catch (_: Throwable) {
         // Resolver threw -> degrade.
         null
      }
   }

   companion object
   {
      /** A registry with no registered globals. */
      val Empty: GlobalPlaceholderRegistry = GlobalPlaceholderRegistry(emptyMap(), emptyList())

      /**
       * Build a registry from a map of global name -> ambient resolver. The map's iteration order
       * defines declaration order (use a [LinkedHashMap] for stable ordering). Names are validated
       * against the token grammar; duplicates are impossible (map keys are unique).
       */
      fun from(globals: Map<String, () -> String?>): GlobalPlaceholderRegistry
      {
         if (globals.isEmpty())
            return Empty

         val ordered = LinkedHashMap<String, () -> String?>(globals.size)
         for ((name, resolver) in globals) {
            require(globalNameRegex.matches(name)) {
               "Invalid global placeholder name '$name'. Names must match [a-z][a-zA-Z0-9]*."
            }
            ordered[name] = resolver
         }

         return GlobalPlaceholderRegistry(ordered, ordered.keys.toList())
      }

      private val globalNameRegex = Regex("^[a-z][a-zA-Z0-9]*$")
   }
}
