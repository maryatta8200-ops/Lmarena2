package com.localmed.tools.api

import java.time.Instant

enum class ToolRisk { LOW, MEDIUM, HIGH, CRITICAL }
enum class ConfirmationPolicy { NEVER, ONCE_PER_SESSION, EVERY_EXECUTION }

data class ToolDescriptor(
    val id: String,
    val version: Int,
    val description: String,
    val inputSchema: String,
    val outputSchema: String,
    val requiredPermissions: Set<String>,
    val risk: ToolRisk,
    val confirmationPolicy: ConfirmationPolicy,
    val availableWhen: String,
    val auditPolicy: String
)

data class ToolRequest(
    val toolId: String,
    val inputJson: String,
    val userConfirmed: Boolean,
    val requestedAt: Instant = Instant.now()
)

data class ToolSource(
    val url: String,
    val title: String,
    val publisher: String,
    val retrievedAt: Instant,
    val contentSha256: String
)

data class ToolResult(
    val success: Boolean,
    val payloadJson: String,
    val sources: List<ToolSource> = emptyList(),
    val untrusted: Boolean = true,
    val errorCode: String? = null
)

fun interface ToolExecutor {
    suspend fun execute(request: ToolRequest): ToolResult
}

interface PermissionController {
    suspend fun isPermissionGranted(permission: String): Boolean
    suspend fun isToolAuthorized(toolId: String): Boolean
}
