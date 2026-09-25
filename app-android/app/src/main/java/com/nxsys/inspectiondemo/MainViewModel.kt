package com.nxsys.inspectiondemo

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nxsys.inspectiondemo.storage.InspectionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val folderName: String? = null,
    val folderChecked: Boolean = false,
    val sn: String = "",
    val photos: List<Uri> = emptyList(),
    val scannerAvailable: Boolean = true,
    val busy: Boolean = false,
    val canRetry: Boolean = false,
) {
    val hasFolder get() = folderName != null
    // An unsaved photo (canRetry) must be saved or explicitly discarded before anything else.
    val canTakePhoto get() = hasFolder && !busy && !canRetry && Naming.sanitizeSn(sn) != null
    val snLocked get() = busy || canRetry
    val canFinish get() = !busy
    val canScan get() = scannerAvailable && !snLocked
}

class MainViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val storage = InspectionStorage(application)
    private val cameraDir = File(application.cacheDir, "camera")

    // SN and the pending photo survive process death while the camera app is open.
    private var sn: String
        get() = savedState[KEY_SN] ?: ""
        set(value) { savedState[KEY_SN] = value }
    private var pendingPhotoPath: String?
        get() = savedState[KEY_PENDING]
        set(value) { savedState[KEY_PENDING] = value }
    // The SN the pending photo was taken for, so a retry can never file it under another SN.
    private var pendingPhotoSn: String?
        get() = savedState[KEY_PENDING_SN]
        set(value) { savedState[KEY_PENDING_SN] = value }

    private val _uiState = MutableStateFlow(UiState(sn = sn))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        cleanStaleTempFiles()
        refreshFolder()
        loadPhotos()
        // Returned from the camera after process death: result arrives via onPhotoResult.
        _uiState.update { it.copy(canRetry = pendingPhotoPath?.let(::File)?.exists() == true) }
    }

    fun refreshFolder() {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { storage.folderDisplayName() }
            _uiState.update { it.copy(folderName = name, folderChecked = true) }
        }
    }

    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { storage.setFolder(uri) }
            refreshFolder()
            loadPhotos()
        }
    }

    fun onSnChanged(text: String) {
        if (_uiState.value.snLocked) return
        sn = text
        _uiState.update { it.copy(sn = text, photos = emptyList()) }
    }

    fun onSnSubmitted() = loadPhotos()

    fun onScanned(value: String) {
        if (_uiState.value.snLocked) return
        onSnChanged(value.trim())
        loadPhotos()
    }

    fun setScannerAvailable(available: Boolean) {
        _uiState.update { it.copy(scannerAvailable = available) }
    }

    /** Creates an empty temp file for the camera app to write into and returns its content Uri. */
    fun createPhotoTarget(): Uri {
        discardPendingPhoto()
        cameraDir.mkdirs()
        val file = File.createTempFile("photo_", ".jpg", cameraDir)
        pendingPhotoPath = file.absolutePath
        pendingPhotoSn = sn
        val app = getApplication<Application>()
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }

    fun onPhotoResult(success: Boolean) {
        val file = pendingPhotoPath?.let(::File) ?: return
        when {
            !success -> discardPendingPhoto()
            !file.exists() || file.length() == 0L -> {
                discardPendingPhoto()
                _messages.trySend(R.string.photo_empty)
            }
            else -> save(file)
        }
    }

    fun onCameraUnavailable() {
        discardPendingPhoto()
        _messages.trySend(R.string.camera_unavailable)
    }

    fun retrySave() {
        val file = pendingPhotoPath?.let(::File)?.takeIf { it.exists() }
        if (file == null) {
            discardPendingPhoto()
            return
        }
        save(file)
    }

    /** Next product. Discards an unsaved photo, so the UI confirms first when [UiState.canRetry]. */
    fun onDone() {
        if (_uiState.value.busy) return
        discardPendingPhoto()
        onSnChanged("")
    }

    private fun save(file: File) {
        val currentSn = pendingPhotoSn ?: sn
        _uiState.update { it.copy(busy = true, canRetry = false) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { storage.savePhoto(currentSn, file) }
            }
            result.onSuccess {
                file.delete()
                pendingPhotoPath = null
                pendingPhotoSn = null
                _uiState.update { it.copy(busy = false) }
                _messages.trySend(R.string.saved)
                loadPhotos()
            }.onFailure { e ->
                _uiState.update { it.copy(busy = false, canRetry = true) }
                if (e is InspectionStorage.FolderUnavailableException) {
                    refreshFolder()
                    _messages.trySend(R.string.folder_lost)
                } else {
                    _messages.trySend(R.string.save_failed)
                }
            }
        }
    }

    private fun loadPhotos() {
        val currentSn = sn
        viewModelScope.launch {
            val photos = withContext(Dispatchers.IO) { storage.listPhotos(currentSn) }
            // Ignore a stale result if the SN changed while loading.
            if (sn == currentSn) _uiState.update { it.copy(photos = photos) }
        }
    }

    private fun discardPendingPhoto() {
        pendingPhotoPath?.let { File(it).delete() }
        pendingPhotoPath = null
        pendingPhotoSn = null
        _uiState.update { it.copy(canRetry = false) }
    }

    private fun cleanStaleTempFiles() {
        val keep = pendingPhotoPath
        cameraDir.listFiles()?.filter { it.absolutePath != keep }?.forEach { it.delete() }
    }

    private companion object {
        const val KEY_SN = "sn"
        const val KEY_PENDING = "pending_photo"
        const val KEY_PENDING_SN = "pending_photo_sn"
    }
}
