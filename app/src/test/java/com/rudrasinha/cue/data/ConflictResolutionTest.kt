package com.rudrasinha.cue.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictResolutionTest {
    @Test fun uploadsOnlyAgainstTheVersionLastSynced() {
        assertEquals(SyncDecision.UPLOAD, syncDecision(200, 200))
        assertEquals(SyncDecision.KEEP_REMOTE, syncDecision(200, 201))
        assertEquals(SyncDecision.KEEP_REMOTE, syncDecision(0, 200))
    }
}
