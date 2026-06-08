package com.yourapp.instagrambot

import com.yourapp.instagrambot.ui.ActivityEntry
import com.yourapp.instagrambot.ui.ActivityLog
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLogTest {

    @Test
    fun append_addsEntryToEnd() {
        val start = listOf(ActivityEntry("00:00:01", "first"))
        val result = ActivityLog.append(start, ActivityEntry("00:00:02", "second"))
        assertEquals(2, result.size)
        assertEquals("second", result.last().message)
    }

    @Test
    fun append_capsAtMaxEntries_keepingNewest() {
        var log = emptyList<ActivityEntry>()
        for (i in 1..ActivityLog.MAX_ENTRIES + 5) {
            log = ActivityLog.append(log, ActivityEntry("t", "msg$i"))
        }
        assertEquals(ActivityLog.MAX_ENTRIES, log.size)
        assertEquals("msg${ActivityLog.MAX_ENTRIES + 5}", log.last().message)
        assertEquals("msg6", log.first().message)
    }
}
