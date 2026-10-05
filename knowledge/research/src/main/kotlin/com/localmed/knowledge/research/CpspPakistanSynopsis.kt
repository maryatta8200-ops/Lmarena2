package com.localmed.knowledge.research

/** Education/research template. Kept separate from clinical-trial evidence records. */
data class CpspPakistanSynopsis(
    val id: String,
    val title: String,
    val researchQuestion: String,
    val background: String,
    val rationale: String,
    val objectives: List<String>,
    val hypothesis: String,
    val studyDesign: String,
    val setting: String,
    val duration: String,
    val population: String,
    val inclusionCriteria: List<String>,
    val exclusionCriteria: List<String>,
    val sampleSizeAssumptions: String,
    val samplingTechnique: String,
    val variables: List<String>,
    val dataCollectionPlan: String,
    val statisticalAnalysisPlan: String,
    val ethicalConsiderations: String,
    val references: List<String>,
    val supervisor: String,
    val institution: String,
    val revision: Int,
    val source: String,
    val license: String,
    val reviewerNotes: String = ""
) {
    fun validate(): List<String> = buildList {
        if (id.isBlank()) add("Synopsis ID is required.")
        if (title.isBlank()) add("Synopsis title is required.")
        if (researchQuestion.isBlank()) add("Research question is required.")
        if (objectives.isEmpty()) add("At least one objective is required.")
        if (studyDesign.isBlank()) add("Study design is required.")
        if (population.isBlank()) add("Population is required.")
        if (ethicalConsiderations.isBlank()) add("Ethical considerations are required.")
        if (references.isEmpty()) add("References must be included.")
        if (revision < 1) add("Revision must be positive.")
        if (source.isBlank()) add("Source is required.")
        if (license.isBlank()) add("License is required.")
    }
}

data class ClinicalTrialRecord(
    val registryId: String,
    val title: String,
    val sponsor: String,
    val status: String,
    val phase: String?,
    val condition: String,
    val intervention: String,
    val sourceUrl: String,
    val lastVerifiedDate: String,
    val license: String
)
