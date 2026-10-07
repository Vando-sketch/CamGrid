package io.github.vandosketch.camgrid

import android.content.Context
import android.content.pm.PackageManager

/**
 * Whether this is a TV (Fire TV, Android TV): driven by a remote, without a usable file picker.
 * Fire TV devices do not all declare leanback, so Amazon's own feature is checked too.
 */
fun Context.isTvDevice(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature("amazon.hardware.fire_tv")
