package com.example.groqchat

/**
 * IMPORTANT — READ BEFORE RELYING ON THIS:
 *
 * This is a lightweight, keyword-based PRE-FILTER only. It is NOT a substitute
 * for real content moderation and will NOT reliably catch determined attempts
 * to get around it (rewordings, other languages, roleplay framing, etc).
 *
 * The actual safety floor in this app comes from the underlying model itself
 * (Groq-hosted open models retain their own base safety training and will
 * refuse things like weapons instructions, CSAM, malware, etc. regardless of
 * your system prompt). This filter just adds a fast, cheap first pass so you
 * don't even spend an API call on obviously-out-of-scope requests, and gives
 * you a place to plug in something stronger later (e.g. a hosted moderation
 * endpoint, or a classifier model) if you need better coverage.
 *
 * Do not treat "passed this filter" as "safe to output blindly" — always keep
 * the model's own judgement as the real backstop.
 */
object ContentFilter {

    // Broad, illustrative category flags rather than an exhaustive keyword list.
    // Extend this by category as needed for your use case.
    private val blockedPatterns: List<Regex> = listOf(
        Regex("(?i)\\bhow to (make|build|synthesi[sz]e)\\b.*\\b(bomb|explosive|nerve agent|bioweapon)\\b"),
        Regex("(?i)\\b(child sexual abuse|csam)\\b"),
        Regex("(?i)\\bwrite (a |me )?(ransomware|malware|virus|exploit)\\b"),
        Regex("(?i)\\bhow (do i|to) (hack into|break into)\\b.*\\b(account|system|network)\\b")
    )

    data class Result(val allowed: Boolean, val reason: String? = null)

    fun check(userInput: String): Result {
        for (pattern in blockedPatterns) {
            if (pattern.containsMatchIn(userInput)) {
                return Result(
                    allowed = false,
                    reason = "This request falls into a category this app doesn't handle " +
                        "(e.g. weapons, exploitation content, or malicious code)."
                )
            }
        }
        return Result(allowed = true)
    }
}
