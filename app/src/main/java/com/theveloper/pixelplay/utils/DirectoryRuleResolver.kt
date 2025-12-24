package com.theveloper.pixelplay.utils

/**
 * Resolves directory allow/deny rules using the nearest ancestor match strategy.
 * A more specific rule (longer path) always overrides a parent rule. Allowed rules
 * take precedence over blocked rules at the same depth to enable explicit overrides
 * inside excluded trees.
 */
class DirectoryRuleResolver(
    private val allowed: Set<String>,
    private val blocked: Set<String>
) {

    fun isBlocked(path: String): Boolean {
        var searchIndex = path.length

        while (searchIndex > 0) {
            val candidate = path.substring(0, searchIndex)

            if (allowed.contains(candidate)) return false
            if (blocked.contains(candidate)) return true

            searchIndex = path.lastIndexOf('/', searchIndex - 1)
        }

        return false
    }
}
