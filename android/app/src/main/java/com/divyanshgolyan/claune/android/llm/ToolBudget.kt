package com.divyanshgolyan.claune.android.llm

import pi.agent.core.BeforeToolCallContext
import pi.agent.core.BeforeToolCallResult

internal class ToolBudget(private val maxToolCalls: Int? = null, private val maxReflectionToolCalls: Int = 4) {
    private var inReflectionPhase: Boolean = false
    private var usedToolCalls: Int = 0
    private var usedReflectionToolCalls: Int = 0

    fun enterReflectionPhase() {
        inReflectionPhase = true
        usedReflectionToolCalls = 0
    }

    fun beforeToolCall(context: BeforeToolCallContext): BeforeToolCallResult? {
        if (inReflectionPhase) {
            if (context.toolCall.name !in MEMORY_REFLECTION_TOOLS) {
                return BeforeToolCallResult(
                    block = true,
                    reason = "Only workspace file tools are allowed during memory reflection.",
                )
            }
            if (usedReflectionToolCalls >= maxReflectionToolCalls) {
                return BeforeToolCallResult(
                    block = true,
                    reason = "Memory reflection tool budget exceeded after $maxReflectionToolCalls tool calls.",
                )
            }
            usedReflectionToolCalls += 1
            return null
        }

        val limit = maxToolCalls
        if (limit != null && usedToolCalls >= limit) {
            return BeforeToolCallResult(
                block = true,
                reason = "Tool budget exceeded after $limit tool calls while handling the current request.",
            )
        }
        usedToolCalls += 1
        return null
    }

    private companion object {
        val MEMORY_REFLECTION_TOOLS = setOf("read", "write", "edit")
    }
}
