package com.decovision.app.ui.designs

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.decovision.app.data.model.Design
import com.decovision.app.data.repository.DesignRepository
import com.decovision.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [MyDesignsFragment].
 * Exposes all saved designs and handles delete operations.
 * Never holds a Context reference.
 */
@HiltViewModel
class MyDesignsViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    private val _designs = MutableLiveData<UiState<List<Design>>>()

    /** Observed by [MyDesignsFragment] to populate the designs grid. */
    val designs: LiveData<UiState<List<Design>>> = _designs

    private val _deleteState = MutableLiveData<UiState<Unit>>()
    /** Observed by [MyDesignsFragment] to confirm delete success or show errors. */
    val deleteState: LiveData<UiState<Unit>> = _deleteState

    init {
        loadAllDesigns()
    }

    /**
     * Collects all designs from Room and posts results via [_designs].
     * Emits a new list reactively whenever the table changes.
     */
    private fun loadAllDesigns() {
        _designs.value = UiState.Loading
        viewModelScope.launch {
            repository.getAllDesigns()
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    _designs.postValue(UiState.Error(e.message ?: "Failed to load designs"))
                }
                .collect { list ->
                    _designs.postValue(UiState.Success(list))
                }
        }
    }

    /**
     * Permanently deletes the given [design] from the database.
     * Posts [UiState.Success] on completion or [UiState.Error] on failure.
     */
    fun deleteDesign(design: Design) {
        _deleteState.value = UiState.Loading
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.deleteDesign(design)
                }
                _deleteState.postValue(UiState.Success(Unit))
            } catch (e: Exception) {
                _deleteState.postValue(UiState.Error(e.message ?: "Failed to delete design"))
            }
        }
    }
}
