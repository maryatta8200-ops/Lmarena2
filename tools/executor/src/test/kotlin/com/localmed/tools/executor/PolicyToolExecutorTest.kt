package com.localmed.tools.executor

import com.localmed.tools.api.PermissionController
import com.localmed.tools.api.ToolRequest
import com.localmed.tools.api.ToolResult
import com.localmed.tools.permissions.ToolPolicy
import com.localmed.tools.registry.BuiltInToolRegistry
import com.localmed.tools.registry.BuiltInToolRegistry.Companion.PUBMED_SEARCH_ID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyToolExecutorTest {
    @Test
    fun aSearchRequiresUserOptInPermissionAndPerExecutionConfirmation() = runTest {
        var calls = 0
        val permissions = FakePermissionController(optedIn = true, hasInternet = true)
        val executor = PolicyToolExecutor(
            registry = BuiltInToolRegistry(),
            policy = ToolPolicy(permissions),
            implementations = mapOf(PUBMED_SEARCH_ID to ToolImplementation {
                calls++
                ToolResult(success = true, payloadJson = "{\"results\":[]}")
            })
        )
        val input = "{\"query\":\"clinical research\"}"

        val unconfirmed = executor.execute(ToolRequest(PUBMED_SEARCH_ID, input, userConfirmed = false))
        assertFalse(unconfirmed.success)
        assertEquals("CONFIRMATION_REQUIRED", unconfirmed.errorCode)
        assertEquals(0, calls)

        val confirmed = executor.execute(ToolRequest(PUBMED_SEARCH_ID, input, userConfirmed = true))
        assertTrue(confirmed.success)
        assertEquals(1, calls)
    }

    @Test
    fun missingOptInAndPlatformPermissionDenyExecution() = runTest {
        val input = "{\"query\":\"clinical research\"}"
        val noOptIn = executor(FakePermissionController(optedIn = false, hasInternet = true))
            .execute(ToolRequest(PUBMED_SEARCH_ID, input, userConfirmed = true))
        assertEquals("USER_OPT_IN_REQUIRED", noOptIn.errorCode)

        val noInternet = executor(FakePermissionController(optedIn = true, hasInternet = false))
            .execute(ToolRequest(PUBMED_SEARCH_ID, input, userConfirmed = true))
        assertEquals("PERMISSION_MISSING", noInternet.errorCode)
    }

    @Test
    fun unknownFieldsAreRejectedBeforeToolExecution() = runTest {
        var calls = 0
        val executor = PolicyToolExecutor(
            registry = BuiltInToolRegistry(),
            policy = ToolPolicy(FakePermissionController(true, true)),
            implementations = mapOf(PUBMED_SEARCH_ID to ToolImplementation {
                calls++
                ToolResult(true, "{}")
            })
        )
        val result = executor.execute(
            ToolRequest(PUBMED_SEARCH_ID, "{\"query\":\"clinical research\",\"extra\":true}", userConfirmed = true)
        )
        assertEquals("SCHEMA_INVALID", result.errorCode)
        assertEquals(0, calls)
    }

    private fun executor(permissionController: PermissionController) = PolicyToolExecutor(
        registry = BuiltInToolRegistry(),
        policy = ToolPolicy(permissionController),
        implementations = mapOf(PUBMED_SEARCH_ID to ToolImplementation { ToolResult(true, "{}") })
    )

    private class FakePermissionController(
        private val optedIn: Boolean,
        private val hasInternet: Boolean
    ) : PermissionController {
        override suspend fun isPermissionGranted(permission: String): Boolean = hasInternet
        override suspend fun isToolAuthorized(toolId: String): Boolean = optedIn
    }
}
