package com.rudrasinha.cue

/** Unattended screen capture only considers one self-contained actionable line. */
internal object ScreenReminderCandidate {
    fun select(ocrText: String): String? {
        val candidates = ocrText.lineSequence().take(120).map { line ->
            line.trim().replace(Regex("\\s+"), " ")
        }.filter { line ->
            line.length in 10..200 && actionableReminder(line) &&
                (explicitDateTimeSpan(line) || suggestedDue(line) != null)
        }.distinct().take(2).toList()
        return candidates.singleOrNull()
    }
}
