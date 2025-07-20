package com.dumch.giga

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.X509TrustManager

fun HttpClientConfig<CIOEngineConfig>.gigaDefaults() {
    this.defaultRequest {
        header(HttpHeaders.ContentType, "application/json")
        header(HttpHeaders.Accept, "application/json")
        header("RqUID", UUID.randomUUID().toString())
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 20000
    }
    install(ContentNegotiation) {
        jackson { this.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) }
    }
    engine {
        https {
            trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        }
    }
}

