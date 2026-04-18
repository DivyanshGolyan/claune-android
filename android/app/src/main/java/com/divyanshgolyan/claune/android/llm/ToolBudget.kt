package com.divyanshgolyan.claune.android.llm

import pi.agent.core.BeforeToolCallContext
import pi.agent.core.BeforeToolCallResult

internal class ToolBudget(private val maxToolCalls: Int, private val maxReflectionToolCalls: Int = 4) {
    private var inReflectionPhase: Boolean = false
    private var usedToolCalls: Int = 0
    private var usedReflectionToolCalls: Int = 0

    fun enterReflectionPhase() {
        inReflectionPhase = true
        usedReflectionToolCalls = 0
    }

    fun beforeToolCall(context: BeforeToolCallContext): BeforeToolCallResult? {
        if (inReflectionPhase) {
            if (context.toolCall.name == "execute_script") {
                return BeforeToolCallResult(
                    block = true,
                    reason = "Phone interaction is not allowed during memory reflection.",
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

        if (usedToolCalls >= maxToolCalls) {
            return BeforeToolCallResult(
                block = true,
                reason = "Tool budget exceeded after $maxToolCalls tool calls while handling the goal.",
            )
        }
        usedToolCalls += 1
        return null
    }
}
