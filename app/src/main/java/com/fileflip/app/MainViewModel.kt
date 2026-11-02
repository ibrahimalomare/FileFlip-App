package com.fileflip.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickedFile(val uri: Uri, val name: String, val sizeBytes: Long, val pages: Int)

data class UiState(
    val file: PickedFile? = null,
    val format: ImageFormat = ImageFormat.PNG,
    val converting: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val results: List<Uri> = emptyList(),
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private var job: Job? = null

    fun onFilePicked(uri: Uri) {
        _state.update { UiState(format = it.format) }
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val mime = ctx.contentResolver.getType(uri)
                var name = "document.pdf"
                var size = -1L
                ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                        c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                    }
                }
                if (mime != "application/pdf" && !name.endsWith(".pdf", true)) {
                    _state.update { it.copy(error = "This is not a PDF file. Please choose a .pdf file.") }
                    return@launch
                }
                val pages = PdfConverter.pageCount(ctx, uri) // also validates the file
                _state.update { it.copy(file = PickedFile(uri, name, size, pages)) }
            } catch (t: Throwable) {
                _state.update { it.copy(error = friendly(t)) }
            }
        }
    }

    fun setFormat(f: ImageFormat) = _state.update { it.copy(format = f, results = emptyList()) }

    fun convert() {
        val s = _state.value
        val file = s.file ?: return
        if (s.converting) return
        _state.update { it.copy(converting = true, done = 0, total = file.pages, results = emptyList(), error = null) }
        job = viewModelScope.launch {
            try {
                val uris = PdfConverter.convert(
                    getApplication(), file.uri, file.name.removeSuffix(".pdf").removeSuffix(".PDF"), s.format
                ) { d, t -> _state.update { it.copy(done = d, total = t) } }
                _state.update { it.copy(converting = false, results = uris) }
            } catch (e: CancellationException) {
                _state.update { it.copy(converting = false) }
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(converting = false, error = friendly(t)) }
            }
        }
    }

    fun cancel() { job?.cancel() }
    fun dismissError() = _state.update { it.copy(error = null) }

    private fun friendly(t: Throwable) = when (t) {
        is SecurityException -> "This PDF is password-protected and can't be converted."
        is OutOfMemoryError -> "A page is too large to convert on this device."
        is java.io.IOException -> "The file is damaged or isn't a valid PDF."
        else -> "Conversion failed: ${t.message ?: "unknown error"}"
    }
}
