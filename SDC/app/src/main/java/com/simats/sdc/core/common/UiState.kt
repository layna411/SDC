package com.simats.sdc.core.common

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val isUnauthorized: Boolean = false) : UiState<Nothing>
    data object Empty : UiState<Nothing>
}

object ValidationUtils {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun isValidPhone(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return digits.length in 7..15
    }

    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun isValidAge(ageStr: String): Boolean {
        val age = ageStr.toIntOrNull() ?: return false
        return age in 0..130
    }

    fun evaluateLocalPreviewOutcome(
        lesionPresent: Boolean,
        abnormalFindingCount: Int,
        examinedFindingCount: Int,
    ): String {
        if (lesionPresent || abnormalFindingCount > 0) return "ABNORMAL"
        if (examinedFindingCount == 0) return "PENDING_CLINICAL_REVIEW"
        return "NORMAL"
    }
}
