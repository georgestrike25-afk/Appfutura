package com.cumbre.app.ui.screens.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cumbre.app.data.PendingImport
import com.cumbre.app.data.PrepareResult
import com.cumbre.app.data.RouteRepository
import com.cumbre.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Loading : ImportUiState
    data class Failed(val message: String) : ImportUiState
    data class Preview(
        val pending: PendingImport,
        val name: String,
        val sourceUrl: String,
        val saving: Boolean = false,
    ) : ImportUiState
    data class Saved(val routeId: Long, val wasDuplicate: Boolean) : ImportUiState
}

class ImportViewModel(
    private val uri: Uri?,
    private val routes: RouteRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val state: StateFlow<ImportUiState> = _state
    private var saved = false

    init {
        load()
    }

    fun load() {
        if (uri == null) {
            _state.value = ImportUiState.Failed("No se recibió ningún archivo para importar.")
            return
        }
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = when (val r = routes.prepareImport(uri)) {
                is PrepareResult.Failed -> ImportUiState.Failed(r.message)
                is PrepareResult.Ready -> {
                    val pendingUrl = settings.current().pendingWikilocUrl
                    ImportUiState.Preview(
                        pending = r.pending,
                        name = r.pending.suggestedName,
                        sourceUrl = r.pending.detectedSourceUrl ?: pendingUrl ?: r.pending.duplicateOf?.sourceUrl ?: "",
                    )
                }
            }
        }
    }

    fun setName(name: String) = _state.update { if (it is ImportUiState.Preview) it.copy(name = name.take(120)) else it }
    fun setSourceUrl(url: String) = _state.update { if (it is ImportUiState.Preview) it.copy(sourceUrl = url.trim().take(500)) else it }

    fun save() {
        val s = _state.value as? ImportUiState.Preview ?: return
        if (s.saving) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            try {
                val id = routes.confirmImport(s.pending, s.name, s.sourceUrl.ifBlank { null })
                saved = true
                settings.setPendingWikilocUrl(null)
                _state.value = ImportUiState.Saved(id, s.pending.duplicateOf != null)
            } catch (e: Exception) {
                _state.value = ImportUiState.Failed("No se pudo guardar la ruta: ${e.message ?: "error desconocido"}.")
            }
        }
    }

    override fun onCleared() {
        val s = _state.value
        if (!saved && s is ImportUiState.Preview) s.pending.tempFile.delete()
    }
}
