// ScanStateListener.kt
package com.cybedefend.services.scan

interface ScanStateListener {
    fun onScanStateChanged(results: ScanResults)
}
