package com.cybedefend.services

/**
 * Supported API regions for CybeDefend.
 * Keep this tiny and explicit; URLs are the single source of truth here.
 */
enum class ApiRegion(val baseUrl: String) {
    US("https://api-us.cybedefend.com"),
    EU("https://api-eu.cybedefend.com");

    companion object {
        /**
         * Safe parser for persisted values. Defaults to US if unknown.
         */
        fun fromPersisted(value: String?): ApiRegion =
            when (value?.uppercase()) {
                "EU" -> EU
                else -> US
            }
    }
}
