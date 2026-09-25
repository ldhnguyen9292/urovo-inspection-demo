package com.nxsys.inspectiondemo

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nxsys.inspectiondemo.scanner.ScannerController

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var scanner: ScannerController

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        viewModel.onFolderPicked(it)
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) {
        viewModel.onPhotoResult(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scanner = ScannerController(this)

        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    viewModel.messages.collect { snackbarHostState.showSnackbar(getString(it)) }
                }

                // Open the folder picker once automatically on first run.
                var autoPrompted by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(state.folderChecked, state.hasFolder) {
                    if (state.folderChecked && !state.hasFolder && !autoPrompted) {
                        autoPrompted = true
                        launchFolderPicker()
                    }
                }

                MainScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onChooseFolder = ::launchFolderPicker,
                    onSnChanged = viewModel::onSnChanged,
                    onSnSubmitted = viewModel::onSnSubmitted,
                    onTakePhoto = ::launchCamera,
                    onRetry = viewModel::retrySave,
                    onDone = viewModel::onDone,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshFolder()
        viewModel.setScannerAvailable(scanner.start(viewModel::onScanned))
    }

    override fun onPause() {
        scanner.stop()
        super.onPause()
    }

    private fun launchFolderPicker() {
        // Start in Documents/ so the user can create "Inspection" there.
        val initial = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Documents"
        )
        pickFolder.launch(initial)
    }

    private fun launchCamera() {
        try {
            takePicture.launch(viewModel.createPhotoTarget())
        } catch (e: ActivityNotFoundException) {
            viewModel.onCameraUnavailable()
        }
    }
}
