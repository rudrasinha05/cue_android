package com.rudrasinha.cue.data

internal enum class SyncDecision { UPLOAD, KEEP_REMOTE }

/** Upload only when the remote still has the version this device last read. */
internal fun syncDecision(syncedMillis: Long, remoteMillis: Long): SyncDecision =
    if (syncedMillis > 0 && syncedMillis == remoteMillis) SyncDecision.UPLOAD
    else SyncDecision.KEEP_REMOTE
