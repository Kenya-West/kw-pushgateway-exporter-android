package com.kw.pushgatewayexporter.reliability.oem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemProfileRegistryTest {

    @Test fun `every known profile has a stable id and display name`() {
        val all = OemProfileRegistry.knownProfiles() + OemProfileRegistry.genericProfile()
        val ids = HashSet<String>()
        for (p in all) {
            assertTrue("id must be non-blank", p.id.isNotBlank())
            assertTrue("displayName must be non-blank for ${p.id}", p.displayName.isNotBlank())
            assertTrue("duplicate id ${p.id}", ids.add(p.id))
        }
    }

    @Test fun `every candidate intent has at least one launch hint`() {
        for (p in OemProfileRegistry.knownProfiles()) {
            for (r in p.routes) {
                for (c in r.candidates) {
                    val hasHint = (c.componentPackage != null && c.componentClass != null) ||
                        c.action != null || c.uri != null
                    assertTrue("candidate '${c.label}' on ${p.id}/${r.id} has no launch hint", hasHint)
                }
            }
        }
    }

    @Test fun `every route has manual fallback steps when it has no candidates`() {
        for (p in OemProfileRegistry.knownProfiles()) {
            for (r in p.routes) {
                if (r.candidates.isEmpty()) {
                    assertTrue(
                        "route ${p.id}/${r.id} has no candidates and no manual fallback",
                        r.manualSteps.isNotEmpty()
                    )
                }
            }
        }
    }

    @Test fun `byId returns the expected profile`() {
        assertNotNull(OemProfileRegistry.byId("xiaomi"))
        assertNotNull(OemProfileRegistry.byId("generic"))
    }

    @Test fun `byId returns null for unknown`() {
        assertFalse(OemProfileRegistry.byId("totally-made-up") != null)
    }
}
