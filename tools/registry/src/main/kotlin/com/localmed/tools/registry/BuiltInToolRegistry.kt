package com.localmed.tools.registry

import com.localmed.tools.api.ConfirmationPolicy
import com.localmed.tools.api.ToolDescriptor
import com.localmed.tools.api.ToolRisk

interface ToolRegistry {
    fun get(toolId: String): ToolDescriptor?
    fun list(): List<ToolDescriptor>
}

class BuiltInToolRegistry : ToolRegistry {
    private val tools = listOf(
        ToolDescriptor(
            id = PUBMED_SEARCH_ID,
            version = 1,
            description = "Search PubMed abstracts from the optional research build. Results are untrusted and are never required for local inference.",
            inputSchema = """{"type":"object","required":["query"],"additionalProperties":false,"properties":{"query":{"type":"string","minLength":3,"maxLength":500},"maxResults":{"type":"integer","minimum":1,"maximum":5}}}""",
            outputSchema = """{"type":"object","required":["results"],"additionalProperties":false,"properties":{"results":{"type":"array"}}}""",
            requiredPermissions = setOf("android.permission.INTERNET"),
            risk = ToolRisk.LOW,
            confirmationPolicy = ConfirmationPolicy.EVERY_EXECUTION,
            availableWhen = "research flavor, web-search preference enabled, network available, and request confirmed",
            auditPolicy = "log query hash and source metadata only; do not log raw query or abstracts"
        )
    ).associateBy { it.id }

    override fun get(toolId: String): ToolDescriptor? = tools[toolId]
    override fun list(): List<ToolDescriptor> = tools.values.sortedBy { it.id }

    companion object { const val PUBMED_SEARCH_ID = "pubmed.search.v1" }
}
