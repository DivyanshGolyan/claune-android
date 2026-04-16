package com.divyanshgolyan.claune.android.scripting

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object ScriptJson {
    val codec: Json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

    fun encodeElement(element: JsonElement): String = codec.encodeToString(element)
}
