package org.wikitide.wikiportal.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.wikitide.wikiportal.BuildKonfig
import org.wikitide.wikiportal.util.AppLog

private const val TAG = "HttpClient"

private val mediaWikiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Shared client configuration applied on top of whichever engine each
 * platform picks. See the platform-specific createHttpClient() actuals.
 */
fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureMediaWikiClient() {
    expectSuccess = false
    install(ContentNegotiation) { json(mediaWikiJson) }
    install(HttpCache)
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 15_000
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                AppLog.d(TAG, message)
            }
        }
        level = LogLevel.INFO
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, "WikiPortal/${BuildKonfig.VERSION_NAME} (https://wikiportal.app)")
    }
}

expect fun createHttpClient(): HttpClient
