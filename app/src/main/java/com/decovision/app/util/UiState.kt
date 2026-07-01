package com.decovision.app.util

/**
 * Sealed class representing the state of any async UI data operation.
 * ViewModels expose [androidx.lifecycle.LiveData]<[UiState]<T>> for each data stream.
 *
 * Usage:
 * ```
 * when (state) {
 *     is UiState.Loading -> showProgress()
 *     is UiState.Success -> render(state.data)
 *     is UiState.Error   -> showError(state.message)
 * }
 * ```
 */
sealed class UiState<out T> {
    /** Indicates that an async operation is in progress. */
    object Loading : UiState<Nothing>()

    /** Indicates that an async operation completed successfully with [data]. */
    data class Success<T>(val data: T) : UiState<T>()

    /** Indicates that an async operation failed with a user-readable [message]. */
    data class Error(val message: String) : UiState<Nothing>()
}
