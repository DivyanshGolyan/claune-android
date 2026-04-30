package com.divyanshgolyan.claune.android.telemetry

object ClauneTelemetry {
    fun createRecorder(): ClauneTelemetryRecorder = NoopClauneTelemetryRecorder
}
