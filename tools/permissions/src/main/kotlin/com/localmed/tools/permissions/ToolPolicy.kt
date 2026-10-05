package com.localmed.tools.permissions

import com.localmed.tools.api.ConfirmationPolicy
import com.localmed.tools.api.PermissionController
import com.localmed.tools.api.ToolDescriptor
import com.localmed.tools.api.ToolRequest
import com.localmed.tools.api.ToolRisk

sealed interface ToolAuthorization {
    data object Allowed : ToolAuthorization
    data class Denied(val code: String, val message: String) : ToolAuthorization
}

class ToolPolicy(private val permissions: PermissionController) {
    suspend fun authorize(descriptor: ToolDescriptor, request: ToolRequest): ToolAuthorization {
        if (!permissions.isToolAuthorized(descriptor.id)) {
            return ToolAuthorization.Denied("USER_OPT_IN_REQUIRED", "This tool is disabled in user settings.")
        }
        val requiresConfirmation = descriptor.confirmationPolicy == ConfirmationPolicy.EVERY_EXECUTION ||
            descriptor.risk == ToolRisk.HIGH || descriptor.risk == ToolRisk.CRITICAL
        if (requiresConfirmation && !request.userConfirmed) {
            return ToolAuthorization.Denied("CONFIRMATION_REQUIRED", "Explicit confirmation is required for this execution.")
        }
        for (permission in descriptor.requiredPermissions) {
            if (!permissions.isPermissionGranted(permission)) {
                return ToolAuthorization.Denied("PERMISSION_MISSING", "Required platform capability is not available: $permission")
            }
        }
        return ToolAuthorization.Allowed
    }
}
