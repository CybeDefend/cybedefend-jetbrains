// ScanResults.kt
package com.cybedefend.services

data class ScanResults(
    val scanType: String,  // "summary", "sast", "iac" ou "sca"
    val html: String       // page HTML complète à injecter
)
