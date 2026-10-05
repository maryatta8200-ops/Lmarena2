package com.localmed.integration.whatsapp

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Official click-to-chat handoff only. This module never reads or syncs WhatsApp conversations. */
class WhatsAppHandoff(private val context: Context) {
    fun openChat(phoneE164: String, text: String? = null) {
        val number = phoneE164.trim()
        val digits = number.filter { it in '0'..'9' }
        require(number.length <= 32 && NUMBER_PATTERN.matches(number) && digits.length in 7..15) {
            "Use an international number with country code and standard phone punctuation."
        }
        require(text == null || text.length <= MAX_TEXT_CHARS) { "Handoff text is too long." }
        val builder = Uri.Builder().scheme("https").authority("wa.me").appendPath(digits)
        text?.takeIf(String::isNotBlank)?.let { builder.appendQueryParameter("text", it) }
        val intent = Intent(Intent.ACTION_VIEW, builder.build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            throw IllegalStateException("No browser or WhatsApp-compatible handler is available.")
        }
    }

    companion object {
        private const val MAX_TEXT_CHARS = 1_000
        private val NUMBER_PATTERN = Regex("\\+?[0-9(). -]+")
    }
}
