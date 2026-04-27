package com.divyanshgolyan.claune.android.scripting

import com.divyanshgolyan.claune.android.runtime.ActionResult
import kotlinx.serialization.json.JsonObject

fun ActionResult.toHostCallOutcome(data: JsonObject? = null): HostCallOutcome = when (this) {
    is ActionResult.Success -> HostCallOutcome(ok = true, message = message, data = data)
    is ActionResult.Blocked -> HostCallOutcome(ok = false, message = reason, data = data)
}
