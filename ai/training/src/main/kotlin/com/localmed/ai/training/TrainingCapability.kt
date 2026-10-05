package com.localmed.ai.training

import com.localmed.ai.api.TrainingEngine

/** The app reports training unavailable until a real model-specific native trainer is installed. */
sealed interface TrainingCapability {
    data class Available(val engine: TrainingEngine, val description: String) : TrainingCapability
    data class Unavailable(val reason: String) : TrainingCapability
}

object CurrentTrainingCapability {
    val value = TrainingCapability.Unavailable(
        "No native optimizer/model-specific training backend is included in this build. Dataset validation is available; no training is simulated or reported as successful."
    )
}
