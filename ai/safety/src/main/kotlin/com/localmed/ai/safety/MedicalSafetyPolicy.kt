package com.localmed.ai.safety

import com.localmed.conversation.api.ResponseState
import java.util.Locale

/** Deterministic pre-inference rules. This is a risk-reduction layer, not a clinical safety certification. */
data class SafetyAssessment(
    val state: ResponseState,
    val reasonCodes: Set<String>,
    val userMessage: String? = null,
    val blockModel: Boolean
)

class MedicalSafetyPolicy {
    fun assess(input: String): SafetyAssessment {
        val text = normalize(input)
        if (text.isBlank()) return SafetyAssessment(
            ResponseState.INSUFFICIENT_INFORMATION,
            setOf("EMPTY_INPUT"),
            "Enter an educational or research question.",
            blockModel = true
        )

        if (EMERGENCY_PATTERNS.any { it.containsMatchIn(text) }) return SafetyAssessment(
            ResponseState.EMERGENCY,
            setOf("POSSIBLE_EMERGENCY"),
            "This may be an emergency. Do not rely on this app for urgent care. Contact local emergency services or go to the nearest emergency department now. If someone is unconscious, having severe trouble breathing, or showing stroke signs, seek emergency help immediately.",
            blockModel = true
        )

        if (SELF_HARM_PATTERNS.any { it.containsMatchIn(text) }) return SafetyAssessment(
            ResponseState.EMERGENCY,
            setOf("POSSIBLE_SELF_HARM"),
            "I can’t assess immediate safety here. If you or someone else may be in danger, contact local emergency services now and stay with a trusted person. Please reach out to a qualified crisis or mental-health professional.",
            blockModel = true
        )

        if (HIGH_RISK_MEDICATION_PATTERNS.any { it.containsMatchIn(text) }) return SafetyAssessment(
            ResponseState.PATIENT_SPECIFIC_HIGH_RISK,
            setOf("MEDICATION_DECISION"),
            "I can’t recommend a patient-specific dose, medication change, or interaction decision. Please consult a licensed clinician or pharmacist who can review the full medical history and current medicines.",
            blockModel = true
        )

        if (PATIENT_SPECIFIC_PATTERNS.any { it.containsMatchIn(text) }) return SafetyAssessment(
            ResponseState.PATIENT_SPECIFIC_HIGH_RISK,
            setOf("PATIENT_SPECIFIC_REQUEST"),
            "I can provide general education, but I can’t diagnose or choose treatment for an individual. Please consult a qualified clinician; seek urgent care if symptoms are severe or worsening.",
            blockModel = true
        )

        return SafetyAssessment(
            state = ResponseState.EDUCATIONAL,
            reasonCodes = emptySet(),
            blockModel = false
        )
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace(WHITESPACE, " ").trim()

    companion object {
        private val WHITESPACE = Regex("\\s+")
        private val EMERGENCY_PATTERNS = listOf(
            Regex("\\b(not breathing|cannot breathe|can't breathe|severe trouble breathing|is unconscious|became unconscious|not waking|having a seizure|seizure right now|possible stroke|having a stroke|face drooping|one-sided weakness|sudden weakness|severe chest pain|chest pain right now|overdose|poisoning|heavy bleeding)\\b"),
            Regex("\\b(i have|having|experiencing)\\b.{0,32}\\b(chest pain|chest tightness|difficulty breathing)\\b"),
            Regex("\\b(chest pain|chest tightness)\\b.{0,32}\\b(now|right now|with shortness of breath|with sweating|with fainting)\\b"),
            Regex("(saans nahi aa|behosh|dil ka dora|zehar|bohat zyada khoon|stroke ki alamat)")
        )
        private val SELF_HARM_PATTERNS = listOf(
            Regex("\\b(suicid(al|e)|kill myself|want to die|hurt myself|self[- ]harm|end my life|overdose on purpose)\\b"),
            Regex("(khudkushi|apne aap ko nuqsan|jaan lena)")
        )
        private val HIGH_RISK_MEDICATION_PATTERNS = listOf(
            Regex("\\b(dose|dosage|mg per kg|change my medicine|change my medication|stop taking|increase my|decrease my|drug interaction|medicine interaction|medication interaction|is it safe to take)\\b"),
            Regex("\\b\\d{1,5}(?:\\.\\d+)?\\s*(mg|mcg|ml|milligram|microgram|units?)\\b"),
            Regex("\\b(how many|how much)\\b.{0,80}\\b(mg|mcg|ml|milligram|microgram|medicine|medication|tablet|capsule)\\b"),
            Regex("\\b(pregnan(t|cy)\\b.{0,120}\\bmedicine|medicine\\b.{0,120}\\bpregnan(t|cy))")
        )
        private val PATIENT_SPECIFIC_PATTERNS = listOf(
            Regex("\\b(my symptoms|my child|my baby|my mother|my father|my report|my test result|should i take|do i have|diagnose me|for my patient|my blood pressure|my sugar is|my 3[- ]year[- ]old)\\b")
        )
    }
}

/** Checks provenance and a few forbidden high-risk output patterns; it does not fact-check medical claims. */
class MedicalOutputValidator {
    fun validate(
        answer: String,
        allowedCitationIds: Set<String>,
        citationIdsInAnswer: Set<String>,
        evidenceSensitive: Boolean
    ): OutputValidation {
        if (answer.isBlank()) return OutputValidation(false, "EMPTY_OUTPUT")
        if (PROHIBITED_DOSE_INSTRUCTION.containsMatchIn(answer) || NUMBERED_DOSE.containsMatchIn(answer) || MEDICATION_SCHEDULE.containsMatchIn(answer)) {
            return OutputValidation(false, "POSSIBLE_DOSING_INSTRUCTION")
        }
        if (citationIdsInAnswer.any { it !in allowedCitationIds }) return OutputValidation(false, "UNKNOWN_CITATION")
        if (evidenceSensitive && citationIdsInAnswer.isEmpty()) return OutputValidation(false, "MISSING_CITATION")
        return OutputValidation(true, null)
    }

    companion object {
        private val PROHIBITED_DOSE_INSTRUCTION = Regex("\\b(take|give|administer|increase|decrease)\\s+\\d+(\\.\\d+)?\\s*(mg|mcg|ml|g|units?|milligram|microgram)\\b", RegexOption.IGNORE_CASE)
        private val NUMBERED_DOSE = Regex("\\b\\d+(\\.\\d+)?\\s*(mg|mcg|ml|g|units?|milligram|microgram)\\b", RegexOption.IGNORE_CASE)
        private val MEDICATION_SCHEDULE = Regex("\\b(take|give|administer|use)\\b.{0,60}\\b(once|twice|daily|nightly|every\\s+\\d+\\s+hours|times per day)\\b", RegexOption.IGNORE_CASE)
    }
}

data class OutputValidation(val accepted: Boolean, val rejectionCode: String?)
