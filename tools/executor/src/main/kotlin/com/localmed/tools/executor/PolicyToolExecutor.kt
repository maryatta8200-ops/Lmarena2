package com.localmed.tools.executor

import com.localmed.tools.api.ToolDescriptor
import com.localmed.tools.api.ToolExecutor
import com.localmed.tools.api.ToolRequest
import com.localmed.tools.api.ToolResult
import com.localmed.tools.registry.ToolRegistry
import com.localmed.tools.permissions.ToolAuthorization
import com.localmed.tools.permissions.ToolPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

fun interface ToolImplementation {
    suspend fun execute(input: JsonObject): ToolResult
}

class PolicyToolExecutor(
    private val registry: ToolRegistry,
    private val policy: ToolPolicy,
    private val implementations: Map<String, ToolImplementation>
) : ToolExecutor {
    override suspend fun execute(request: ToolRequest): ToolResult {
        val descriptor = registry.get(request.toolId) ?: return failure("UNKNOWN_TOOL", "Tool is not registered.")
        val implementation = implementations[request.toolId] ?: return failure("TOOL_UNAVAILABLE", "Tool implementation is not installed in this build.")
        if (request.inputJson.length > MAX_TOOL_INPUT_CHARS) return failure("INPUT_TOO_LARGE", "Tool request is too large.")
        val input = runCatching { JSON.parseToJsonElement(request.inputJson).jsonObject }.getOrNull()
            ?: return failure("INVALID_INPUT", "Tool input must be a JSON object.")
        val schemaError = runCatching { validateRestrictedSchema(input, descriptor) }
            .getOrElse { "Tool schema validation failed safely." }
        if (schemaError != null) return failure("SCHEMA_INVALID", schemaError)
        when (val authorization = policy.authorize(descriptor, request)) {
            is ToolAuthorization.Denied -> return failure(authorization.code, authorization.message)
            ToolAuthorization.Allowed -> Unit
        }
        return runCatching { implementation.execute(input) }
            .getOrElse { failure("TOOL_FAILED", "Tool failed safely (${it.javaClass.simpleName}).") }
    }

    private fun validateRestrictedSchema(input: JsonObject, descriptor: ToolDescriptor): String? {
        val schema = runCatching { JSON.parseToJsonElement(descriptor.inputSchema).jsonObject }.getOrNull()
            ?: return "Registered input schema is invalid."
        if (schema["type"]?.jsonPrimitive?.content != "object") return "Only object input schemas are supported."
        val properties = schema["properties"]?.jsonObject ?: return "Tool schema has no properties."
        val required = schema["required"]?.let { element -> runCatching { element.jsonArray.map { it.jsonPrimitive.content }.toSet() }.getOrNull() }.orEmpty()
        val allowAdditional = schema["additionalProperties"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!allowAdditional && input.keys.any { it !in properties }) return "Tool input contains fields not declared in the schema."
        if (required.any { input[it] == null }) return "Tool input is missing a required field."
        for ((name, value) in input) {
            val property = properties[name]?.jsonObject ?: return "Tool input field is not declared."
            val type = property["type"]?.jsonPrimitive?.content
            when (type) {
                "string" -> {
                    if (value !is JsonPrimitive || !value.isString) return "Field '$name' must be a string."
                    val text = value.content
                    val minLength = property["minLength"]?.jsonPrimitive?.intOrNull ?: 0
                    val maxLength = property["maxLength"]?.jsonPrimitive?.intOrNull ?: MAX_TOOL_INPUT_CHARS
                    if (text.length !in minLength..maxLength) return "Field '$name' exceeds its configured length bounds."
                }
                "integer" -> {
                    val number = value.jsonPrimitive.intOrNull ?: return "Field '$name' must be an integer."
                    val minimum = property["minimum"]?.jsonPrimitive?.intOrNull ?: Int.MIN_VALUE
                    val maximum = property["maximum"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE
                    if (number !in minimum..maximum) return "Field '$name' is outside its configured range."
                }
                else -> return "Registered schema uses an unsupported property type."
            }
        }
        return null
    }

    private fun failure(code: String, message: String) = ToolResult(
        success = false,
        payloadJson = "{\"error\":\"${message.replace("\\", "\\\\").replace("\"", "\\\"")}\"}",
        errorCode = code,
        untrusted = true
    )

    companion object {
        private const val MAX_TOOL_INPUT_CHARS = 16_384
        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }
    }
}
