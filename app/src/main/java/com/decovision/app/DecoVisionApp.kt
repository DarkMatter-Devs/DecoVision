package com.decovision.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class annotated with @HiltAndroidApp to enable Hilt dependency injection.
 * This is the entry point for the entire Hilt component hierarchy.
 */
@HiltAndroidApp
class DecoVisionApp : Application()
