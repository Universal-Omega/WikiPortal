package org.wikitide.wikiportal.ui.feedback

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.wikitide.wikiportal.network.FeedbackApi
import org.wikitide.wikiportal.util.AppLog
import org.wikitide.wikiportal.util.AppVersionProvider

private const val TAG = "Feedback"

enum class FeedbackCategory(val label: String) {
    BUG("Something's broken"),
    IDEA("Idea or request"),
    CONFUSING("Something felt confusing"),
    OTHER("Other"),
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
            _state.value = current.copy(errorMessage = "Add a description of what happened before sending.")
            return
        }

        _state.value = current.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val logs = if (current.includeLogs) recentLogText() else null
            val result = api.submit(
                message = current.message,
                category = current.category.label,
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
                    errorMessage = "Couldn't send that. Check your connection, or use \"Copy feedback\" and paste it in somewhere else.",
                )
            }
        }
    }

    /** Same text a submission would send, for the copy-to-clipboard fallback. */
    fun composeShareText(): String {
        val current = _state.value
        return buildString {
            appendLine("Category: ${current.category.label}")
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
