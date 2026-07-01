package com.decovision.app.data.model

/**
 * Plain data class representing a furniture item placed in the AR scene.
 * Not a Room entity — serialized to JSON via Gson and stored in [Design.furnitureJson].
 *
 * // FUTURE: Replace imagePath with a glbModelPath for real 3D models once GLB assets are available.
 */
data class FurnitureItem(
    /** Unique identifier generated via UUID.randomUUID().toString() */
    val id: String,
    /** Absolute file path to the image copied into getExternalFilesDir(DIRECTORY_PICTURES) */
    val imagePath: String,
    /** AR world-space X position in meters */
    val positionX: Float = 0f,
    /** AR world-space Y position in meters */
    val positionY: Float = 0f,
    /** AR world-space Z position in meters */
    val positionZ: Float = 0f,
    /** Rotation around the Y-axis in degrees */
    val rotationY: Float = 0f,
    /** Uniform scale factor applied to the node */
    val scale: Float = 1f
)
