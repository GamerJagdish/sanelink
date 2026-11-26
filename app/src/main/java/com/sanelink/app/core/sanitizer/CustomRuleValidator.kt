package com.sanelink.app.core.sanitizer

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Plain-language checks for the custom rule editor, so broken rules can't be saved. */
object CustomRuleValidator {

    /** Error for an invalid regex, or null if it compiles. Blank counts as valid (optional fields). */
    fun regexError(pattern: String): String? {
        if (pattern.isBlank()) return null
        return try {
            Pattern.compile(pattern)
            null
        } catch (e: PatternSyntaxException) {
            "Not a valid pattern: ${e.description}"
        }
    }

    /**
     * Error for a rewrite pair: both fields are needed together, the pattern must compile and
     * the replacement may only reference groups ($1, $2, ...) that the pattern defines.
     */
    fun rewriteError(pattern: String, replacement: String): String? {
        if (pattern.isBlank() && replacement.isBlank()) return null
        if (pattern.isBlank()) return "Add a match pattern for this replacement"
        if (replacement.isBlank()) return "Add what to replace the match with"
        regexError(pattern)?.let { return it }
        val groups = Pattern.compile(pattern).matcher("").groupCount()
        val highest = GROUP_REF.findAll(replacement).maxOfOrNull { it.groupValues[1].toInt() } ?: 0
        return if (highest > groups) {
            "Uses \$$highest but the pattern only has $groups group${if (groups == 1) "" else "s"}"
        } else {
            null
        }
    }

    /**
     * A simple rule does nothing if it keeps built-in cleaning but removes no parameters and
     * opens no other site. (Without built-in cleaning, an empty rule is a valid way to exclude
     * a site from cleaning.)
     */
    fun isNoOpSimpleRule(keepBuiltIn: Boolean, paramCount: Int, hasSwap: Boolean): Boolean =
        keepBuiltIn && paramCount == 0 && !hasSwap

    private val GROUP_REF = Regex("(?<!\\\\)\\$(\\d+)")
}
