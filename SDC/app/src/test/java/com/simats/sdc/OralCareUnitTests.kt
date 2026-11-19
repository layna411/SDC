package com.simats.sdc

import com.simats.sdc.core.common.ClinicalConstants
import com.simats.sdc.core.common.ValidationUtils
import org.junit.Assert.*
import org.junit.Test

class OralCareUnitTests {

    @Test
    fun phoneValidation_acceptsValidNumbersAndRejectsInvalid() {
        assertTrue(ValidationUtils.isValidPhone("9876543210"))
        assertTrue(ValidationUtils.isValidPhone("+91 98765 43210"))
        assertFalse(ValidationUtils.isValidPhone("12345"))
        assertFalse(ValidationUtils.isValidPhone("abc"))
    }

    @Test
    fun emailValidation_checksProperFormat() {
        assertTrue(ValidationUtils.isValidEmail("clinician@oralcare.org"))
        assertFalse(ValidationUtils.isValidEmail("not-an-email"))
        assertFalse(ValidationUtils.isValidEmail("missing@domain"))
    }

    @Test
    fun clinicalOutcomeRules_followClinicalFindingsAndLesions() {
        assertEquals("ABNORMAL", ValidationUtils.evaluateLocalPreviewOutcome(true, 0, 2))
        assertEquals("ABNORMAL", ValidationUtils.evaluateLocalPreviewOutcome(false, 1, 3))
        assertEquals("PENDING_CLINICAL_REVIEW", ValidationUtils.evaluateLocalPreviewOutcome(false, 0, 0))
        assertEquals("NORMAL", ValidationUtils.evaluateLocalPreviewOutcome(false, 0, 4))
    }

    @Test
    fun clinicalTaxonomy_containsAll11AnatomicalRegionsAndFdiTeeth() {
        assertEquals(11, ClinicalConstants.ANATOMICAL_REGIONS.size)
        val permanentTotal = ClinicalConstants.PERMANENT_UPPER_RIGHT.size +
            ClinicalConstants.PERMANENT_UPPER_LEFT.size +
            ClinicalConstants.PERMANENT_LOWER_RIGHT.size +
            ClinicalConstants.PERMANENT_LOWER_LEFT.size
        assertEquals(32, permanentTotal)

        val primaryTotal = ClinicalConstants.PRIMARY_UPPER_RIGHT.size +
            ClinicalConstants.PRIMARY_UPPER_LEFT.size +
            ClinicalConstants.PRIMARY_LOWER_RIGHT.size +
            ClinicalConstants.PRIMARY_LOWER_LEFT.size
        assertEquals(20, primaryTotal)
    }
}
