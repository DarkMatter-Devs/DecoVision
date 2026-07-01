package com.decovision.app.ui.home

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
 * ViewModel for [HomeFragment].
 * Exposes the 3 most recent designs as [LiveData]<[UiState]<[List]<[Design]>>>.
 * Never holds a Context reference.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: DesignRepository
) : ViewModel() {

    private val _recentDesigns = MutableLiveData<UiState<List<Design>>>()

    /** Observed by [HomeFragment] to display the recent-designs horizontal strip. */
    val recentDesigns: LiveData<UiState<List<Design>>> = _recentDesigns

    init {
        loadRecentDesigns()
    }

    /**
     * Collects the most recent 3 designs from Room and posts results via [_recentDesigns].
     */
    private fun loadRecentDesigns() {
        _recentDesigns.value = UiState.Loading
        viewModelScope.launch {
            repository.getRecentDesigns()
                .flowOn(Dispatchers.IO)
                .catch { e ->
                    _recentDesigns.postValue(UiState.Error(e.message ?: "Unknown error"))
                }
                .collect { designs ->
                    _recentDesigns.postValue(UiState.Success(designs))
                }
        }
    }
}
