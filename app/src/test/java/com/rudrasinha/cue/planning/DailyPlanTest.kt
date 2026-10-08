package com.rudrasinha.cue.planning

import com.rudrasinha.cue.data.CommitmentEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlanTest {
    private val date = LocalDate.of(2026, 10, 1)
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun reminder(id: String, hour: Int? = null, minute: Int = 0): CommitmentEntity =
        CommitmentEntity(id, "guest", id, null,
            hour?.let { date.atTime(it, minute).atZone(zone).toInstant().toEpochMilli() },
            zone.id, "active", 1L)

    @Test fun fixedTimeWinsOverRoutineAndFlexibleItemsAreUnique() {
        val plan = buildDailyPlan(listOf(reminder("lunch meeting", 13, 10),
            reminder("write paper"), reminder("write paper two")), date, zone = zone)
        val fixed = plan.single { it.reminderId == "lunch meeting" }
        assertEquals(13 * 60 + 10, fixed.startMinute)
        assertFalse(plan.any { it.title == "Lunch" })
        assertEquals(1, plan.count { it.reminderId == "write paper" })
        assertEquals(1, plan.count { it.reminderId == "write paper two" })
        assertEquals(420, plan.first().startMinute)
        assertEquals(1320, plan.last().endMinute)
        assertTrue(plan.any { it.kind == DayBlock.Kind.OPEN })
    }

    @Test fun eventsOutsideWakingHoursKeepTheirTime() {
        val plan = buildDailyPlan(listOf(reminder("early flight", 5, 45)), date, zone = zone)
        assertEquals(345, plan.first().startMinute)
        assertEquals("early flight", plan.first().title)
    }
}
