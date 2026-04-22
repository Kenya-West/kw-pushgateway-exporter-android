package com.kw.pushgatewayexporter.reliability

import com.kw.pushgatewayexporter.reliability.oem.CandidateIntent
import com.kw.pushgatewayexporter.reliability.oem.RouteConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for the fallback selection model used by [SettingsNavigator].
 *
 * We cannot run the Android-dependent `launch` path in a JVM unit test, but we can verify
 * that the candidate-intent data model sorts & iterates the way the production code expects:
 *  - candidates with HIGHER confidence appear before BEST_EFFORT / FALLBACK when users build
 *    their lists manually,
 *  - malformed candidates (no component, no action, no uri) are detectable.
 */
class SettingsNavigatorFallbackTest {

    @Test fun `candidate without component action or uri is considered malformed`() {
        val c = CandidateIntent(label = "empty")
        assertTrue(c.componentPackage == null && c.action == null && c.uri == null)
    }

    @Test fun `component-based candidate is well-formed`() {
        val c = CandidateIntent(
            label = "x",
            componentPackage = "com.example",
            componentClass = "com.example.A"
        )
        assertEquals("com.example", c.componentPackage)
        assertEquals("com.example.A", c.componentClass)
    }

    @Test fun `confidence sort highest first when explicitly applied`() {
        val list = listOf(
            CandidateIntent("low", action = "x", confidence = RouteConfidence.FALLBACK),
            CandidateIntent("high", action = "x", confidence = RouteConfidence.LIKELY),
            CandidateIntent("med", action = "x", confidence = RouteConfidence.BEST_EFFORT)
        )
        val sorted = list.sortedBy { it.confidence.ordinal }
        assertEquals("high", sorted[0].label)
        assertEquals("med", sorted[1].label)
        assertEquals("low", sorted[2].label)
    }
}
