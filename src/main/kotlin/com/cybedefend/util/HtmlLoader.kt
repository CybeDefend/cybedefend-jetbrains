package com.cybedefend.util

import java.nio.charset.StandardCharsets
import java.io.InputStreamReader

object HtmlLoader {
    fun loadResource(path: String): String {
        val stream = javaClass.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Resource not found: $path")
        return InputStreamReader(stream, StandardCharsets.UTF_8).use { it.readText() }
    }
}
