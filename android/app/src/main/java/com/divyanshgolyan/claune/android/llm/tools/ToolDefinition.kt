@file:Suppress("ktlint:standard:function-signature")

package com.divyanshgolyan.claune.android.llm.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

internal fun objectParameters(properties: JsonObject = buildJsonObject {}, required: List<String> = emptyList()): JsonObject =
    buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("properties", properties)
        if (required.isNotEmpty()) {
            put("required", kotlinx.serialization.json.JsonArray(required.map(::JsonPrimitive)))
        }
    }

internal fun stringProperty(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive(description))
}

internal fun integerProperty(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("integer"))
    put("description", JsonPrimitive(description))
}

internal fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: error("Missing $name")

internal fun JsonObject.requiredStringList(name: String): List<String> =
    this[name]
        ?.jsonArray
        ?.map { it.jsonPrimitive.contentOrNull?.trim().orEmpty() }
        ?.takeIf { values -> values.all { it.isNotBlank() } }
        ?: error("Missing $name")
