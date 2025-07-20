package com.dumch.giga

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class GigaChatAPI(private val auth: GigaAuth) {
    private val client = HttpClient(CIO) {
        var token = "" // get form env, or cache, or db
        val gigaKey = System.getenv("GIGA_KEY")
        gigaDefaults()
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(token, "")
                }
                refreshTokens {
                    token = auth.requestToken(gigaKey)
                    BearerTokens(token, "")
                }
            }
        }
    }

    suspend fun message(body: GigaRequest.Chat): GigaResponse.Chat =
        client.post("https://gigachat.devices.sberbank.ru/api/v1/chat/completions") {
            setBody(body)
        }.body()

    fun clear() = client.close()
}