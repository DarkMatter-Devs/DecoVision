package com.decovision.app.ui.ar

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.decovision.app.data.model.Design
import com.decovision.app.data.model.FurnitureItem
import com.decovision.app.data.repository.DesignRepository
import com.decovision.app.util.UiState
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for [ArFragment].
 * Manages the list of placed [FurnitureItem] objects and handles saving designs to Room.
 * Never holds a Context reference — file paths are passed in as Strings.
 */
@HiltViewModel
class ArViewModel @Inject constructor(
    private val repository: DesignRepository,
    private val gson: Gson
) : ViewModel() {

    private val _saveState = MutableLiveData<UiState<Unit>>()
    /** Observed by [ArFragment] to show save success/failure feedback. */
    val saveState: LiveData<UiState<Unit>> = _saveState

    /** In-memory list of furniture items placed in the current AR session. */
    private val _placedItems = mutableListOf<FurnitureItem>()
    val placedItems: List<FurnitureItem> get() = _placedItems.toList()

    /**
     * Creates a new [FurnitureItem] with the given [imagePath] and appends it to the placed list.
     * @return the newly created [FurnitureItem]
     */
    fun addFurnitureItem(imagePath: String): FurnitureItem {
        val item = FurnitureItem(
            id = UUID.randomUUID().toString(),
            imagePath = imagePath
        )
        _placedItems.add(item)
        return item
    }

    /**
     * Removes a [FurnitureItem] with the given [itemId] from the placed list.
     */
    fun removeFurnitureItem(itemId: String) {
        _placedItems.removeAll { it.id == itemId }
    }

    /**
     * Saves the current AR session as a [Design] to Room.
     * @param designName the name the user gave this design
     * @param thumbnailPath absolute path to the saved thumbnail image
     */
    fun saveDesign(designName: String, thumbnailPath: String) {
        _saveState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val furnitureJson = gson.toJson(_placedItems)
                val design = Design(
                    name = designName,
                    thumbnailPath = thumbnailPath,
                    furnitureJson = furnitureJson,
                    createdAt = System.currentTimeMillis()
                )
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    repository.insertDesign(design)
                }
                _saveState.postValue(UiState.Success(Unit))
            } catch (e: Exception) {
                _saveState.postValue(UiState.Error(e.message ?: "Failed to save design"))
            }
        }
    }

    /** Clears the placed items list for a new AR session. */
    fun clearSession() {
        _placedItems.clear()
    }
}
