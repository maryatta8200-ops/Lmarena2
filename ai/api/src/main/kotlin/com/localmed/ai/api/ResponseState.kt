package com.localmed.ai.api

/** Safety and confidence classification shared by inference, safety, and conversation layers. */
enum class ResponseState {
    EDUCATIONAL,
    PATIENT_SPECIFIC_LOW_RISK,
    PATIENT_SPECIFIC_HIGH_RISK,
    EMERGENCY,
    INSUFFICIENT_INFORMATION,
    UNCERTAIN,
    REFUSAL_OR_REDIRECTION
}
