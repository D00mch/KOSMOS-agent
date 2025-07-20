package com.dumch.giga

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*

object GigaAuth {
    suspend fun requestToken(apiKey: String): String {
        val client = HttpClient(CIO) {
            gigaDefaults()
        }
        val response = client.submitForm(
            url = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
            formParameters = Parameters.build {
                append("scope", "GIGACHAT_API_PERS")
            }
        ) {
            header("Content-Type", "application/x-www-form-urlencoded")
            header("Authorization", "Basic $apiKey")
        }.body<GigaResponse.Token>()

        client.close()
        return response.accessToken
    }
}