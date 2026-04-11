package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
): TranslationPushResponse
{
   return executePushRequest {
      val body = client.post("$BASE_URL/api/v1/translations/project") {
         header(HttpHeaders.Authorization, apiKey)
         contentType(ContentType.Application.Json)
         setBody(pushJson.encodeToString(request))
      }.requirePushSuccessBodyText()

      pushJson.decodeFromString(TranslationPushResponse.serializer(), body)
   }
}

internal fun createDefaultPushHttpClient(): HttpClient
{
   return HttpClient(io.ktor.client.engine.cio.CIO) {
      install(ContentNegotiation) {
         json(Json { ignoreUnknownKeys = true })
      }
   }
}

private suspend fun <T> executePushRequest(action: suspend () -> T): T
{
   try {
      return action()
   }
   catch (exception: TranslationToolsPushException) {
      throw exception
   }
   catch (exception: SerializationException) {
      throw TranslationToolsPushException("Failed to parse TranslationTools push response.", exception)
   }
   catch (exception: ClientRequestException) {
      throw TranslationToolsPushException(
         "TranslationTools push request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: ServerResponseException) {
      throw TranslationToolsPushException(
         "TranslationTools push request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: Throwable) {
      throw TranslationToolsPushException("TranslationTools push request failed: ${exception.message ?: "unknown error"}", exception)
   }
}

private suspend fun HttpResponse.requirePushSuccessBodyText(): String
{
   if (!status.isSuccess()) {
      val body = bodyAsText().trim()
      throw TranslationToolsPushException("TranslationTools request failed with status ${status.value}: $body")
   }

   return bodyAsText()
}

internal class TranslationToolsPushException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private val pushJson = Json { ignoreUnknownKeys = true }
