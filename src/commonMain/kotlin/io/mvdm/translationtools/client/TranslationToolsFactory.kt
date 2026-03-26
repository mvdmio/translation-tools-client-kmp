package io.mvdm.translationtools.client

import io.ktor.client.HttpClient

public object TranslationTools
{
   public fun createClient(
      httpClient: HttpClient,
      options: TranslationToolsClientOptions,
   ): TranslationToolsClient {
      return TranslationToolsClient(
         options = options,
         api = TranslationToolsHttpApi(
            httpClient = httpClient,
            apiKey = options.apiKey,
         ),
      )
   }
}
