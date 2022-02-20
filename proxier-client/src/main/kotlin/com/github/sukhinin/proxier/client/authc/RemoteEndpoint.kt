package com.github.sukhinin.proxier.client.authc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.sukhinin.proxier.http.HttpUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object RemoteEndpoint {

    private val objectMapper = ObjectMapper()
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun call(address: String, params: Map<String, String>): JsonNode {
        val req = HttpRequest.newBuilder(URI.create(address))
            .POST(HttpRequest.BodyPublishers.ofString(HttpUtils.formEncode(params)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(5))
            .build()

        val res = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray())
        if (res.statusCode() !in 200..299) {
            throw AuthenticationException(getErrorMessage(res))
        }

        return objectMapper.readTree(res.body())
    }

    private fun getErrorMessage(res: HttpResponse<ByteArray>): String {
        return try {
            val json = objectMapper.readTree(res.body())
            val error = requireNotNull(json.path("error").textValue())
            val description = json.path("error_description").textValue()
            if (description == null) error else "$description ($error)"
        } catch (e: Exception) {
            "Bad HTTP response: code ${res.statusCode()}"
        }
    }

}
