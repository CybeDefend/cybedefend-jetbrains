// ScanStateListener.kt
package com.cybedefend.services

interface ScanStateListener {
    fun onScanStateChanged(results: ScanResults)
}
