package com.nxsys.inspectiondemo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiStateTest {

    private val ready = UiState(folderName = "Documents/Inspection", folderChecked = true, sn = "SN1")

    @Test
    fun canTakePhoto_whenFolderAndSnPresent() {
        assertTrue(ready.canTakePhoto)
    }

    @Test
    fun canTakePhoto_falseWhileUnsavedPhotoWaitsForRetry() {
        // Taking a new photo would discard the only copy of the unsaved one.
        assertFalse(ready.copy(canRetry = true).canTakePhoto)
    }

    @Test
    fun snLocked_whileSavingOrUnsavedPhotoPending() {
        assertFalse(ready.snLocked)
        assertTrue(ready.copy(busy = true).snLocked)
        assertTrue(ready.copy(canRetry = true).snLocked)
    }

    @Test
    fun canFinish_falseWhileSaving() {
        assertTrue(ready.canFinish)
        assertFalse(ready.copy(busy = true).canFinish)
    }
}
