package com.nxsys.inspectiondemo.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.device.ScanManager
import android.device.scanner.configuration.PropertyID
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Receives barcodes from the Urovo scanner in broadcast ("intent") output mode.
 * Restores the previous output mode on [stop] so other apps keep working as before.
 */
class ScannerController(context: Context) {

    private val appContext = context.applicationContext
    private var scanManager: ScanManager? = null
    private var receiver: BroadcastReceiver? = null
    private var previousOutputMode: Int? = null

    /** Returns false when the Urovo scanner is not available (emulator, non-Urovo device). */
    fun start(onScan: (String) -> Unit): Boolean {
        if (receiver != null) return true
        return try {
            val sm = scanManager ?: ScanManager().also { scanManager = it }
            if (!sm.scannerState) sm.openScanner()

            val currentMode = sm.outputMode
            previousOutputMode = currentMode
            if (currentMode != OUTPUT_MODE_INTENT) sm.switchOutputMode(OUTPUT_MODE_INTENT)

            // The device may be configured with a custom action / extra name.
            val params = sm.getParameterString(
                intArrayOf(PropertyID.WEDGE_INTENT_ACTION_NAME, PropertyID.WEDGE_INTENT_DATA_STRING_TAG)
            )
            val action = params?.getOrNull(0)?.takeIf { it.isNotBlank() } ?: ScanManager.ACTION_DECODE
            val stringTag = params?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: ScanManager.BARCODE_STRING_TAG

            val r = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val value = intent.getStringExtra(stringTag)
                        ?: intent.getByteArrayExtra(ScanManager.DECODE_DATA_TAG)?.let {
                            val length = intent.getIntExtra(ScanManager.BARCODE_LENGTH_TAG, it.size)
                            String(it, 0, length.coerceIn(0, it.size), Charsets.UTF_8)
                        }
                    if (!value.isNullOrBlank()) onScan(value)
                }
            }
            // The broadcast comes from the system scanner service, i.e. another process.
            ContextCompat.registerReceiver(appContext, r, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
            receiver = r
            true
        } catch (t: Throwable) {
            // NoClassDefFoundError when android.device.ScanManager is missing.
            Log.w(TAG, "Scanner unavailable", t)
            false
        }
    }

    /** Software trigger for the on-screen scan button. Result arrives through the same broadcast. */
    fun triggerScan(): Boolean {
        if (receiver == null) return false
        return try {
            scanManager?.startDecode() ?: false
        } catch (t: Throwable) {
            Log.w(TAG, "startDecode failed", t)
            false
        }
    }

    fun stop() {
        val r = receiver ?: return
        receiver = null
        runCatching { appContext.unregisterReceiver(r) }
        try {
            val sm = scanManager
            if (sm != null) {
                sm.stopDecode()
                val previous = previousOutputMode
                if (previous != null && previous != sm.outputMode) sm.switchOutputMode(previous)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to restore scanner mode", t)
        }
        previousOutputMode = null
    }

    private companion object {
        const val TAG = "ScannerController"
        const val OUTPUT_MODE_INTENT = 0
    }
}
