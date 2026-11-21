package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
internal data class TranslationPushRequest(
   val items: List<TranslationPushItemRequest>,
)

@Serializable
internal data class TranslationPushItemRequest(
   val origin: String,
   val locale: String,
   val key: String,
   val value: String?,
)

@Serializable
internal data class TranslationPushResponse(
   val receivedKeyCount: Int,
   val createdKeyCount: Int,
   val updatedKeyCount: Int,
   val removedKeyCount: Int,
)

internal suspend fun pushProjectTranslations(
   client: HttpClient,
   apiKey: String,
   request: TranslationPushRequest,
   baseUrl: String = BASE_URL,
): TranslationPushResponse
{
   return executeRemoteRequest("push", ::TranslationToolsPushException) {
      val body = client.post("$baseUrl/api/v1/translations/project") {
         header(HttpHeaders.Authorization, apiKey)
         contentType(ContentType.Application.Json)
         setBody(remoteJson.encodeToString(request))
      }.requireSuccessBodyText()

      remoteJson.decodeFromString(TranslationPushResponse.serializer(), body)
   }
}

internal class TranslationToolsPushException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
