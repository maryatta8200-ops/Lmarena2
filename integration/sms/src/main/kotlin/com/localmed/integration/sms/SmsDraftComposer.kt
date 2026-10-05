package com.localmed.integration.sms

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the user's SMS app with a draft. It does not send messages or request SMS permissions. */
class SmsDraftComposer(private val context: Context) {
    fun createDraftIntent(phoneNumber: String, body: String): Intent {
        val number = phoneNumber.trim()
        val digitCount = number.count(Char::isDigit)
        require(number.length <= 32 && NUMBER_PATTERN.matches(number) && digitCount in 5..15) {
            "Use a recipient number containing 5–15 digits and only standard phone punctuation."
        }
        require(body.isNotBlank() && body.length <= MAX_DRAFT_CHARS) { "Draft is empty or too long." }
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openDraft(phoneNumber: String, body: String) {
        val intent = createDraftIntent(phoneNumber, body)
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            throw IllegalStateException("No SMS application is available on this device.")
        }
    }

    companion object {
        private const val MAX_DRAFT_CHARS = 4_000
        private val NUMBER_PATTERN = Regex("\\+?[0-9(). -]+")
    }
}
