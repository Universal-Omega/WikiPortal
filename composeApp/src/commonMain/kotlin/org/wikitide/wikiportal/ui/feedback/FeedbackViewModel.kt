package org.wikitide.wikiportal.ui.feedback

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.wikitide.wikiportal.network.FeedbackApi
import org.wikitide.wikiportal.resources.Res
import org.wikitide.wikiportal.resources.feedback_category_bug
import org.wikitide.wikiportal.resources.feedback_category_confusing
import org.wikitide.wikiportal.resources.feedback_category_idea
import org.wikitide.wikiportal.resources.feedback_category_other
import org.wikitide.wikiportal.resources.feedback_error_empty_message
import org.wikitide.wikiportal.resources.feedback_error_send_failed
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.AppVersionProvider

private const val TAG = "FeedbackViewModel"

/**
 * [apiValue] is a stable identifier sent to the feedback backend, kept in
 * English regardless of the device's language so categorization stays
 * consistent. [labelRes] is what's shown on screen, resolved with
 * stringResource by the UI.
 */
enum class FeedbackCategory(val apiValue: String, val labelRes: StringResource) {
    BUG("Something's broken", Res.string.feedback_category_bug),
    IDEA("Idea or request", Res.string.feedback_category_idea),
    CONFUSING("Something felt confusing", Res.string.feedback_category_confusing),
    OTHER("Other", Res.string.feedback_category_other),
}

@Immutable
data class FeedbackUiState(
    val category: FeedbackCategory = FeedbackCategory.BUG,
    val message: String = "",
    val displayName: String = "",
    val contact: String = "",
    val includeLogs: Boolean = true,
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
    val errorMessage: String? = null,
)

class FeedbackViewModel(
    private val api: FeedbackApi,
    private val versionProvider: AppVersionProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = _state

    fun setCategory(category: FeedbackCategory) {
        _state.value = _state.value.copy(category = category)
    }

    fun setMessage(message: String) {
        _state.value = _state.value.copy(message = message, errorMessage = null)
    }

    fun setDisplayName(displayName: String) {
        _state.value = _state.value.copy(displayName = displayName)
    }

    fun setContact(contact: String) {
        _state.value = _state.value.copy(contact = contact)
    }

    fun setIncludeLogs(include: Boolean) {
        _state.value = _state.value.copy(includeLogs = include)
    }

    fun submit() {
        val current = _state.value
        if (current.message.isBlank()) {
            viewModelScope.launch {
                _state.value = current.copy(errorMessage = getString(Res.string.feedback_error_empty_message))
            }
            return
        }

        _state.value = current.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val logs = if (current.includeLogs) recentLogText() else null
            val result = api.submit(
                message = current.message,
                category = current.category.apiValue,
                displayName = current.displayName,
                contact = current.contact,
                appVersion = versionProvider.versionName,
                logs = logs,
            )
            result.onSuccess {
                _state.value = _state.value.copy(isSubmitting = false, sent = true)
            }.onFailure {
                AppLog.e(TAG, "Couldn't submit feedback", it)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = getString(Res.string.feedback_error_send_failed),
                )
            }
        }
    }

    /** Same text a submission would send, for the copy-to-clipboard fallback. */
    suspend fun composeShareText(): String {
        val current = _state.value
        return buildString {
            appendLine("Category: ${current.category.apiValue}")
            appendLine("Version: ${versionProvider.versionName}")
            if (current.displayName.isNotBlank()) appendLine("From: ${current.displayName}")
            if (current.contact.isNotBlank()) appendLine("Contact: ${current.contact}")
            appendLine()
            appendLine(current.message)
            if (current.includeLogs) {
                appendLine()
                appendLine("--- Recent logs ---")
                appendLine(recentLogText())
            }
        }
    }

    private fun recentLogText(): String =
        AppLog.entries.value.takeLast(200).joinToString("\n") { "${it.level.name} ${it.tag}: ${it.message}" }
}
