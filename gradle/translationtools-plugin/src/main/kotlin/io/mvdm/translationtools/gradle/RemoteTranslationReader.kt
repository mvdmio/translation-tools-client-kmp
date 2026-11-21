package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal const val BASE_URL = "https://translations.mvdm.io"

internal val remoteJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class TranslationItemResponse(
   val origin: String,
   val key: String,
   val value: String?,
)

/** Builds the exception a task reports for a failed request: pull and push each pass their own, so each keeps its own failure type. */
internal typealias RemoteRequestFailure = (message: String, cause: Throwable?) -> RuntimeException

/** An error status found by [requireSuccessBodyText]; [executeRemoteRequest] turns it into the caller's failure type. */
internal class RemoteStatusFailure(val status: Int, val body: String) : RuntimeException("TranslationTools request failed with status $status: $body")

/** Reads every translation TranslationTools holds in [locale], with the same `GET /api/v1/translations/{locale}` pull uses. */
internal suspend fun fetchLocaleTranslations(
   client: HttpClient,
   apiKey: String,
   locale: String,
   baseUrl: String,
   failure: RemoteRequestFailure,
): List<TranslationItemResponse>
{
   return executeRemoteRequest("locale '$locale'", failure) {
      val body = client.get("$baseUrl/api/v1/translations/$locale") {
         header(HttpHeaders.Authorization, apiKey)
      }.requireSuccessBodyText()

      remoteJson.decodeFromString(ListSerializer(TranslationItemResponse.serializer()), body)
   }
}

internal suspend fun <T> executeRemoteRequest(label: String, failure: RemoteRequestFailure, action: suspend () -> T): T
{
   try {
      return action()
   }
   catch (exception: RemoteStatusFailure) {
      throw failure("TranslationTools $label request failed with status ${exception.status}: ${exception.body}", null)
   }
   catch (exception: SerializationException) {
      throw failure("Failed to parse TranslationTools $label response. ${exception.message.orEmpty()}".trim(), exception)
   }
   catch (exception: ClientRequestException) {
      throw failure(
         "TranslationTools $label request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: ServerResponseException) {
      throw failure(
         "TranslationTools $label request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: CancellationException) {
      throw exception
   }
   catch (exception: Throwable) {
      throw failure("TranslationTools $label request failed: ${exception.message ?: "unknown error"}", exception)
   }
}

internal suspend fun HttpResponse.requireSuccessBodyText(): String
{
   if (!status.isSuccess()) {
      val body = bodyAsText().trim()
      throw RemoteStatusFailure(status.value, body)
   }

   return bodyAsText()
}

internal fun createDefaultHttpClient(): HttpClient
{
   return HttpClient(io.ktor.client.engine.cio.CIO) {
      install(ContentNegotiation) {
         json(remoteJson)
      }
   }
}
