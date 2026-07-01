package com.decovision.app.util

import android.view.View
import com.google.android.material.snackbar.Snackbar

/**
 * Extension functions used across the DecoVision UI layer.
 */

/** Shows a short Snackbar on this view with the given [message]. */
fun View.showSnackbar(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT).show()
}

/** Shows a long Snackbar on this view with the given [message]. */
fun View.showLongSnackbar(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}

/** Sets visibility to [View.VISIBLE]. */
fun View.show() { visibility = View.VISIBLE }

/** Sets visibility to [View.GONE]. */
fun View.hide() { visibility = View.GONE }

/** Toggles visibility between [View.VISIBLE] and [View.GONE]. */
fun View.toggleVisibility() {
    visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
}
