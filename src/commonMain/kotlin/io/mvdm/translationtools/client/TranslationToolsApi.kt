package io.mvdm.translationtools.client

public interface TranslationToolsApi
{
   public suspend fun getProjectMetadata(): ProjectMetadata

   public suspend fun getLocale(locale: String): List<TranslationItem>

   public suspend fun getTranslation(locale: String, ref: TranslationRef, defaultValue: String? = null): TranslationItem

   public suspend fun sendHeartbeat(clientId: String, environment: String?, platform: String, version: String)
   {
   }

   /**
    * Push the declared global placeholder [names] for [environment] to the server (spec §6).
    * Sent as a globals-only project push: empty items (keys untouched) + a non-null globals list
    * (full-replaced for this environment). A null/empty names list is still sent as an empty array
    * so the environment's globals are cleared.
    */
   public suspend fun pushGlobals(environment: String?, names: List<String>)
   {
   }

   /**
    * Declare the Keys the running app holds, under [environment]. The [items] carry values that fill
    * only empty Translation Values on the server; an edit made in TranslationTools is never
    * overwritten. Public API addition in 3.2.0; the default body does nothing, so existing
    * implementers keep compiling.
    */
   public suspend fun declareKeys(environment: String?, items: List<TranslationDeclarationItem>)
   {
   }
}
