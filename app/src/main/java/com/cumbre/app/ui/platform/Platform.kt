package com.cumbre.app.ui.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Abre un enlace en el navegador del sistema (Cumbre no usa Internet por sí misma). */
fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No hay un navegador instalado para abrir el enlace.", Toast.LENGTH_LONG).show()
    }
}

fun Context.shareFile(uri: Uri, mime: String, title: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(Intent.createChooser(send, "Compartir «$title»").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No hay apps disponibles para compartir.", Toast.LENGTH_LONG).show()
    }
}

fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun Context.openLocationSettings() {
    runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
