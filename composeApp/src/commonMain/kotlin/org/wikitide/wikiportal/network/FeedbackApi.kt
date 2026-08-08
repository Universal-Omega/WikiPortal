package org.wikitide.wikiportal.network

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.runCatchingCancellable

private const val TAG = "FeedbackApi"

object FeedbackConfig {
    const val FORM_URL = "https://docs.google.com/forms/d/e/1FAIpQLScrmwsQSFFSz9J6Ewd9sfTw6yoMtlmlqeMo5vYFjgDJZTdlAg/formResponse"
    const val ENTRY_MESSAGE = "entry.1812969761"
    const val ENTRY_CATEGORY = "entry.689025891"
    const val ENTRY_DISPLAY_NAME = "entry.104535048"
    const val ENTRY_CONTACT = "entry.403657016"
    const val ENTRY_VERSION = "entry.26269742"
    const val ENTRY_LOGS = "entry.1783738155"
}

class FeedbackApi(private val httpClient: HttpClient) {

    suspend fun submit(
        message: String,
        category: String,
        displayName: String,
        contact: String,
        appVersion: String,
        logs: String?,
    ): Result<Unit> = runCatchingCancellable {
        val fields = buildMap {
            put(FeedbackConfig.ENTRY_MESSAGE, message)
            put(FeedbackConfig.ENTRY_CATEGORY, category)
            put(FeedbackConfig.ENTRY_DISPLAY_NAME, displayName)
            put(FeedbackConfig.ENTRY_CONTACT, contact)
            put(FeedbackConfig.ENTRY_VERSION, appVersion)
            if (!logs.isNullOrBlank()) put(FeedbackConfig.ENTRY_LOGS, logs)
        }
        val response: HttpResponse = httpClient.post(FeedbackConfig.FORM_URL) {
            setBody(FormDataContent(Parameters.build { fields.forEach { (key, value) -> append(key, value) } }))
        }
        // Google answers a successful submission with its own HTML
        // confirmation page rather than a small 2xx with an empty
        // body, so this only checks that something other than a
        // server or routing error came back, not the exact status.
        if (response.status.value >= 400) error("HTTP ${response.status.value} from feedback form")
    }.onFailure {
        AppLog.e(TAG, "submit() failed", it)
    }
}
