package com.divyanshgolyan.claune.android.llm.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import pi.agent.core.AgentTool
import pi.agent.core.AgentToolResult

internal interface ToolDefinition<TArguments> {
    val name: String
    val label: String
    val description: String
    val parameters: JsonObject
    val promptSnippet: String
    val promptGuidelines: List<String>

    fun validateArguments(arguments: JsonObject): TArguments

    suspend fun execute(
        toolCallId: String,
        params: TArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement>
}

internal fun <TArguments> toAgentTool(definition: ToolDefinition<TArguments>): AgentTool<TArguments> = object : AgentTool<TArguments> {
    override val name: String = definition.name
    override val label: String = definition.label
    override val description: String = definition.description
    override val parameters: JsonObject = definition.parameters

    override fun validateArguments(arguments: JsonObject): TArguments = definition.validateArguments(arguments)

    override suspend fun execute(
        toolCallId: String,
        params: TArguments,
        signal: pi.ai.core.AbortSignal?,
        onUpdate: pi.agent.core.AgentToolUpdateCallback<JsonElement>?,
    ): AgentToolResult<JsonElement> = definition.execute(toolCallId, params, signal, onUpdate)
}
