package com.kw.pushgatewayexporter.reliability

import com.kw.pushgatewayexporter.reliability.BatteryOptimizationHelper.OptimizationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryOptimizationHelperTest {

    @Test fun `pre-M always returns NotApplicable regardless of ignoring flag`() {
        assertEquals(OptimizationStatus.NotApplicable, BatteryOptimizationHelper.classify(21, null))
        assertEquals(OptimizationStatus.NotApplicable, BatteryOptimizationHelper.classify(21, true))
        assertEquals(OptimizationStatus.NotApplicable, BatteryOptimizationHelper.classify(22, false))
    }

    @Test fun `M+ maps ignoring true to Exempt`() {
        assertEquals(OptimizationStatus.Exempt, BatteryOptimizationHelper.classify(23, true))
        assertEquals(OptimizationStatus.Exempt, BatteryOptimizationHelper.classify(34, true))
    }

    @Test fun `M+ maps ignoring false to NotExempt`() {
        assertEquals(OptimizationStatus.NotExempt, BatteryOptimizationHelper.classify(23, false))
    }

    @Test fun `M+ maps null to Unknown`() {
        assertEquals(OptimizationStatus.Unknown, BatteryOptimizationHelper.classify(29, null))
    }

    @Test fun `needsUserAction only true for NotExempt`() {
        assertTrue(BatteryOptimizationHelper.needsUserAction(OptimizationStatus.NotExempt))
        assertFalse(BatteryOptimizationHelper.needsUserAction(OptimizationStatus.Exempt))
        assertFalse(BatteryOptimizationHelper.needsUserAction(OptimizationStatus.NotApplicable))
        assertFalse(BatteryOptimizationHelper.needsUserAction(OptimizationStatus.Unknown))
    }

    @Test fun `isApplicable is true for everything except NotApplicable`() {
        assertFalse(BatteryOptimizationHelper.isApplicable(OptimizationStatus.NotApplicable))
        assertTrue(BatteryOptimizationHelper.isApplicable(OptimizationStatus.Exempt))
        assertTrue(BatteryOptimizationHelper.isApplicable(OptimizationStatus.NotExempt))
        assertTrue(BatteryOptimizationHelper.isApplicable(OptimizationStatus.Unknown))
    }
}
