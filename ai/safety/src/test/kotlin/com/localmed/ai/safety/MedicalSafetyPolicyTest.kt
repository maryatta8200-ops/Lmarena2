package com.localmed.ai.safety

import com.localmed.conversation.api.ResponseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalSafetyPolicyTest {
    private val policy = MedicalSafetyPolicy()

    @Test
    fun emergencySymptomsBlockInference() {
        val result = policy.assess("I can't breathe")
        assertEquals(ResponseState.EMERGENCY, result.state)
        assertTrue(result.blockModel)
    }

    @Test
    fun generalStrokeEducationIsNotMistakenForAnActiveEmergency() {
        val result = policy.assess("What is a stroke and how is it studied?")
        assertEquals(ResponseState.EDUCATIONAL, result.state)
        assertFalse(result.blockModel)
    }

    @Test
    fun medicationAmountsAndIndividualAdviceAreRedirected() {
        val dose = policy.assess("Should I take 500 mg?")
        assertEquals(ResponseState.PATIENT_SPECIFIC_HIGH_RISK, dose.state)
        assertTrue(dose.blockModel)

        val patient = policy.assess("What do my symptoms mean?")
        assertEquals(ResponseState.PATIENT_SPECIFIC_HIGH_RISK, patient.state)
        assertTrue(patient.blockModel)
    }

    @Test
    fun emptyInputNeverReachesInference() {
        assertTrue(policy.assess("  \n ").blockModel)
    }

    @Test
    fun outputRequiresAllowedCitationsAndBlocksDosing() {
        val validator = MedicalOutputValidator()
        assertEquals("MISSING_CITATION", validator.validate("General statement.", setOf("K1"), emptySet(), true).rejectionCode)
        assertEquals("UNKNOWN_CITATION", validator.validate("[source:K2]", setOf("K1"), setOf("K2"), true).rejectionCode)
        assertEquals("POSSIBLE_DOSING_INSTRUCTION", validator.validate("Take 5 mg now.", setOf("K1"), setOf("K1"), true).rejectionCode)
    }
}
