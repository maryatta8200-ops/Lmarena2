package com.relaymessages.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LocalLearningEngineTest {
    private val examples = listOf(
        TrainingExample("busy", "I am in a meeting, can I call you later?", "I will call you when I am free."),
        TrainingExample("arrival", "When will you get here?", "I should be there in twenty minutes."),
        TrainingExample("greeting", "Good morning, how are you?", "Good morning! I am doing well, thanks.")
    )

    @Test
    fun exactOrCaseChangedInputReturnsSavedReply() {
        val result = LocalLearningEngine(examples).suggest("i am in a meeting, can i call you later?")
        assertNotNull(result)
        assertEquals("I will call you when I am free.", result?.reply)
    }

    @Test
    fun relatedMessageCanUseClosestTrainedExample() {
        val result = LocalLearningEngine(examples).suggest("Can I call you later? I am in a meeting")
        assertNotNull(result)
        assertEquals("busy", result?.intent)
    }

    @Test
    fun unrelatedMessageAbstainsInsteadOfInventingReply() {
        val result = LocalLearningEngine(examples).suggest("The blue bicycle is beside the river")
        assertNull(result)
    }

    @Test
    fun emptyTrainingSetAbstains() {
        assertNull(LocalLearningEngine(emptyList()).suggest("Are you free?"))
    }
}
