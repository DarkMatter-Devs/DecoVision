package com.decovision.app.data.db

import androidx.room.TypeConverter
import com.decovision.app.data.model.FurnitureItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverter — NO @ProvidedTypeConverter, NO @Inject constructor.
 * Plain class so Room can instantiate it directly without Hilt involvement.
 */
class FurnitureConverter {

    private val gson = Gson()

    @TypeConverter
    fun fromList(list: List<FurnitureItem>): String = gson.toJson(list)

    @TypeConverter
    fun toList(json: String): List<FurnitureItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<FurnitureItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
