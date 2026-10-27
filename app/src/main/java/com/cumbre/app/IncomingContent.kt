package com.cumbre.app

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Contenido recibido desde otras apps («Abrir con» o «Compartir»), pendiente de procesar. */
sealed interface Incoming {
    data class GpxFile(val uri: Uri) : Incoming
    data class WikilocLink(val url: String) : Incoming
    data object OpenTracking : Incoming
}

class IncomingContent {
    private val _pending = MutableStateFlow<Incoming?>(null)
    val pending: StateFlow<Incoming?> = _pending.asStateFlow()

    fun offer(item: Incoming) {
        _pending.value = item
    }

    fun consume(item: Incoming) {
        if (_pending.value == item) _pending.value = null
    }
}
