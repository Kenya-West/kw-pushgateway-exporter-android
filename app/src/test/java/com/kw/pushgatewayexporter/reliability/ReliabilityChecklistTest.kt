package com.kw.pushgatewayexporter.reliability

import com.kw.pushgatewayexporter.reliability.oem.OemProfileRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReliabilityChecklistTest {

    @Test fun `API 21 marks battery optimization step as not applicable`() {
        val steps = ReliabilityChecklist.buildSteps(21, emptyList())
        val battery = steps.first { it.id == ReliabilityChecklist.ID_BATTERY_OPT }
        assertEquals(VerificationKind.NOT_APPLICABLE, battery.verification)
    }

    @Test fun `API 23 marks battery optimization as direct and required`() {
        val steps = ReliabilityChecklist.buildSteps(23, emptyList())
        val battery = steps.first { it.id == ReliabilityChecklist.ID_BATTERY_OPT }
        assertEquals(VerificationKind.DIRECT, battery.verification)
        assertEquals(StepSeverity.REQUIRED, battery.severity)
    }

    @Test fun `OEM routes are appended as user-confirmed steps`() {
        val xiaomi = OemProfileRegistry.byId("xiaomi")!!
        val steps = ReliabilityChecklist.buildSteps(29, xiaomi.routes)
        val oemSteps = steps.filter { it.id.startsWith("oem:") }
        assertEquals(xiaomi.routes.size, oemSteps.size)
        assertTrue(oemSteps.all { it.verification == VerificationKind.USER_CONFIRMED })
    }

    @Test fun `classify maps direct-true to DONE`() {
        val step = sample(VerificationKind.DIRECT, StepSeverity.REQUIRED)
        assertEquals(StepStatus.DONE, ReliabilityChecklist.classify(step, directlyDone = true, userConfirmed = false))
    }

    @Test fun `classify maps direct-false to REQUIRED_NOT_DONE when required`() {
        val step = sample(VerificationKind.DIRECT, StepSeverity.REQUIRED)
        assertEquals(StepStatus.REQUIRED_NOT_DONE, ReliabilityChecklist.classify(step, directlyDone = false, userConfirmed = false))
    }

    @Test fun `classify maps direct-null to UNKNOWN`() {
        val step = sample(VerificationKind.DIRECT, StepSeverity.REQUIRED)
        assertEquals(StepStatus.UNKNOWN, ReliabilityChecklist.classify(step, directlyDone = null, userConfirmed = false))
    }

    @Test fun `classify respects user confirmation for USER_CONFIRMED steps`() {
        val step = sample(VerificationKind.USER_CONFIRMED, StepSeverity.REQUIRED)
        assertEquals(StepStatus.DONE, ReliabilityChecklist.classify(step, directlyDone = null, userConfirmed = true))
        assertEquals(StepStatus.REQUIRED_NOT_DONE, ReliabilityChecklist.classify(step, directlyDone = null, userConfirmed = false))
    }

    @Test fun `NOT_APPLICABLE verification always yields NOT_APPLICABLE status`() {
        val step = sample(VerificationKind.NOT_APPLICABLE, StepSeverity.REQUIRED)
        assertEquals(StepStatus.NOT_APPLICABLE, ReliabilityChecklist.classify(step, directlyDone = true, userConfirmed = true))
        assertEquals(StepStatus.NOT_APPLICABLE, ReliabilityChecklist.classify(step, directlyDone = false, userConfirmed = false))
    }

    @Test fun `RECOMMENDED severity produces RECOMMENDED_NOT_DONE when not done`() {
        val step = sample(VerificationKind.DIRECT, StepSeverity.RECOMMENDED)
        assertEquals(StepStatus.RECOMMENDED_NOT_DONE, ReliabilityChecklist.classify(step, directlyDone = false, userConfirmed = false))
    }

    @Test fun `default checklist always contains self-test and foreground and boot and job steps`() {
        val steps = ReliabilityChecklist.buildSteps(23, emptyList())
        val ids = steps.map { it.id }.toSet()
        assertTrue(ReliabilityChecklist.ID_SELF_TEST in ids)
        assertTrue(ReliabilityChecklist.ID_FOREGROUND in ids)
        assertTrue(ReliabilityChecklist.ID_BOOT in ids)
        assertTrue(ReliabilityChecklist.ID_JOB in ids)
    }

    private fun sample(kind: VerificationKind, severity: StepSeverity) = ChecklistStep(
        id = "x",
        title = "x",
        rationale = "x",
        severity = severity,
        verification = kind
    )
}
