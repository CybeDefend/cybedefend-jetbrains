package com.cybedefend.services

/**
 * Supported API regions for CybeDefend.
 * Keep this tiny and explicit; URLs are the single source of truth here.
 * 
 * Debug Mode: Set environment variable CYBEDEFEND_DEBUG=true to use localhost:3000
 */
enum class ApiRegion(val baseUrl: String) {
    US("https://api-us.cybedefend.com"),
    EU("https://api-eu.cybedefend.com");

    companion object {
        /** Default localhost URL for debug mode */
        private const val DEBUG_BASE_URL = "http://localhost:3000"
        
        /** Environment variable name to enable debug mode */
        private const val DEBUG_ENV_VAR = "CYBEDEFEND_DEBUG"
        
        /**
         * Checks if debug mode is enabled via environment variable.
         * Set CYBEDEFEND_DEBUG=true to enable localhost API calls.
         */
        fun isDebugMode(): Boolean {
            val debugValue = System.getenv(DEBUG_ENV_VAR) 
                ?: System.getProperty("cybedefend.debug")
            return debugValue?.equals("true", ignoreCase = true) == true
        }
        
        /**
         * Returns the effective base URL considering debug mode.
         * If debug mode is enabled, returns localhost URL regardless of region.
         * 
         * @param region The configured API region
         * @return The actual base URL to use for API calls
         */
        fun getEffectiveBaseUrl(region: ApiRegion): String {
            return if (isDebugMode()) {
                println("[CybeDefend] Debug mode enabled - using $DEBUG_BASE_URL")
                DEBUG_BASE_URL
            } else {
                region.baseUrl
            }
        }

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
