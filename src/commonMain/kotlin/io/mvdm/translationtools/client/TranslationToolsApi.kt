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
}
