package com.divyanshgolyan.claune.android.runtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun ModelTurnOutput.toStatusMessageJson(): JsonObject = buildJsonObject {
    put("status", statusName())
    put("message", messageText())
}

fun ModelTurnOutput.toStatusMessageJsonString(): String = toStatusMessageJson().toString()

private fun ModelTurnOutput.statusName(): String = when (this) {
    is ModelTurnOutput.Blocked -> "blocked"
    is ModelTurnOutput.Completion -> "completed"
    is ModelTurnOutput.Message -> "message"
}

private fun ModelTurnOutput.messageText(): String = when (this) {
    is ModelTurnOutput.Blocked -> reason
    is ModelTurnOutput.Completion -> summary
    is ModelTurnOutput.Message -> messageToUser
}
