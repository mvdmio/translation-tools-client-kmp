package io.mvdm.translationtools.gradle

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

internal class MockTranslationToolsServer(
   private val projectResponse: String = """{"locales":["en"],"defaultLocale":"en"}""",
   private val localeResponses: Map<String, String> = mapOf(
      "en" to """[{"origin":":/strings.xml","key":"home_title","value":"Home"}]""",
   ),
   private val pushResponse: String =
      """{"receivedKeyCount":1,"createdKeyCount":1,"updatedKeyCount":0,"removedKeyCount":0}""",
) : AutoCloseable
{
   private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
   private val executor = Executors.newCachedThreadPool()

   val pushBodies = mutableListOf<String>()
   val baseUrl: String

   init
   {
      server.executor = executor
      server.createContext("/api/v1/translations/project") { exchange ->
         val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
         when (exchange.requestMethod)
         {
            "GET" -> writeResponse(exchange, 200, projectResponse)
            "POST" ->
            {
               pushBodies += body
               writeResponse(exchange, 200, pushResponse)
            }
            else -> writeResponse(exchange, 405, "Method not allowed")
         }
      }
      localeResponses.forEach { (locale, response) ->
         server.createContext("/api/v1/translations/$locale") { exchange ->
            if (exchange.requestMethod == "GET")
               writeResponse(exchange, 200, response)
            else
               writeResponse(exchange, 405, "Method not allowed")
         }
      }
      server.start()
      baseUrl = "http://127.0.0.1:${server.address.port}"
   }

   override fun close()
   {
      server.stop(0)
      executor.shutdownNow()
   }

   private fun writeResponse(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String)
   {
      val bytes = body.toByteArray(Charsets.UTF_8)
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(status, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
   }
}
