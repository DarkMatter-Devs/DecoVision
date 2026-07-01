package com.decovision.app.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Utility object for checking and requesting runtime permissions required by DecoVision.
 */
object PermissionHelper {

    private const val CAMERA_PERMISSION_CODE = 1001
    private const val STORAGE_PERMISSION_CODE = 1002

    /**
     * Returns true if the CAMERA permission has already been granted.
     */
    fun hasCameraPermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Requests the CAMERA permission from the user at runtime.
     */
    fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    /**
     * Returns true if the storage read permission is granted.
     * On API 33+ checks READ_MEDIA_IMAGES; below that checks READ_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(activity: Activity): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                activity, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Requests storage read permission appropriate for the running Android version.
     */
    fun requestStoragePermission(activity: Activity) {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        ActivityCompat.requestPermissions(activity, arrayOf(permission), STORAGE_PERMISSION_CODE)
    }

    /** Returns true if the user should be shown a permission rationale dialog. */
    fun shouldShowCameraRationale(activity: Activity): Boolean =
        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}
