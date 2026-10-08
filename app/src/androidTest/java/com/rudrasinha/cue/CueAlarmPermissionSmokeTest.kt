package com.rudrasinha.cue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rudrasinha.cue.data.CommitmentEntity
import com.rudrasinha.cue.reminders.ReminderScheduler
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Non-destructive AlarmManager check under the current permission state.
 * No reminder DB rows or notifications; pending test alarms are always cancelled.
 */
@RunWith(AndroidJUnit4::class)
class CueAlarmPermissionSmokeTest {
    @Test fun scheduleAndCancelWithoutAssumingExactAlarmPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scheduler = ReminderScheduler(context)
        val item = CommitmentEntity(
            id = "qa-temp-${UUID.randomUUID()}",
            ownerId = "qa-isolated",
            title = "Temporary scheduling check",
            details = null,
            dueAtMillis = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000,
            timezone = "UTC",
            status = "active",
            updatedAtMillis = System.currentTimeMillis()
        )
        try {
            scheduler.schedule(item)
        } finally {
            scheduler.cancel(item)
        }
    }
}
