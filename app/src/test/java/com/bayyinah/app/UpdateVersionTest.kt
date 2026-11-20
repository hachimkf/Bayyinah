package com.bayyinah.app

import com.bayyinah.app.update.UpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test fun semverComparison() {
        assertTrue(UpdateManager.isNewer("1.1.1", "1.1.0"))
        assertTrue(UpdateManager.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateManager.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateManager.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateManager.isNewer("1.0.9", "1.1.0"))
        assertFalse(UpdateManager.isNewer("1.1", "1.1.0"))
    }
}
