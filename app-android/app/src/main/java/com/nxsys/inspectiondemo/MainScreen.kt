package com.nxsys.inspectiondemo

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    snackbarHostState: SnackbarHostState,
    onChooseFolder: () -> Unit,
    onSnChanged: (String) -> Unit,
    onSnSubmitted: () -> Unit,
    onTakePhoto: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val focusManager = LocalFocusManager.current
        var confirmDiscard by rememberSaveable { mutableStateOf(false) }

        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                title = { Text(stringResource(R.string.discard_title)) },
                text = { Text(stringResource(R.string.discard_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDiscard = false
                        onDone()
                    }) { Text(stringResource(R.string.discard_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FolderCard(state, onChooseFolder)

            if (!state.scannerAvailable) {
                Text(
                    stringResource(R.string.scanner_unavailable),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = state.sn,
                onValueChange = onSnChanged,
                label = { Text(stringResource(R.string.serial_number)) },
                placeholder = { Text(stringResource(R.string.scan_hint)) },
                singleLine = true,
                readOnly = state.snLocked,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    onSnSubmitted()
                }),
                trailingIcon = {
                    if (state.sn.isNotEmpty() && !state.snLocked) {
                        TextButton(onClick = { onSnChanged("") }) { Text(stringResource(R.string.clear)) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onTakePhoto()
                    },
                    enabled = state.canTakePhoto,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(R.string.take_photo)) }
                if (state.canRetry) {
                    OutlinedButton(
                        onClick = onRetry,
                        enabled = !state.busy && state.hasFolder,
                        modifier = Modifier.height(56.dp),
                    ) { Text(stringResource(R.string.retry)) }
                }
            }

            Text(
                stringResource(R.string.photos_count, state.photos.size),
                fontWeight = FontWeight.Bold,
            )
            PhotoGrid(state.photos, Modifier.weight(1f))

            OutlinedButton(
                onClick = {
                    focusManager.clearFocus()
                    if (state.canRetry) confirmDiscard = true else onDone()
                },
                enabled = state.canFinish,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.done_next)) }
        }
    }
}

@Composable
private fun FolderCard(state: UiState, onChooseFolder: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.folder_label), style = MaterialTheme.typography.labelMedium)
                    Text(
                        state.folderName ?: stringResource(R.string.folder_not_set),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.hasFolder) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onChooseFolder) {
                    Text(stringResource(if (state.hasFolder) R.string.change_folder else R.string.choose_folder))
                }
            }
            if (!state.hasFolder) {
                Text(stringResource(R.string.choose_folder_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<Uri>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(photos, key = { it.toString() }) { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(1f),
            )
        }
    }
}
